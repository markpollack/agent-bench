package io.github.markpollack.bench.agents.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.markpollack.agents.client.AgentClient;
import io.github.markpollack.agents.client.AgentClientResponse;
import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Check;
import io.github.markpollack.judge.result.Judgment;

/**
 * Reference-based recall against a published answer key.
 *
 * <p>
 * Scores a report the agent produced by asking, for each entry in an answer key, whether the
 * report recovered it — and where it placed it. The judge is <b>domain-agnostic</b>: it knows
 * about answer keys, quotes and severities, and nothing about what is being reviewed. All
 * domain knowledge lives in the answer key the benchmark ships, so no benchmark's vocabulary
 * enters this engine.
 *
 * <p>
 * <b>Per-entry majority.</b> Each entry is voted independently across {@code votes} adjudication
 * rounds. That is finer-grained than a jury {@code MajorityVotingStrategy}, which aggregates
 * whole judgments: an entry that two rounds out of three find is recovered, regardless of how
 * the rounds scored the other entries.
 *
 * <p>
 * <b>A match requires a quote.</b> A round that says {@code matched} without supplying
 * supporting text from the report does not count. Recall is a claim about the report, so it
 * must be evidenced by the report.
 */
public class AnswerKeyRecallJudge implements Judge {

	private static final Logger logger = LoggerFactory.getLogger(AnswerKeyRecallJudge.class);

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final YAMLMapper YAML = new YAMLMapper();

	private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

	/** One expected finding. {@code adjudication} is free-text guidance for the adjudicator. */
	public record Entry(String id, String name, String essence, String expectedSeverity, String adjudication) {
	}

	private final Function<Path, AgentClient> agentClientFactory;

	private final Path answerKeyPath;

	private final String reportPath;

	private final int votes;

	private final int minMatches;

	public AnswerKeyRecallJudge(Function<Path, AgentClient> agentClientFactory, Path answerKeyPath, String reportPath,
			int votes, int minMatches) {
		this.agentClientFactory = agentClientFactory;
		this.answerKeyPath = answerKeyPath;
		this.reportPath = reportPath;
		this.votes = Math.max(1, votes);
		this.minMatches = minMatches;
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Path workspace = context.workspace();
		Path report = workspace.resolve(reportPath);
		if (!Files.isRegularFile(report)) {
			return Judgment.abstain("Report not found, so recall is unmeasurable: " + reportPath);
		}

		List<Entry> entries;
		String reportText;
		try {
			entries = loadAnswerKey(answerKeyPath);
			reportText = Files.readString(report);
		}
		catch (IOException ex) {
			return Judgment.error("Could not read answer key or report: " + ex.getMessage());
		}
		if (entries.isEmpty()) {
			return Judgment.error("Answer key has no entries: " + answerKeyPath);
		}

		// entry id -> number of rounds that found it WITH a quote, plus the first quote/severity
		Map<String, Integer> yes = new LinkedHashMap<>();
		Map<String, String> quote = new LinkedHashMap<>();
		Map<String, String> placed = new LinkedHashMap<>();
		int rounds = 0;
		for (int i = 0; i < votes; i++) {
			String out;
			try {
				AgentClient client = agentClientFactory.apply(workspace);
				AgentClientResponse response = client.goal(buildPrompt(entries, reportText))
					.workingDirectory(workspace)
					.run();
				out = response.getResult();
			}
			catch (Exception ex) {
				logger.warn("Adjudication round {} failed", i + 1, ex);
				continue;
			}
			Map<String, String[]> round = parseRound(out);
			if (round == null) {
				logger.warn("Adjudication round {} produced no parsable JSON", i + 1);
				continue;
			}
			rounds++;
			for (Map.Entry<String, String[]> e : round.entrySet()) {
				yes.merge(e.getKey(), 1, Integer::sum);
				quote.putIfAbsent(e.getKey(), e.getValue()[0]);
				placed.putIfAbsent(e.getKey(), e.getValue()[1]);
			}
		}
		if (rounds == 0) {
			return Judgment.error("Every adjudication round failed; recall not measured");
		}

		List<Check> checks = new ArrayList<>();
		int matched = 0;
		int placedOk = 0;
		for (Entry e : entries) {
			boolean hit = yes.getOrDefault(e.id(), 0) * 2 > rounds;
			if (hit) {
				matched++;
				checks.add(Check.pass(e.id(), e.name() + " — found: \"" + clip(quote.get(e.id())) + "\""));
				String sev = placed.get(e.id());
				if (e.expectedSeverity() != null && !e.expectedSeverity().isBlank() && sev != null) {
					boolean ok = atOrAbove(sev, e.expectedSeverity());
					if (ok) {
						placedOk++;
					}
					checks.add(new Check("placement:" + e.id(), ok,
							"placed at " + sev.toUpperCase(Locale.ROOT) + " (expected ≥ "
									+ e.expectedSeverity().toUpperCase(Locale.ROOT) + ")"));
				}
			}
			else {
				checks.add(new Check(e.id(), false, e.name() + " — NOT FOUND"));
			}
		}

		double recall = (double) matched / entries.size();
		int threshold = minMatches > 0 ? minMatches : entries.size();
		return Judgment.scored(recall)
			.passingAt((double) threshold / entries.size())
			.reasoning("recall " + matched + "/" + entries.size() + "; placement " + placedOk
					+ " at-or-above expected; " + rounds + " adjudication round(s)")
			.checks(checks)
			.metadata("answerKey", answerKeyPath.getFileName().toString())
			.metadata("recall", recall)
			.metadata("rounds", rounds)
			.build();
	}

	/** Accepts {@code entries:} (canonical) or {@code register:} (the bud-ddd gold-register shape). */
	static List<Entry> loadAnswerKey(Path file) throws IOException {
		JsonNode root = YAML.readTree(Files.readString(file));
		JsonNode list = root.has("entries") ? root.get("entries") : root.get("register");
		List<Entry> out = new ArrayList<>();
		if (list == null || !list.isArray()) {
			return out;
		}
		for (JsonNode n : list) {
			out.add(new Entry(text(n, "id"), text(n, "name"), text(n, "essence"), text(n, "expectedSeverity"),
					text(n, "adjudication")));
		}
		return out;
	}

	private String buildPrompt(List<Entry> entries, String reportText) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are adjudicating REFERENCE-BASED RECALL. Below is an answer key of findings a "
				+ "domain expert established for this codebase, and a report produced by an agent. For each "
				+ "answer-key entry decide whether the REPORT surfaced that finding.\n\n");
		sb.append("Rules:\n");
		sb.append("- A match must be SURFACED as the subject of a finding. A passing mention buried inside a "
				+ "finding about something else is NOT a match.\n");
		sb.append("- Quote the report text that establishes the match. No quote, no match.\n");
		sb.append("- Report the severity heading the report placed it under.\n");
		sb.append("- Judge only what the report says. Do not evaluate the codebase yourself.\n\n");
		sb.append("ANSWER KEY:\n");
		for (Entry e : entries) {
			sb.append("- id: ").append(e.id()).append("\n  name: ").append(e.name()).append("\n");
			if (e.essence() != null && !e.essence().isBlank()) {
				sb.append("  essence: ").append(e.essence()).append("\n");
			}
			if (e.adjudication() != null && !e.adjudication().isBlank()) {
				sb.append("  adjudication: ").append(e.adjudication()).append("\n");
			}
		}
		sb.append("\nREPORT:\n").append(reportText).append("\n\n");
		sb.append("Respond with ONLY this JSON object and nothing else:\n");
		sb.append("{\"matches\":[{\"entry\":\"<id>\",\"matched\":true,\"quote\":\"<text from the report>\","
				+ "\"severity\":\"CRITICAL|WARNING|SUGGESTION\"}]}\n");
		sb.append("Include an object for EVERY answer-key entry; use matched:false with no quote when absent.");
		return sb.toString();
	}

	/** @return entry id → [quote, severity] for entries this round matched WITH a quote, or null if unparsable. */
	static Map<String, String[]> parseRound(String agentOutput) {
		if (agentOutput == null) {
			return null;
		}
		Matcher m = JSON_BLOCK.matcher(agentOutput);
		if (!m.find()) {
			return null;
		}
		try {
			JsonNode root = JSON.readTree(m.group());
			JsonNode matches = root.get("matches");
			if (matches == null || !matches.isArray()) {
				return null;
			}
			Map<String, String[]> found = new LinkedHashMap<>();
			for (JsonNode n : matches) {
				String id = text(n, "entry");
				String q = text(n, "quote");
				boolean matched = n.has("matched") && n.get("matched").asBoolean();
				if (id != null && matched && q != null && !q.isBlank()) {
					found.put(id, new String[] { q, text(n, "severity") });
				}
			}
			return found;
		}
		catch (Exception ex) {
			return null;
		}
	}

	private static int rank(String severity) {
		if (severity == null) {
			return -1;
		}
		return switch (severity.trim().toUpperCase(Locale.ROOT)) {
			case "CRITICAL", "ISSUE", "BLOCKER" -> 3;
			case "WARNING", "MAJOR" -> 2;
			case "SUGGESTION", "MINOR", "INFO" -> 1;
			default -> -1;
		};
	}

	static boolean atOrAbove(String actual, String expected) {
		int a = rank(actual);
		int e = rank(expected);
		return a >= 0 && e >= 0 && a >= e;
	}

	private static String text(JsonNode n, String field) {
		return n.hasNonNull(field) ? n.get(field).asText() : null;
	}

	private static String clip(String s) {
		if (s == null) {
			return "";
		}
		return s.length() <= 90 ? s : s.substring(0, 89) + "…";
	}

	/** Default adjudicator factory, mirroring {@link TestQualityJudge#defaultAgentClientFactory}. */
	public static Function<Path, AgentClient> defaultAgentClientFactory(String model, Duration timeout) {
		return TestQualityJudge.defaultAgentClientFactory(model, timeout);
	}

}

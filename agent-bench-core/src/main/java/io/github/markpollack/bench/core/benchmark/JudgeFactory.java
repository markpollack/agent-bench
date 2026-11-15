package io.github.markpollack.bench.core.benchmark;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.Judges;
import io.github.markpollack.judge.coverage.CoverageComparison;
import io.github.markpollack.judge.coverage.CoverageImprovementJudge;
import io.github.markpollack.judge.coverage.CoveragePreservationJudge;
import io.github.markpollack.judge.coverage.JaCoCoReportParser;
import io.github.markpollack.judge.exec.BuildSuccessJudge;
import io.github.markpollack.judge.file.FileContentJudge;
import io.github.markpollack.judge.file.FileExistsJudge;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.jury.CascadedJury;
import io.github.markpollack.judge.jury.SimpleJury;
import io.github.markpollack.judge.verdict.RoutingRule;
import io.github.markpollack.judge.voting.MajorityVotingStrategy;

/**
 * Materializes YAML jury configuration into {@link Judge} instances. Supports built-in
 * check types (file-exists, file-content, maven-build, coverage-preservation,
 * coverage-improvement) and custom types registered via {@link #register}.
 * <p>
 * {@link #createFromConfig(Map)} reads and validates the configuration once and returns
 * a function that builds a fresh judge for each trial's {@link TrialEvidence}.
 * </p>
 * <p>
 * A cascade tier's {@code policy} is an Agent Judge {@link RoutingRule} name. The names
 * used before Agent Judge 0.18 are still accepted: {@code REJECT_ON_ANY_FAIL} reads as
 * {@link RoutingRule#STOP_ON_ANY_OPINION_FAIL} and {@code ACCEPT_ON_ALL_PASS} as
 * {@link RoutingRule#STOP_ON_ALL_OPINIONS_PASS}.
 * </p>
 */
public class JudgeFactory {

	/** Task metadata key holding the baseline line coverage, in percent. */
	public static final String BASELINE_COVERAGE_KEY = "baselineCoverage";

	private static final Map<String, RoutingRule> LEGACY_POLICIES = Map.of("REJECT_ON_ANY_FAIL",
			RoutingRule.STOP_ON_ANY_OPINION_FAIL, "ACCEPT_ON_ALL_PASS", RoutingRule.STOP_ON_ALL_OPINIONS_PASS);

	private final Map<String, CheckFactory> registry = new HashMap<>();

	public JudgeFactory() {
		// Built-in types
		registry.put("file-exists",
				(config, evidence) -> new FileExistsJudge(workspace(evidence), (String) config.get("path")));
		registry.put("file-content", (config, evidence) -> {
			String matchMode = (String) config.getOrDefault("match", "EXACT");
			return new FileContentJudge(workspace(evidence), (String) config.get("path"),
					(String) config.get("expected"), FileContentJudge.MatchMode.valueOf(matchMode));
		});
		registry.put("maven-build", (config, evidence) -> {
			@SuppressWarnings("unchecked")
			List<String> goals = (List<String>) config.getOrDefault("goals", List.of("clean", "test"));
			return BuildSuccessJudge.maven(goals.toArray(new String[0])).evidenceSupplier(workspace(evidence)).build();
		});
		registry.put("coverage-preservation", (config, evidence) -> withBaseline(evidence,
				comparison -> new CoveragePreservationJudge(() -> comparison)));
		registry.put("coverage-improvement", (config, evidence) -> {
			double maxImprovement = config.containsKey("min") ? ((Number) config.get("min")).doubleValue() : 50.0;
			return withBaseline(evidence, comparison -> new CoverageImprovementJudge(() -> comparison, maxImprovement));
		});
	}

	/**
	 * Registers a custom judge type. Use this to add domain-specific judges (e.g.,
	 * test-quality-llm) from outside agent-bench-core.
	 * @param type the check type named in YAML
	 * @param factory builds the check's judge for one trial
	 */
	public void register(String type, CheckFactory factory) {
		registry.put(type, factory);
	}

	/**
	 * Reads a benchmark's jury configuration.
	 * @param juryConfig the benchmark's {@code jury} map, with {@code tiers} or
	 * {@code checks}
	 * @return a function that builds the judge for one trial
	 * @throws IllegalArgumentException when the configuration is missing, names an
	 * unknown check type or an unknown tier policy
	 */
	@SuppressWarnings("unchecked")
	public Function<TrialEvidence, Judge> createFromConfig(Map<String, Object> juryConfig) {
		if (juryConfig == null || juryConfig.isEmpty()) {
			throw new IllegalArgumentException("Jury configuration is required");
		}

		Object tiers = juryConfig.get("tiers");
		if (tiers instanceof List) {
			return createCascade((List<Map<String, Object>>) tiers);
		}

		Object checks = juryConfig.get("checks");
		if (checks instanceof List) {
			List<ResolvedCheck> resolved = resolve((List<Map<String, Object>>) checks);
			return evidence -> Judges.allOf(judges(resolved, evidence).toArray(new Judge[0]));
		}

		throw new IllegalArgumentException("Jury config must contain 'tiers' or 'checks'");
	}

	/**
	 * Reads a tier policy.
	 * @param policy a {@link RoutingRule} name, or a name used before Agent Judge 0.18
	 * @return the routing rule
	 */
	static RoutingRule routingRule(String policy) {
		String name = policy.trim().toUpperCase(Locale.ROOT);
		RoutingRule legacy = LEGACY_POLICIES.get(name);
		return legacy != null ? legacy : RoutingRule.valueOf(name);
	}

	@SuppressWarnings("unchecked")
	private Function<TrialEvidence, Judge> createCascade(List<Map<String, Object>> tiers) {
		List<ResolvedTier> resolved = new ArrayList<>();
		for (int i = 0; i < tiers.size(); i++) {
			Map<String, Object> tierMap = tiers.get(i);
			String name = (String) tierMap.getOrDefault("name", "tier-" + (i + 1));
			String policy = (String) tierMap.getOrDefault("policy",
					i == tiers.size() - 1 ? "FINAL_TIER" : "REJECT_ON_ANY_FAIL");
			List<Map<String, Object>> checks = (List<Map<String, Object>>) tierMap.get("checks");
			resolved.add(new ResolvedTier(name, routingRule(policy), resolve(checks)));
		}

		return evidence -> {
			CascadedJury.Builder builder = CascadedJury.builder();
			for (ResolvedTier tier : resolved) {
				SimpleJury.Builder juryBuilder = SimpleJury.builder().votingStrategy(new MajorityVotingStrategy());
				judges(tier.checks(), evidence).forEach(juryBuilder::judge);
				builder.tier(tier.name(), juryBuilder.build(), tier.rule());
			}
			CascadedJury jury = builder.build();
			return () -> jury.vote().judgment();
		};
	}

	private List<ResolvedCheck> resolve(List<Map<String, Object>> checks) {
		List<ResolvedCheck> resolved = new ArrayList<>();
		if (checks == null) {
			return resolved;
		}
		for (Map<String, Object> check : checks) {
			String type = (String) check.get("type");
			CheckFactory factory = registry.get(type);
			if (factory == null) {
				throw new IllegalArgumentException("Unknown check type: " + type + ". Available: " + registry.keySet());
			}
			resolved.add(new ResolvedCheck(check, factory));
		}
		return resolved;
	}

	private static List<Judge> judges(List<ResolvedCheck> checks, TrialEvidence evidence) {
		List<Judge> judges = new ArrayList<>();
		for (ResolvedCheck check : checks) {
			judges.add(check.factory().create(check.config(), evidence));
		}
		return judges;
	}

	private static Supplier<Path> workspace(TrialEvidence evidence) {
		return evidence::workspace;
	}

	/**
	 * Builds a coverage judge from the task's {@code baselineCoverage}, or abstains when
	 * the baseline is missing or unreadable, as the coverage judges did before Agent
	 * Judge 0.18.
	 */
	private static Judge withBaseline(TrialEvidence evidence, Function<CoverageComparison, Judge> judge) {
		Object baseline = evidence.metadata().get(BASELINE_COVERAGE_KEY);
		if (baseline == null) {
			return () -> Judgment.abstain("No baselineCoverage in metadata");
		}
		double lineCoverage;
		if (baseline instanceof JaCoCoReportParser.CoverageMetrics metrics) {
			lineCoverage = metrics.lineCoverage();
		}
		else {
			try {
				lineCoverage = Double.parseDouble(baseline.toString());
			}
			catch (NumberFormatException ex) {
				return () -> Judgment.abstain("Invalid baselineCoverage value: " + baseline);
			}
		}
		if (!Double.isFinite(lineCoverage) || lineCoverage < 0 || lineCoverage > 100) {
			return () -> Judgment.abstain("Invalid baselineCoverage value: " + baseline);
		}
		return judge.apply(new CoverageComparison(evidence.workspace(), lineCoverage));
	}

	private record ResolvedCheck(Map<String, Object> config, CheckFactory factory) {
	}

	private record ResolvedTier(String name, RoutingRule rule, List<ResolvedCheck> checks) {
	}

}

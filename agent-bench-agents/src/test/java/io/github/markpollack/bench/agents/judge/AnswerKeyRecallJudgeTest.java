package io.github.markpollack.bench.agents.judge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.bench.agents.judge.AnswerKeyRecallJudge.Entry;
import io.github.markpollack.judge.context.JudgmentContext;
import io.github.markpollack.judge.result.Judgment;
import io.github.markpollack.judge.result.JudgmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerKeyRecallJudgeTest {

	@TempDir
	Path workspace;

	@Test
	void readsTheCanonicalEntriesShape() throws Exception {
		Path key = workspace.resolve("key.yaml");
		Files.writeString(key, """
				schema: bench.answer-key.v1
				target: demo
				entries:
				  - id: A1
				    name: First finding
				    essence: something structural
				    expectedSeverity: critical
				""");
		List<Entry> entries = AnswerKeyRecallJudge.loadAnswerKey(key);

		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).id()).isEqualTo("A1");
		assertThat(entries.get(0).expectedSeverity()).isEqualTo("critical");
	}

	@Test
	void alsoReadsTheRegisterShapeSoExistingGoldRegistersLoadUnchanged() throws Exception {
		Path key = workspace.resolve("gold.yaml");
		Files.writeString(key, """
				register:
				  - id: G1
				    name: Aggregate boundaries
				    expectedSeverity: critical
				    adjudication: must be surfaced as its own finding
				""");
		List<Entry> entries = AnswerKeyRecallJudge.loadAnswerKey(key);

		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).id()).isEqualTo("G1");
		assertThat(entries.get(0).adjudication()).contains("surfaced");
	}

	@Test
	void aMatchWithoutAQuoteDoesNotCount() {
		Map<String, String[]> round = AnswerKeyRecallJudge.parseRound("""
				Sure, here you go:
				{"matches":[
				  {"entry":"A1","matched":true,"quote":"the aggregate spans three roots","severity":"CRITICAL"},
				  {"entry":"A2","matched":true,"quote":"","severity":"WARNING"},
				  {"entry":"A3","matched":false}
				]}
				""");

		assertThat(round).containsOnlyKeys("A1");
		assertThat(round.get("A1")[0]).contains("three roots");
		assertThat(round.get("A1")[1]).isEqualTo("CRITICAL");
	}

	@Test
	void unparsableAdjudicationIsNullNotAnEmptyResult() {
		// Distinguishing "the round failed" from "the round found nothing" is the difference
		// between abstaining and reporting zero recall.
		assertThat(AnswerKeyRecallJudge.parseRound("There is no JSON here.")).isNull();
		assertThat(AnswerKeyRecallJudge.parseRound(null)).isNull();
		assertThat(AnswerKeyRecallJudge.parseRound("{\"nope\":1}")).isNull();
	}

	@Test
	void placementComparesSeverityRankNotStringEquality() {
		assertThat(AnswerKeyRecallJudge.atOrAbove("CRITICAL", "warning")).isTrue();
		assertThat(AnswerKeyRecallJudge.atOrAbove("Issue", "SUGGESTION")).isTrue();
		assertThat(AnswerKeyRecallJudge.atOrAbove("suggestion", "CRITICAL")).isFalse();
		assertThat(AnswerKeyRecallJudge.atOrAbove("nonsense", "CRITICAL")).isFalse();
	}

	@Test
	void abstainsRatherThanScoringZeroWhenTheReportIsMissing() throws Exception {
		Path key = workspace.resolve("k.yaml");
		Files.writeString(key, "entries:\n  - id: A1\n    name: n\n");
		JudgmentContext context = JudgmentContext.builder().workspace(workspace).build();

		Judgment j = new AnswerKeyRecallJudge(ws -> {
			throw new IllegalStateException("must not be invoked when there is no report");
		}, key, "absent-report.md", 3, 1).judge(context);

		assertThat(j.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(j.reasoning()).contains("unmeasurable");
	}

}

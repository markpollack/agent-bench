package io.github.markpollack.bench.core.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.judgment.Judgment;
import io.github.markpollack.judge.judgment.JudgmentStatus;
import io.github.markpollack.judge.verdict.RoutingRule;

class JudgeFactoryTest {

	private static final Map<String, Object> HELLO_WORLD = Map.of("checks",
			List.of(Map.of("type", "file-exists", "path", "hello.txt"),
					Map.of("type", "file-content", "path", "hello.txt", "expected", "Hello World!", "match", "EXACT")));

	private final JudgeFactory factory = new JudgeFactory();

	@Test
	void buildsAFreshJudgeForEachTrialWorkspace(@TempDir Path passing, @TempDir Path failing) throws IOException {
		Files.writeString(passing.resolve("hello.txt"), "Hello World!");
		Function<TrialEvidence, Judge> judges = factory.createFromConfig(HELLO_WORLD);

		assertThat(judges.apply(TrialEvidence.of(passing)).judge().pass()).isTrue();
		assertThat(judges.apply(TrialEvidence.of(failing)).judge().pass()).isFalse();
	}

	@Test
	void customChecksReceiveTheTrialEvidence(@TempDir Path workspace) {
		factory.register("goal-echo", (config, evidence) -> () -> evidence.goal().equals(config.get("expect"))
				? Judgment.pass("goal seen") : Judgment.fail("goal not seen"));
		Map<String, Object> jury = Map.of("checks", List.of(Map.of("type", "goal-echo", "expect", "do it")));

		Judgment judgment = factory.createFromConfig(jury)
			.apply(new TrialEvidence(workspace, "do it", Map.of()))
			.judge();

		assertThat(judgment.pass()).isTrue();
	}

	@Test
	void cascadeStopsOnAFailingGateUnderItsLegacyPolicyName(@TempDir Path workspace) {
		factory.register("always-pass", (config, evidence) -> () -> Judgment.pass("ok"));
		Map<String, Object> jury = Map.of("tiers",
				List.of(Map.of("name", "gate", "policy", "REJECT_ON_ANY_FAIL", "checks",
						List.of(Map.of("type", "file-exists", "path", "missing.txt"))),
						Map.of("name", "final", "policy", "FINAL_TIER", "checks",
								List.of(Map.of("type", "always-pass")))));

		Judgment judgment = factory.createFromConfig(jury).apply(TrialEvidence.of(workspace)).judge();

		assertThat(judgment.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void readsLegacyAndCurrentTierPolicyNames() {
		assertThat(JudgeFactory.routingRule("REJECT_ON_ANY_FAIL")).isEqualTo(RoutingRule.STOP_ON_ANY_OPINION_FAIL);
		assertThat(JudgeFactory.routingRule("ACCEPT_ON_ALL_PASS")).isEqualTo(RoutingRule.STOP_ON_ALL_OPINIONS_PASS);
		assertThat(JudgeFactory.routingRule("FINAL_TIER")).isEqualTo(RoutingRule.FINAL_TIER);
		assertThat(JudgeFactory.routingRule("STOP_ON_CONCLUSIVE")).isEqualTo(RoutingRule.STOP_ON_CONCLUSIVE);
	}

	@Test
	void coverageChecksAbstainWithoutABaseline(@TempDir Path workspace) {
		Map<String, Object> jury = Map.of("checks", List.of(Map.of("type", "coverage-preservation")));

		Judgment missing = factory.createFromConfig(jury).apply(TrialEvidence.of(workspace)).judge();
		Judgment invalid = factory.createFromConfig(jury)
			.apply(new TrialEvidence(workspace, null, Map.of(JudgeFactory.BASELINE_COVERAGE_KEY, "lots")))
			.judge();

		assertThat(missing.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(invalid.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void rejectsUnknownTypesAndPoliciesBeforeAnyTrial() {
		assertThatThrownBy(() -> factory.createFromConfig(Map.of("checks", List.of(Map.of("type", "nope")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unknown check type: nope");
		assertThatThrownBy(() -> factory.createFromConfig(Map.of("tiers",
				List.of(Map.of("policy", "SOMETIMES", "checks", List.of(Map.of("type", "file-exists", "path", "x")))))))
			.isInstanceOf(IllegalArgumentException.class);
	}

}

package io.github.markpollack.bench.core.benchmark;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * What one trial hands its checks: the workspace being graded, the task's goal and the
 * task's metadata (for example {@code baselineCoverage} for the coverage checks).
 * <p>
 * Agent Judge judges take their evidence when they are built rather than when they are
 * called, so {@link JudgeFactory} builds a fresh judge from this for every trial.
 * </p>
 *
 * @param workspace the workspace to evaluate
 * @param goal the task instruction, or {@code null} when the task has none
 * @param metadata the task metadata, never {@code null}
 */
public record TrialEvidence(Path workspace, String goal, Map<String, Object> metadata) {

	public TrialEvidence {
		Objects.requireNonNull(workspace, "workspace");
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}

	/**
	 * Evidence for a workspace with no goal or metadata.
	 * @param workspace the workspace to evaluate
	 * @return the evidence
	 */
	public static TrialEvidence of(Path workspace) {
		return new TrialEvidence(workspace, null, Map.of());
	}

}

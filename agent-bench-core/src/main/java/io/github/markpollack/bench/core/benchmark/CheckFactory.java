package io.github.markpollack.bench.core.benchmark;

import java.util.Map;

import io.github.markpollack.judge.Judge;

/**
 * Builds the judge for one check entry of a benchmark's jury configuration, for one
 * trial.
 */
@FunctionalInterface
public interface CheckFactory {

	/**
	 * Builds the judge for one trial.
	 * @param config the check's YAML entry, including its {@code type}
	 * @param evidence the trial's workspace, goal and metadata
	 * @return a judge that evaluates that trial
	 */
	Judge create(Map<String, Object> config, TrialEvidence evidence);

}

#!/usr/bin/env bash
# grade.sh — the spring-boot-upgrade benchmark's verification harness (Terminal-Bench style).
#
# The rubric IS the bud-spring-modernize judge layer, elevated into a CLI (ModernizeJudgeMain):
#   build-gate (verify on the target JDK) + baseline-DISPOSITION (a still-red test penalizes the
#   agent only if it was green on the baseline) + classification (the human handoff).
# A naive "build green" grader would unfairly fail real projects (e.g. the deployer's
# checkRepositoryPolicies, a JUnit-4 test that never ran on the baseline). This grader does not.
#
# Usage:  grade.sh <workspace>
# Env:    BENCH_MODERNIZE_JAR  = bud-spring-modernize CLI uber-jar
#         BENCH_TARGET_JDK     = JDK 17 home (the Boot 3 build floor)
set -euo pipefail
WORKSPACE="${1:?usage: grade.sh <workspace>}"
JAR="${BENCH_MODERNIZE_JAR:?set BENCH_MODERNIZE_JAR to the bud-spring-modernize -cli.jar}"
JDK17="${BENCH_TARGET_JDK:?set BENCH_TARGET_JDK to a JDK 17 home}"
JAVA="${BENCH_LAUNCH_JDK:-$JDK17}/bin/java"   # the jar itself launches on JDK 17+/21

BASELINE_ARG=()
[ -f "$WORKSPACE/baseline-passed.txt" ] && BASELINE_ARG=(--baseline-passed "$WORKSPACE/baseline-passed.txt")

exec "$JAVA" -cp "$JAR" io.github.markpollack.bud.spring.modernize.bench.ModernizeJudgeMain \
  --workspace "$WORKSPACE" \
  --target-jdk "$JDK17" \
  --maven-args "verify -DskipITs" \
  "${BASELINE_ARG[@]}" \
  --json "$WORKSPACE/modernize-verdict.json"

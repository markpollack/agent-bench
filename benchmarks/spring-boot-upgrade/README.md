# spring-boot-upgrade benchmark

Can an AI coding agent perform a **real Spring Boot 2.x → 3.x framework migration** on an
enterprise project — and do it **honestly** (no disabling/weakening/deleting tests)?

Companion to **FreshBrew** (arXiv 2510.04852): the deterministic-only ceiling sits well below the
best AI, and the anti-reward-hacking gate is what makes the score meaningful. Here the gate is our
own judge layer.

## The rubric *is* the judges (elevated to a CLI)

Grading is **not** a naive "build green" — that's unfair on real projects. It's the
`bud-spring-modernize` judge layer, elevated into a Terminal-Bench-style verification CLI
(`ModernizeJudgeMain`, invoked by `grade.sh`):

1. **build-gate** — `mvnw -DskipITs verify` on the target JDK (compile + unit tests).
2. **baseline-disposition** (the differentiator) — a still-red test penalizes the agent **only if it
   was green on the pre-upgrade baseline** (a `REGRESSION`). A test that was never green before
   (`NOT_IN_BASELINE`) is a pre-existing latent issue, not the agent's fault. *Example:* the
   deployer's `MavenResourceTests.checkRepositoryPolicies` is a JUnit-4 test that never executed on
   the baseline (no `junit-vintage`); the JUnit 4→5 migration makes it run and surface a brittle
   assertion — that must not fail the agent.
3. **classification** — each remaining failure is labelled (migration-defect vs environmental) for
   the human handoff (classify, don't suppress).

`PASS` ⟺ the upgrade compiles **and** introduces no regression.

## Run it (terminal-bench flow)

```bash
export BENCH_MODERNIZE_JAR=~/projects/bud-spring-modernize/target/bud-spring-modernize-0.1.0-SNAPSHOT-cli.jar
export BENCH_MODERNIZE_BUNDLE=~/projects/bud-spring-modernize/knowledge/modernize/junit/4-to-5
export BENCH_BASELINE_JDK=~/.sdkman/candidates/java/8.0.472-amzn      # JDK 8 (baseline)
export BENCH_TARGET_JDK=~/.sdkman/candidates/java/17.0.17-librca      # JDK 17 (Boot 3 floor)
export BENCH_LAUNCH_JDK=~/.sdkman/candidates/java/21.0.9-amzn         # the jar's own JDK

# 1) provide + setup (clone v2.9.5, capture baseline-green unit tests)
bench provide --benchmark spring-boot-upgrade --task spring-cloud-deployer --workspace /tmp/scd

# 2) run an agent in the workspace (any of):
( cd /tmp/scd && eval "$(grep '^command:' ../../agents/claude-code.yaml | cut -d' ' -f2-)" )   # vanilla
# or the hybrid engine:  agents/bud-modernize.yaml

# 3) grade with the elevated judges
benchmarks/spring-boot-upgrade/grade.sh /tmp/scd
```

## Agents to compare
- `agents/claude-code.yaml` — vanilla coding agent (reads INSTRUCTION.md).
- `agents/bud-modernize.yaml` — the hybrid deterministic+AI engine (DETECT → deterministic chain →
  bounded AI residual). The "informed, not a mop" contender.
- *(deterministic-only — run `UpgradeMain --no-ai` — the floor, à la FreshBrew.)*

## Tasks
- `spring-cloud-deployer` — the hard, real-world target (Security restructure needs AI; pre-existing
  latent test exercises baseline-disposition). Clones `v2.9.5`.
- *(planned)* `spring-petclinic` — the clean target that reaches full `verify` green deterministically.

## Open wire (follow-up)
`grade.sh` is the standalone (terminal-bench-style) grader. To run it through `bench grade` directly,
register `ModernizeJudgeMain` as an agent-bench **exec-judge** (`JudgeFactory.register("modernize-rubric", …)`
in `agent-bench-agents`) so the `benchmark.yaml` jury delegates to it — both build on the same
`agent-judge` library, so the judges drop in without an adapter.

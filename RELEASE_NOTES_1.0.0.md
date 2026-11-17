# Agent Bench 1.0.0

Agent Bench 1.0.0 moves to the Agent Judge 1.0 API (Agent Eval, formerly Agent Judge) and Agent Client 1.0.0. It also adds
the `answer-key-recall` check type and removes Spring Framework from `agent-bench-core`. These notes cover everything since
0.6.3.

This is a major release. Code that registers custom check types or calls `JudgeFactory` directly has to change; see
[Breaking changes](#breaking-changes). Benchmark YAML files load unchanged.

## Requirements and dependencies

- Java 21.
- Agent Client **1.0.0** (`agent-client-core`, `agent-claude`), used by `agent-bench-agents`.
- Agent Judge **1.0.0** (`agent-judge-core`, `agent-judge-exec`, and the new direct dependency `agent-judge-file`).
- Jackson **2.22.3** and **3.2.3**. The exported floors were 2.21.6 and 3.1.6.
- `agent-bench-core` no longer depends on Spring Framework at runtime.

## New: `answer-key-recall`

`answer-key-recall` measures recall against a published answer key. It is available in `agent-bench-agents`. For each
entry in the key, an LLM adjudicator decides whether the agent's report recovered it and at what severity the report
placed it.

```yaml
jury:
  checks:
    - type: answer-key-recall
      answerKey: answer-key.yaml   # entries: (or a gold register's register:)
      report: report.md            # relative to the workspace; default report.md
      votes: 3                     # adjudication rounds; default 3
      min: 4                       # entries needed to pass; default 0 = all
      model: claude-sonnet-4-6
```

- **Domain-agnostic.** The judge knows about answer keys, quotes and severities. Every domain term lives in the
  answer-key file the benchmark ships.
- **Per-entry majority.** Each entry is voted on separately across rounds, so an entry that most rounds find counts as
  recovered.
- **A match needs a quote.** A round that claims a match without quoting the report doesn't count.
- **Failures are kept apart from misses.**
  - A missing report abstains.
  - A round with no parsable output is skipped.
  - If every round fails, the result is an error, not a score of zero.

## Breaking changes

### Judge API

Agent Judge removed `JudgmentContext`, `TierPolicy` and `io.github.markpollack.judge.jury.Verdict`. Judges now take their
evidence when they are built rather than when they are called (`Judge.judge()` takes no arguments). Agent Bench follows
that model:

- **`JudgeFactory.createFromConfig(Map)` returns `Function<TrialEvidence, Judge>`.** It used to return a `Judge`. It reads
  and validates the jury configuration once and rejects unknown check types and tier policies at that point. The
  function then builds a fresh judge for each trial.
- **`TrialEvidence` is new.** It carries a trial's workspace, goal and metadata.
- **Custom check types register a `CheckFactory`.** `JudgeFactory.register(String, CheckFactory)` replaces
  `register(String, Function<Map<String, Object>, Judge>)`. A `CheckFactory` receives the check's YAML entry and the
  trial's `TrialEvidence`:

  ```java
  // 0.6.x
  factory.register("my-check", config -> ctx -> myJudgment(ctx.workspace(), config));
  // 1.0.0
  factory.register("my-check", (config, evidence) -> () -> myJudgment(evidence.workspace(), config));
  ```

- **Constructors of the `agent-bench-agents` judges take the workspace first.** `TestQualityJudge` and
  `AnswerKeyRecallJudge` take a `Supplier<? extends Path>` as their first constructor argument and implement
  `judge()`.
- **Judge types moved package.** Agent Judge types now come from their new packages, for example
  `io.github.markpollack.judge.judgment.Judgment` and `io.github.markpollack.judge.verdict.RoutingRule`.

### Removed

- `io.github.markpollack.bench.core.cli.JudgeConfiguration`, the unused Spring `@Configuration` for the hello-world judge.
  The `hello-world` benchmark YAML defines the same checks.
- The `spring-context` dependency of `agent-bench-core`. If your code relied on getting Spring through Agent Bench,
  declare Spring yourself.

## Behaviour

- **Tier policies.** Cascade tier `policy` values are Agent Judge `RoutingRule` names, such as
  `STOP_ON_ANY_OPINION_FAIL`, `STOP_ON_ALL_OPINIONS_PASS`, `STOP_ON_CONCLUSIVE` and `FINAL_TIER`. The 0.6.x names
  still load:

  | 0.6.x name | Read as |
  |---|---|
  | `REJECT_ON_ANY_FAIL` | `STOP_ON_ANY_OPINION_FAIL` |
  | `ACCEPT_ON_ALL_PASS` | `STOP_ON_ALL_OPINIONS_PASS` |

  Existing benchmark files need no edit.
- **Coverage checks.**
  - `coverage-preservation` and `coverage-improvement` still read the task's `baselineCoverage` metadata. They abstain
    when it is missing or unreadable.
  - They also abstain when it is outside 0–100.
  - A missing JaCoCo report is an error, as before.
- **Defaults kept.** The `checks` (all must pass) and `tiers` (cascade with a majority vote per tier) shapes, their
  defaults, and `TrialResult` output are unchanged.

## Licence

There is no licence change. Agent Bench remains under the Business Source License 1.1. The `LICENSE` text now matches the
canonical BSL 1.1 text, and the source headers and the `agent-bench-agents` README that still named the Apache License
now point to the root `LICENSE`. Releases before 0.3.0 remain under their historical Apache License 2.0.

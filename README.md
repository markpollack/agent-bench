# Agent Bench

Agent Bench is a Java framework for defining, running, and grading repeatable AI coding-agent benchmarks.

**Documentation: [lab.pollack.ai/projects/agent-bench](https://lab.pollack.ai/projects/agent-bench)**

## Maven

The current development line is 0.6.0-SNAPSHOT. The latest public release remains 0.5.0 and
publishes these modules:

- `io.github.markpollack:agent-bench-core:0.5.0`
- `io.github.markpollack:agent-bench-agents:0.5.0`

Use the agents module when an LLM-backed judge is required:

```xml
<dependency>
  <groupId>io.github.markpollack</groupId>
  <artifactId>agent-bench-agents</artifactId>
  <version>0.5.0</version>
</dependency>
```

## Build

```bash
./mvnw clean verify
```

## Maturity and safety

Agent Bench is an open benchmarking framework. Benchmark YAML and agent configurations are trusted
code: their setup, post-processing, and configured agent commands execute as local host processes
with the invoking user's permissions. A workspace directory organizes files; it is not a security
boundary. Use your own disposable VM, CI runner, or other externally managed isolation for
untrusted benchmark definitions or agents.

## License

Current development is licensed under the [Business Source License 1.1](LICENSE). Releases before
0.3.0 remain under their historical Apache License 2.0 terms; see
[LICENSE-APACHE.txt](LICENSE-APACHE.txt).

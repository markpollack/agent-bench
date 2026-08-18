# Agent Bench

Agent Bench is a Java framework for defining, running, and grading repeatable AI coding-agent benchmarks.

**Documentation: [lab.pollack.ai/projects/agent-bench](https://lab.pollack.ai/projects/agent-bench)**

## Maven

Agent Bench 0.5.1 publishes these modules:

- `io.github.markpollack:agent-bench-core:0.5.1`
- `io.github.markpollack:agent-bench-agents:0.5.1`

Use the agents module when an LLM-backed judge is required:

```xml
<dependency>
  <groupId>io.github.markpollack</groupId>
  <artifactId>agent-bench-agents</artifactId>
  <version>0.5.1</version>
</dependency>
```

## Build

```bash
./mvnw clean verify
```

## Maturity and safety

Agent Bench is an open benchmarking framework. Benchmark YAML and agent configurations are trusted
code: their setup, post-processing, and agent commands execute processes, and Agent Bench does not
provide host isolation. Use a suitably isolated environment for untrusted workloads.

## License

Current development is licensed under the [Business Source License 1.1](LICENSE). Releases before
0.3.0 remain under their historical Apache License 2.0 terms; see
[LICENSE-APACHE.txt](LICENSE-APACHE.txt).

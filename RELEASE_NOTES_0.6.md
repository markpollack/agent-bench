## Agent Bench 0.6.0

Agent Bench 0.6.0 removes the unused `DockerSandbox` API and its direct Testcontainers dependency.
Agent Bench no longer provides container execution or isolation. Benchmark setup, post-processing,
and configured agent commands run as local host processes with the invoking user's permissions; use
externally managed isolation for untrusted workloads.

The release retains the `Sandbox` abstraction and `LocalSandbox`, Agent Judge-based deterministic
and cascaded grading, the Agent Client-backed LLM judge in `agent-bench-agents`, end-to-end and split
CLI workflows, resume and comparison support, and the Java API. It also raises the exported Jackson
2 and Jackson 3 security floors to 2.21.6 and 3.1.6 respectively.

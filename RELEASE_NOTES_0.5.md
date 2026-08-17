## Agent Bench 0.5.0

- Adds an owner-operated Spring Boot 2.7-to-3.2 upgrade benchmark based on the pinned Spring Cloud Deployer 2.9.5 release, with baseline-aware external grading.
- Aligns the published modules with Agent Judge 0.14.0 and Agent Client 0.26.0.
- Repairs the benchmark workspace setup contract and lets setup/post phases use the task timeout.
- Adds root-only CycloneDX 1.6 SBOM publication and restores a working clean-verification CI gate.

The Spring Boot upgrade benchmark's baseline-aware `ModernizeJudgeMain` grader remains an external integration; the native Agent Bench jury is a build-only quick check in this release.

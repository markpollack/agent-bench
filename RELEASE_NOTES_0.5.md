## Agent Bench 0.5.1

Agent Bench 0.5.1 is a maintenance release that exports the Jackson 3.1.6 floor to ordinary
standalone consumers. This clears CVE-2026-59889 from the shipped compile/runtime dependency graph.

There is no Agent Bench public API, benchmark schema, or benchmark-behavior change in this patch.
The release retains the 0.5 line's Spring upgrade benchmark and its explicit limitation: the
baseline-aware external grader remains an owner-operated integration outside the public repository.

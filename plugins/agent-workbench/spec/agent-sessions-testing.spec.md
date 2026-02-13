---
name: Agent Threads Testing
description: Coverage requirements for provider aggregation, tree rendering, and backend contracts in Agent Threads.
targets:
  - ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceIntegrationTestSupport.kt
  - ../sessions/testSrc/AgentSessionsToolWindowTest.kt
  - ../sessions/testSrc/CodexSessionsPagingLogicTest.kt
  - ../sessions/testSrc/CodexAppServerClientTest.kt
  - ../sessions/testSrc/CodexAppServerClientTestSupport.kt
  - ../sessions/testSrc/CodexTestAppServer.kt
---

# Agent Threads Testing

Status: Draft
Date: 2026-02-11

## Summary
Define required test coverage for the multi-provider Agent Threads stack: source aggregation, service behavior, tree/UI rendering, and Codex backend protocol compatibility.

## Goals
- Keep provider merge behavior stable across refactors.
- Validate refresh/on-demand/concurrency flows against realistic service state transitions.
- Keep UI expectations explicit for warning/error/empty/More states.
- Preserve Codex protocol compatibility with mock and optional real backend tests.

## Non-goals
- End-to-end UI automation outside current module tests.
- Performance benchmarking as part of default test runs.

## Requirements
- Aggregation unit tests must cover:
  - merged ordering by `updatedAt`,
  - partial-provider warnings,
  - all-provider-failure blocking error,
  - unknown total propagation.
- Service integration tests must cover:
  - mixed-provider refresh merge,
  - provider warning and blocking error paths,
  - unknown-count behavior when unknown provider fails/succeeds.
- On-demand integration tests must cover:
  - project request deduplication,
  - worktree request deduplication with refresh interaction.
- Concurrency integration tests must verify refresh mutex deduplicates overlapping refresh calls.
- Tree UI tests must cover:
  - provider warning rendering,
  - error row precedence over warnings,
  - `More…` rendering for unknown count,
  - `More (N)` rendering for exact count.
- Codex compatibility tests must cover cursor-loop/no-progress guard behavior in `seedInitialVisibleThreads`.
- Codex app-server contract tests must run against mock backend always and real backend when available.

[@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceConcurrencyIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt
[@test] ../sessions/testSrc/CodexSessionsPagingLogicTest.kt
[@test] ../sessions/testSrc/CodexAppServerClientTest.kt

## Contract Suite
- `CodexAppServerClientTest` is a parameterized contract test that executes against:
  - Mock app-server (`CodexTestAppServer`) using a synthetic config file.
  - Real `codex app-server` when the CLI is available (skipped otherwise).
- Both backends share the same invariant assertions:
  - Threads are sorted by `updatedAt` descending.
  - `archived` flags are consistent with the requested list.
- The mock backend additionally asserts exact thread IDs because the fixture is deterministic. The real backend uses only invariant assertions because thread data is user-specific and unstable.

[@test] ../sessions/testSrc/CodexAppServerClientTest.kt

## Integration Gating
- The real backend runs when the `codex` CLI is resolvable.
- `CODEX_BIN` can be used to point at a specific binary; otherwise PATH is used.
- Mock backend contract tests are mandatory and must run in CI.

## Isolation
- The test creates a fresh `CODEX_HOME` directory in a temp location.
- A minimal `config.toml` is generated there for the real backend.
- `CODEX_HOME` is set only for the spawned `codex app-server` via per-process environment overrides.
- No global environment state is mutated.

## Running Locally
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsService*IntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.CodexSessionsPagingLogicTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.CodexAppServerClientTest -Dintellij.build.test.main.module=intellij.agent.workbench.sessions'`

Optional real-backend override:
```bash
export CODEX_BIN=/path/to/codex
```

The real backend requires Codex CLI authentication to be available in the environment.

## Open Questions / Risks
- Claude backend contract tests equivalent to `CodexAppServerClientTest` are not yet present; adding them would improve provider parity guarantees.

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-sessions-thread-visibility.spec.md`

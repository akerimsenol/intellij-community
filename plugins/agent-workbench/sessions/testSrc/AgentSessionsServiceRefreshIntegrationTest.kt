// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsServiceRefreshIntegrationTest {
  @Test
  fun refreshMergesMixedProviderThreadsAndMarksUnknownCount() = runBlocking {
    withService(
      sessionSources = listOf(
        ScriptedSessionSource(
          provider = AgentSessionProvider.CODEX,
          canReportExactThreadCount = false,
          listFromOpenProject = { path, _ ->
            if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 100, provider = AgentSessionProvider.CODEX))
            else emptyList()
          },
        ),
        ScriptedSessionSource(
          provider = AgentSessionProvider.CLAUDE,
          listFromOpenProject = { path, _ ->
            if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
            else emptyList()
          },
        ),
      ),
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.providerWarnings).isEmpty()
      assertThat(project.hasUnknownThreadCount).isTrue()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1", "codex-1")
    }
  }

  @Test
  fun refreshShowsProviderWarningWhenOneProviderFails() = runBlocking {
    withService(
      sessionSources = listOf(
        ScriptedSessionSource(
          provider = AgentSessionProvider.CODEX,
          canReportExactThreadCount = false,
          listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
        ),
        ScriptedSessionSource(
          provider = AgentSessionProvider.CLAUDE,
          listFromOpenProject = { path, _ ->
            if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
            else emptyList()
          },
        ),
      ),
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.providerWarnings).hasSize(1)
      assertThat(project.providerWarnings.single().provider).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(project.hasUnknownThreadCount).isFalse()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }

  @Test
  fun refreshShowsBlockingErrorWhenAllProvidersFail() = runBlocking {
    withService(
      sessionSources = listOf(
        ScriptedSessionSource(
          provider = AgentSessionProvider.CODEX,
          canReportExactThreadCount = false,
          listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
        ),
        ScriptedSessionSource(
          provider = AgentSessionProvider.CLAUDE,
          listFromOpenProject = { _, _ -> throw IllegalStateException("claude failed") },
        ),
      ),
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNotNull()
      assertThat(project.providerWarnings).isEmpty()
      assertThat(project.threads).isEmpty()
    }
  }

  @Test
  fun refreshDoesNotMarkUnknownCountWhenOnlyUnknownProviderFails() = runBlocking {
    withService(
      sessionSources = listOf(
        ScriptedSessionSource(
          provider = AgentSessionProvider.CODEX,
          canReportExactThreadCount = false,
          listFromOpenProject = { _, _ -> throw IllegalStateException("codex failed") },
        ),
        ScriptedSessionSource(
          provider = AgentSessionProvider.CLAUDE,
          canReportExactThreadCount = true,
          listFromOpenProject = { path, _ ->
            if (path == PROJECT_PATH) listOf(thread(id = "claude-1", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
            else emptyList()
          },
        ),
      ),
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val project = service.state.value.projects.single { it.path == PROJECT_PATH }
      assertThat(project.errorMessage).isNull()
      assertThat(project.hasUnknownThreadCount).isFalse()
      assertThat(project.threads.map { it.id }).containsExactly("claude-1")
    }
  }
}

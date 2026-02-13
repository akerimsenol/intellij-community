package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionsTreeUiStateServiceTest {
  @Test
  fun projectCollapseStateRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()

    assertTrue(uiState.setProjectCollapsed("/work/project-a", collapsed = true))
    assertTrue(uiState.isProjectCollapsed("/work/project-a"))
    assertFalse(uiState.setProjectCollapsed("/work/project-a", collapsed = true))

    assertTrue(uiState.setProjectCollapsed("/work/project-a", collapsed = false))
    assertFalse(uiState.isProjectCollapsed("/work/project-a"))
  }

  @Test
  fun visibleThreadCountStateRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()

    assertEquals(DEFAULT_VISIBLE_THREAD_COUNT, uiState.getVisibleThreadCount("/work/project-a"))

    assertTrue(uiState.incrementVisibleThreadCount("/work/project-a", delta = 3))
    assertEquals(DEFAULT_VISIBLE_THREAD_COUNT + 3, uiState.getVisibleThreadCount("/work/project-a"))

    assertTrue(uiState.resetVisibleThreadCount("/work/project-a"))
    assertEquals(DEFAULT_VISIBLE_THREAD_COUNT, uiState.getVisibleThreadCount("/work/project-a"))
    assertFalse(uiState.resetVisibleThreadCount("/work/project-a"))
  }

  @Test
  fun pathNormalizationIsAppliedToStoredState() {
    val uiState = AgentSessionsTreeUiStateService()

    uiState.setProjectCollapsed("/work/project-a/", collapsed = true)
    assertTrue(uiState.isProjectCollapsed("/work/project-a"))

    uiState.incrementVisibleThreadCount("/work/project-b/", delta = 3)
    assertEquals(DEFAULT_VISIBLE_THREAD_COUNT + 3, uiState.getVisibleThreadCount("/work/project-b"))
  }

  @Test
  fun openProjectThreadPreviewCacheRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()
    val threads = listOf(
      CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 5L, archived = false),
      CodexThread(id = "thread-2", title = "Thread 2", updatedAt = 10L, archived = false),
    )

    assertTrue(uiState.setOpenProjectThreadPreviews("/work/project-a/", threads))

    val cached = uiState.getOpenProjectThreadPreviews("/work/project-a")
    assertEquals(listOf("thread-2", "thread-1"), cached?.map { it.id })

    assertTrue(uiState.retainOpenProjectThreadPreviews(setOf("/work/project-b")))
    assertNull(uiState.getOpenProjectThreadPreviews("/work/project-a"))
  }
}

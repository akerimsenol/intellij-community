// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers.claude

import com.intellij.agent.workbench.claude.common.ClaudeSessionThread
import com.intellij.agent.workbench.claude.common.ClaudeSessionsStore
import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionThread
import com.intellij.agent.workbench.sessions.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project

internal class ClaudeSessionSource(
  private val store: ClaudeSessionsStore,
) : BaseAgentSessionSource(provider = AgentSessionProvider.CLAUDE) {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return store.listThreads(projectPath = path).map { it.toAgentSessionThread() }
  }
}

private fun ClaudeSessionThread.toAgentSessionThread(): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    provider = AgentSessionProvider.CLAUDE,
    originBranch = gitBranch,
  )
}

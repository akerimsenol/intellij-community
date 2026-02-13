// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers.codex

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexSessionBranchStore
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSubAgent
import com.intellij.agent.workbench.sessions.codex.CodexProjectSessionService
import com.intellij.agent.workbench.sessions.codex.resolveProjectDirectoryFromPath
import com.intellij.agent.workbench.sessions.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

internal class CodexSessionSource(
  private val coroutineScope: CoroutineScope,
  private val branchStore: CodexSessionBranchStore = CodexSessionBranchStore(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CODEX, canReportExactThreadCount = false) {
  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    if (openProject != null) {
      return listThreadsFromOpenProject(project = openProject)
    }
    return listThreadsFromClosedPath(path)
  }

  private suspend fun listThreadsFromOpenProject(project: Project): List<AgentSessionThread> {
    val service = project.getService(CodexProjectSessionService::class.java)
    if (service == null || !service.hasWorkingDirectory()) {
      throw CodexAppServerException("Project directory is not available")
    }
    val threads = service.listThreads()
    return enrichWithBranches(threads).map { it.toAgentSessionThread() }
  }

  private suspend fun listThreadsFromClosedPath(path: String): List<AgentSessionThread> {
    val workingDirectory = resolveProjectDirectoryFromPath(path)
      ?: return emptyList()
    val client = CodexAppServerClient(coroutineScope = coroutineScope, workingDirectory = workingDirectory)
    try {
      val threads = client.listThreads(archived = false)
      return enrichWithBranches(threads).map { it.toAgentSessionThread() }
    }
    finally {
      client.shutdown()
    }
  }

  private fun enrichWithBranches(threads: List<CodexThread>): List<CodexThread> {
    val needBranch = threads.filter { it.gitBranch == null }.map { it.id }.toSet()
    if (needBranch.isEmpty()) return threads
    val branches = branchStore.resolveBranches(needBranch)
    if (branches.isEmpty()) return threads
    return threads.map { thread ->
      if (thread.gitBranch == null) {
        val branch = branches[thread.id]
        if (branch != null) thread.copy(gitBranch = branch) else thread
      }
      else thread
    }
  }
}

private fun CodexThread.toAgentSessionThread(): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    provider = AgentSessionProvider.CODEX,
    subAgents = subAgents.map { AgentSubAgent(it.id, it.name) },
    originBranch = gitBranch,
  )
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers

import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionThread
import com.intellij.openapi.project.Project

internal interface AgentSessionSource {
  val provider: AgentSessionProvider
  val canReportExactThreadCount: Boolean
    get() = true

  suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread>

  suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread>
}

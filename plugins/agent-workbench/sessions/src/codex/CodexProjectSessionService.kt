// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.codex

import com.intellij.agent.workbench.codex.common.CodexAppServerClient
import com.intellij.agent.workbench.codex.common.CodexAppServerException
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadPage
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<CodexProjectSessionService>()

@Service(Service.Level.PROJECT)
internal class CodexProjectSessionService(
  project: Project,
  private val coroutineScope: CoroutineScope,
) {
  private val workingDirectory = resolveProjectDirectory(project)
  private val client = CodexAppServerClient(
    coroutineScope = coroutineScope,
    workingDirectory = workingDirectory,
  )

  init {
    registerShutdownOnCancellation(coroutineScope) { client.shutdown() }
  }

  fun hasWorkingDirectory(): Boolean = workingDirectory != null

  @Suppress("unused")
  suspend fun listThreads(): List<CodexThread> {
    if (workingDirectory == null) {
      throw CodexAppServerException("Project directory is not available")
    }
    // TODO: Add archived session support and unarchive actions.
    return client.listThreads(archived = false)
  }

  suspend fun listThreadsPage(cursor: String?, limit: Int): CodexThreadPage {
    if (workingDirectory == null) {
      throw CodexAppServerException("Project directory is not available")
    }
    // TODO: Add archived session support and unarchive actions.
    return client.listThreadsPage(
      archived = false,
      cursor = cursor,
      limit = limit,
    )
  }

  suspend fun createThread(): CodexThread {
    if (workingDirectory == null) {
      throw CodexAppServerException("Project directory is not available")
    }
    return client.createThread()
  }
}

@OptIn(InternalCoroutinesApi::class)
internal fun registerShutdownOnCancellation(scope: CoroutineScope, onShutdown: () -> Unit) {
  val job = scope.coroutineContext[Job]
  if (job == null) {
    LOG.warn("Codex project session scope has no Job; shutdown hook not installed")
    return
  }
  job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { _ ->
    LOG.debug { "Codex project session scope cancelling; shutting down client" }
    onShutdown()
  }
}

private fun resolveProjectDirectory(project: Project): Path? {
  val recentProjectPath = RecentProjectsManagerBase.getInstanceEx().getProjectPath(project)
  val projectFilePath = project.projectFilePath
  val guessedProjectDir = project.guessProjectDir()
    ?.takeIf { it.isInLocalFileSystem }
    ?.toNioPath()
  return resolveProjectDirectory(
    recentProjectPath = recentProjectPath,
    projectFilePath = projectFilePath,
    basePath = project.basePath,
    guessedProjectDir = guessedProjectDir,
  )
}

@VisibleForTesting
internal fun resolveProjectDirectory(
  recentProjectPath: Path?,
  projectFilePath: String?,
  basePath: String?,
  guessedProjectDir: Path?,
): Path? {
  val candidates = sequenceOf(
    recentProjectPath,
    parseProjectPath(projectFilePath),
    parseProjectPath(basePath),
    guessedProjectDir,
  )
  for (candidate in candidates) {
    val normalized = normalizeProjectPath(candidate) ?: continue
    if (Files.isDirectory(normalized)) {
      return normalized
    }
  }
  return null
}

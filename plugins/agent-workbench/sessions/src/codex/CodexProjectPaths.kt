// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.codex

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun parseProjectPath(path: String?): Path? {
  val trimmed = path?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
  return try {
    Path.of(trimmed)
  }
  catch (_: InvalidPathException) {
    null
  }
}

internal fun normalizeProjectPath(projectPath: Path?): Path? {
  val path = projectPath ?: return null
  val fileName = path.fileName?.toString() ?: return path
  val parentName = path.parent?.fileName?.toString()
  val normalized = when {
    fileName == ".idea" -> path.parent
    parentName == ".idea" -> path.parent?.parent
    fileName.endsWith(".ipr", ignoreCase = true) -> path.parent
    fileName.endsWith(".iws", ignoreCase = true) -> path.parent
    else -> path
  }
  return normalized ?: path
}

internal fun resolveProjectDirectoryFromPath(path: String): Path? {
  val parsed = parseProjectPath(path) ?: return null
  val normalized = normalizeProjectPath(parsed) ?: return null
  return normalized.takeIf { Files.isDirectory(it) }
}

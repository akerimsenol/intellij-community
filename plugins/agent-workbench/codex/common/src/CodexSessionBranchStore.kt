// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves git branch data from Codex session JSONL files.
 *
 * [Dima] Unfortunately, I have found no other way to extract git branch information from Codex.
 *
 * Codex stores session transcripts at `~/.codex/sessions/YYYY/MM/DD/rollout-{sessionId}.jsonl`.
 * The first line of each file contains a `session_meta` record with `payload.git.branch`.
 */
class CodexSessionBranchStore(
  private val codexHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".codex") },
) {
  private val jsonFactory = JsonFactory()

  fun resolveBranches(sessionIds: Set<String>): Map<String, String> {
    if (sessionIds.isEmpty()) return emptyMap()

    val sessionsDir = codexHomeProvider().resolve("sessions")
    if (!Files.isDirectory(sessionsDir)) return emptyMap()

    val result = LinkedHashMap<String, String>()
    val remaining = sessionIds.toMutableSet()

    try {
      Files.walk(sessionsDir).use { stream ->
        val iterator = stream.iterator()
        while (iterator.hasNext() && remaining.isNotEmpty()) {
          val path = iterator.next()
          if (!Files.isRegularFile(path)) continue
          val fileName = path.fileName?.toString() ?: continue
          if (!fileName.startsWith("rollout-") || !fileName.endsWith(".jsonl")) continue

          val fileSessionId = fileName.removePrefix("rollout-").removeSuffix(".jsonl")
          if (fileSessionId !in remaining) continue

          val branch = parseFirstLineBranch(path)
          if (branch != null) {
            result[fileSessionId] = branch
          }
          remaining.remove(fileSessionId)
        }
      }
    }
    catch (_: Throwable) {
      // gracefully handle missing/inaccessible files
    }

    return result
  }

  private fun parseFirstLineBranch(path: Path): String? {
    val firstLine = try {
      Files.newBufferedReader(path).use { it.readLine() }
    }
    catch (_: Throwable) {
      return null
    }
    if (firstLine.isNullOrBlank()) return null

    return try {
      jsonFactory.createParser(firstLine).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        var type: String? = null
        var branch: String? = null
        forEachObjectField(parser) { fieldName ->
          when (fieldName) {
            "type" -> type = readStringOrNull(parser)
            "payload" -> {
              if (parser.currentToken == JsonToken.START_OBJECT) {
                branch = parsePayloadGitBranch(parser)
              }
              else {
                parser.skipChildren()
              }
            }
            else -> parser.skipChildren()
          }
          true
        }
        if (type == "session_meta") branch else null
      }
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun parsePayloadGitBranch(parser: JsonParser): String? {
    var branch: String? = null
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "git" -> {
          if (parser.currentToken == JsonToken.START_OBJECT) {
            forEachObjectField(parser) { gitField ->
              if (gitField == "branch") {
                branch = readStringOrNull(parser)
              }
              else {
                parser.skipChildren()
              }
              true
            }
          }
          else {
            parser.skipChildren()
          }
        }
        else -> parser.skipChildren()
      }
      true
    }
    return branch
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.codex.common.CodexSessionBranchStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CodexSessionBranchStoreTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun resolvesBranchFromSessionMetaLine() {
    val sessionId = "abc-123-def"
    val sessionsDir = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("10")
    Files.createDirectories(sessionsDir)
    val jsonlFile = sessionsDir.resolve("rollout-$sessionId.jsonl")
    Files.writeString(
      jsonlFile,
      """{"type":"session_meta","payload":{"id":"$sessionId","git":{"branch":"feature-x","commit":"abc123"}}}
{"type":"user","message":"hello"}
""",
    )

    val store = CodexSessionBranchStore(codexHomeProvider = { tempDir })
    val result = store.resolveBranches(setOf(sessionId))

    assertThat(result).hasSize(1)
    assertThat(result[sessionId]).isEqualTo("feature-x")
  }

  @Test
  fun returnsEmptyMapWhenSessionsDirMissing() {
    val store = CodexSessionBranchStore(codexHomeProvider = { tempDir })
    val result = store.resolveBranches(setOf("non-existent"))
    assertThat(result).isEmpty()
  }

  @Test
  fun returnsEmptyMapForEmptyInput() {
    val store = CodexSessionBranchStore(codexHomeProvider = { tempDir })
    val result = store.resolveBranches(emptySet())
    assertThat(result).isEmpty()
  }

  @Test
  fun ignoresNonSessionMetaFirstLine() {
    val sessionId = "no-meta-session"
    val sessionsDir = tempDir.resolve("sessions").resolve("2026").resolve("01").resolve("01")
    Files.createDirectories(sessionsDir)
    val jsonlFile = sessionsDir.resolve("rollout-$sessionId.jsonl")
    Files.writeString(
      jsonlFile,
      """{"type":"user","payload":{"id":"$sessionId"}}
""",
    )

    val store = CodexSessionBranchStore(codexHomeProvider = { tempDir })
    val result = store.resolveBranches(setOf(sessionId))

    assertThat(result).isEmpty()
  }

  @Test
  fun handlesMultipleSessionsAcrossDirectories() {
    val session1 = "session-one"
    val session2 = "session-two"
    val dir1 = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("01")
    val dir2 = tempDir.resolve("sessions").resolve("2026").resolve("02").resolve("02")
    Files.createDirectories(dir1)
    Files.createDirectories(dir2)
    Files.writeString(
      dir1.resolve("rollout-$session1.jsonl"),
      """{"type":"session_meta","payload":{"id":"$session1","git":{"branch":"main"}}}
""",
    )
    Files.writeString(
      dir2.resolve("rollout-$session2.jsonl"),
      """{"type":"session_meta","payload":{"id":"$session2","git":{"branch":"develop"}}}
""",
    )

    val store = CodexSessionBranchStore(codexHomeProvider = { tempDir })
    val result = store.resolveBranches(setOf(session1, session2))

    assertThat(result).hasSize(2)
    assertThat(result[session1]).isEqualTo("main")
    assertThat(result[session2]).isEqualTo("develop")
  }

  @Test
  fun handlesMissingGitFieldGracefully() {
    val sessionId = "no-git-field"
    val sessionsDir = tempDir.resolve("sessions").resolve("2026").resolve("01").resolve("01")
    Files.createDirectories(sessionsDir)
    Files.writeString(
      sessionsDir.resolve("rollout-$sessionId.jsonl"),
      """{"type":"session_meta","payload":{"id":"$sessionId"}}
""",
    )

    val store = CodexSessionBranchStore(codexHomeProvider = { tempDir })
    val result = store.resolveBranches(setOf(sessionId))

    assertThat(result).isEmpty()
  }
}

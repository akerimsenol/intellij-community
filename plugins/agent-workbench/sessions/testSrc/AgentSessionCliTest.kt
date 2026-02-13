// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionCliTest {
  @Test
  fun parseIdentityParsesProviderAndSessionId() {
    val parsed = parseAgentSessionIdentity("CODEX:thread-1")

    assertEquals(AgentSessionProvider.CODEX, parsed?.provider)
    assertEquals("thread-1", parsed?.sessionId)
  }

  @Test
  fun parseIdentityRejectsMalformedValue() {
    assertNull(parseAgentSessionIdentity("CODEX"))
    assertNull(parseAgentSessionIdentity("CODEX:"))
    assertNull(parseAgentSessionIdentity(":thread-1"))
    assertNull(parseAgentSessionIdentity("UNKNOWN:thread-1"))
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NewRdCompletionSupport {
  fun isFrontendRdCompletionOnImpl(): Boolean

  companion object {
    @JvmStatic
    fun isFrontendRdCompletionOn(): Boolean = service<NewRdCompletionSupport>().isFrontendRdCompletionOnImpl()
  }
}

internal class NoOpNewCompletionSupport : NewRdCompletionSupport {
  override fun isFrontendRdCompletionOnImpl(): Boolean = false
}
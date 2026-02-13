// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.platform.project.ProjectId
import kotlinx.serialization.Serializable

/**
 * Represents an event about showing/hiding a completion lookup and choosing a current completion item in it.
 */
@Serializable
sealed interface RpcLookupElementEvent {
  /**
   * the current item changed in the current lookup
   */
  @Serializable
  data class CurrentItemChanged(
    val requestId: RpcCompletionRequestId,
    val itemId: RpcCompletionItemId?,
    val focusDegree: LookupFocusDegree,
  ) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("SelectedItem") {
      field("requestId", requestId)
      field("itemId", itemId)
      field("focusDegree", focusDegree)
    }
  }

  /**
   * the arrangement changed in the current lookup
   */
  @Serializable
  data class ArrangementChanged(
    val requestId: RpcCompletionRequestId,
    val arrangementId: RpcCompletionArrangementId,
    val focusDegree: LookupFocusDegree,
  ) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("ArrangementChanged") {
      field("requestId", requestId)
      field("arrangementId", arrangementId)
      field("focusDegree", focusDegree)
    }
  }

  /**
   * the lookup is closed without completion
   */
  @Serializable
  data class Cancel(val projectId: ProjectId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("Cancel") {
      field("projectId", projectId)
    }
  }

  /**
   * the lookup is closed with completion
   */
  @Serializable
  data class ItemSelected(val projectId: ProjectId) : RpcLookupElementEvent {
    override fun toString(): String = buildToString("ItemSelected") {
      field("projectId", projectId)
    }
  }

}


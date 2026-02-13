// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui.sandbox.tests.screenshots.button

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class ButtonTypesPanel : UISandboxScreenshotPanel() {

  override val title: String = "Button types"

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row {
        button("Primary") {}
          .applyToComponent {
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
          }
        button("Secondary") {}
        cell(object : JButton("Focused") {
          init { isOpaque = false }
          override fun hasFocus() = true
        })
        button("Disabled") {}
          .applyToComponent { isEnabled = false }
      }
    }
  }
}

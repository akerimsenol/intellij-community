// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter.getSessionNonSplitOnly
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.application
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.mixedmode.highLevelProcessOrThrow
import com.intellij.xdebugger.impl.mixedmode.lowLevelMixedModeExtensionOrThrow
import com.intellij.xdebugger.impl.mixedmode.lowLevelProcessOrThrow
import com.intellij.xdebugger.impl.ui.SessionTabComponentProvider
import com.intellij.xdebugger.impl.ui.getSessionTabCustomer
import com.intellij.xdebugger.impl.ui.useSplitterView
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent
import javax.swing.JPanel

// TODO: Doesn't work when mixed-mode in RemDev : RIDER-134022
/**
 * Allows customizing of variables view and splitting into 2 components.
 * Notice that you must provide the bottom component of the view by implementing XDebugSessionTabCustomizer in your XDebugProcess
 * @see com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer.getBottomLocalsComponentProvider
 *
 * This component supports working in the mixed mode debugging,
 * If only one debug process provides a custom bottom component,
 * we will show the customized frame view only when a frame of this debug process is chosen.
 * When switching to a frame of a debug process that doesn't provide a custom bottom component, we will show a default frame view
 */
@Internal
class XSplitterWatchesViewImpl(
  sessionProxy: XDebugSessionProxy,
  watchesInVariables: Boolean,
  isVertical: Boolean,
  withToolbar: Boolean) : XWatchesViewImpl(sessionProxy, watchesInVariables, isVertical, withToolbar), DnDNativeTarget, XWatchesView {

  companion object {
    private const val proportionKey = "debugger.immediate.window.in.watches.proportion.key"
  }

  lateinit var splitter: OnePixelSplitter
    private set

  private var myPanel: BorderLayoutPanel? = null
  private var customized = true
  private var localsPanel : JComponent? = null

  override fun createMainPanel(localsPanelComponent: JComponent): JPanel {
    customized = getShowCustomized()
    localsPanel = localsPanelComponent

    addMixedModeListenerIfNeeded()
    return BorderLayoutPanel().also {
      myPanel = it
      updateMainPanel()
    }
  }

  private fun updateMainPanel() {
    val myPanel = requireNotNull(myPanel)
    val localsPanel = requireNotNull(localsPanel)
    myPanel.removeAll()

    if (!customized) {
      val wrappedLocalsPanel = BorderLayoutPanel().addToCenter(localsPanel) // have to wrap it because it's mutated in the super method
      myPanel.addToCenter(super.createMainPanel(wrappedLocalsPanel))
      return
    }

    val monolithSession = getSessionNonSplitOnly(sessionProxy!!)
    val evaluatorComponent =
      if (monolithSession != null) {
        val provider = tryGetBottomComponentProvider(monolithSession, useLowLevelDebugProcessPanel())
                       ?: error("BottomLocalsComponentProvider is not implemented to use SplitterWatchesVariablesView")
        provider.createBottomLocalsComponent(sessionProxy!!)
      }
      else
        SessionTabComponentProvider.getInstance().createBottomLocalsComponent(sessionProxy!!)


    splitter = OnePixelSplitter(true, proportionKey, 0.01f, 0.99f)

    splitter.firstComponent = localsPanel
    splitter.secondComponent = evaluatorComponent

    myPanel.addToCenter(splitter)
  }

  private fun addMixedModeListenerIfNeeded() {
    val monolithSession = getSessionNonSplitOnly(sessionProxy!!) ?: return
    if (!monolithSession.isMixedMode) return

    val lowSupportsCustomization = monolithSession.lowLevelProcessOrThrow.useSplitterView()
    val highSupportsCustomization = monolithSession.highLevelProcessOrThrow.useSplitterView()
    if (lowSupportsCustomization == highSupportsCustomization) return

    monolithSession.addSessionListener(object : XDebugSessionListener {
      override fun stackFrameChanged() {
        updateView()
      }

      override fun sessionPaused() {
        updateView()
      }
    })
  }

  private fun updateView() {
    application.invokeLater {
      val showCustomizedView = getShowCustomized()
      if (customized == showCustomizedView) return@invokeLater

      customized = showCustomizedView
      updateMainPanel()
    }
  }

  private fun getShowCustomized(): Boolean {
    val monolithSession = getSessionNonSplitOnly(sessionProxy!!) ?: return true // split debugger is on, return true to use rider default immediate window view
    if (!monolithSession.isMixedMode) return true

    val lowSupportsCustomization = monolithSession.lowLevelProcessOrThrow.useSplitterView()
    val highSupportsCustomization = monolithSession.highLevelProcessOrThrow.useSplitterView()

    val useLowLevelPanel = useLowLevelDebugProcessPanel() == true
    val useHighLevelPanel = !useLowLevelPanel
    return useLowLevelPanel && lowSupportsCustomization || useHighLevelPanel && highSupportsCustomization
  }

  private fun useLowLevelDebugProcessPanel(): Boolean? {
    val monolithSession = getSessionNonSplitOnly(sessionProxy!!) ?: return null
    if (!monolithSession.isMixedMode) return null
    val frame = monolithSession.currentStackFrame ?: return false
    return monolithSession.lowLevelMixedModeExtensionOrThrow.belongsToMe(frame)
  }

  private fun tryGetBottomComponentProvider(session: XDebugSession, useLowLevelDebugProcessPanel: Boolean?) =
    when (useLowLevelDebugProcessPanel) {
      null -> session.debugProcess
      true -> session.lowLevelProcessOrThrow
      false -> session.highLevelProcessOrThrow
    }.getSessionTabCustomer()?.getBottomLocalsComponentProvider()
}
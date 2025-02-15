// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.Disposable
import com.intellij.platform.lvcs.impl.ActivityItem
import com.intellij.platform.lvcs.impl.ActivityPresentation
import com.intellij.platform.lvcs.impl.ActivitySelection
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.EventDispatcher
import com.intellij.util.text.DateFormatUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JList

internal class ActivityList(presentationFunction: (item: ActivityItem) -> ActivityPresentation?) : JBList<ActivityItem>() {
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  private var allItems = emptyList<ActivityItem>()
  private var visibleItems: Set<ActivityItem>? = null

  val selection: ActivitySelection get() = ActivitySelection(selectedIndices.map { model.getElementAt(it) }, allItems)

  init {
    cellRenderer = ActivityItemRenderer(presentationFunction)
    addListSelectionListener {
      if (it.valueIsAdjusting) return@addListSelectionListener
      eventDispatcher.multicaster.onSelectionChanged(selection)
    }
    addKeyListener(MyEnterListener())
    MyDoubleClickListener().installOn(this)
  }

  fun setItems(items: List<ActivityItem>) {
    allItems = items
    val filteringModel = FilteringListModel(createDefaultListModel(items))
    setModel(filteringModel)
    filteringModel.setFilter { visibleItems?.contains(it) != false }
  }

  fun setVisibleItems(items: Set<ActivityItem>?) {
    visibleItems = items
    (model as? FilteringListModel)?.refilter()
  }

  fun addListener(listener: Listener, parent: Disposable) {
    eventDispatcher.addListener(listener, parent)
  }

  interface Listener : EventListener {
    fun onSelectionChanged(selection: ActivitySelection)
    fun onEnter(): Boolean
    fun onDoubleClick(): Boolean
  }

  private inner class MyEnterListener : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (KeyEvent.VK_ENTER != e.keyCode || e.modifiers != 0) return
      if (selectedIndices.isEmpty()) return
      if (eventDispatcher.listeners.first { it.onEnter() } != null) e.consume()
    }
  }

  private inner class MyDoubleClickListener : DoubleClickListener() {
    override fun onDoubleClick(e: MouseEvent): Boolean {
      if (selectedIndices.isEmpty()) return false
      return eventDispatcher.listeners.first { it.onDoubleClick() } != null
    }
  }
}

class ActivityItemRenderer(val presentationFunction: (item: ActivityItem) -> ActivityPresentation?) : ColoredListCellRenderer<ActivityItem>() {
  override fun customizeCellRenderer(list: JList<out ActivityItem>,
                                     value: ActivityItem,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    val presentation = presentationFunction(value) ?: return
    append(presentation.text)
    if (presentation.text.isNotBlank()) append(", ", SimpleTextAttributes.GRAY_ATTRIBUTES)
    append(DateFormatUtil.formatPrettyDateTime(value.timestamp), SimpleTextAttributes.GRAY_ATTRIBUTES)
  }
}
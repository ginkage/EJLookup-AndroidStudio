package com.ginkage.ejlookup

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView
import java.util.ArrayList
import java.util.HashMap

internal class MyExpandableListAdapter(
  private val context: Context,
  private val groups: ArrayList<String>,
  val data: ArrayList<ArrayList<ResultLine>>
) : BaseExpandableListAdapter() {
  override fun areAllItemsEnabled(): Boolean {
    return true
  }

  private val groupIdx = HashMap<String?, Int>()
  fun addItem(result: ResultLine) {
    val gname = result.group
    val idx = groupIdx[gname]
    val index = idx ?: groups.size
    if (idx == null) {
      groups.add(gname)
      groupIdx[gname] = index
    }
    if (data.size < index + 1) data.add(ArrayList())
    data[index].add(result)
  }

  override fun getChild(groupPosition: Int, childPosition: Int): Any {
    return data[groupPosition][childPosition]
  }

  override fun getChildId(groupPosition: Int, childPosition: Int): Long {
    return childPosition.toLong()
  }

  override fun getChildView(
    groupPosition: Int,
    childPosition: Int,
    isLastChild: Boolean,
    convertView: View?,
    parent: ViewGroup
  ): View {
    val result = getChild(groupPosition, childPosition) as ResultLine
    val textView = TextView(context)
    textView.text = result.data
    textView.textSize = 15.5f
    textView.setPadding(7, 7, 7, 7)
    textView.setLineSpacing(0f, 1.2f)
    if (ResultLine.theme_color == 1) {
      textView.setBackgroundColor(Color.rgb(255, 255, 255))
      textView.setTextColor(Color.rgb(0, 0, 0))
    }
    return textView
  }

  override fun getChildrenCount(groupPosition: Int): Int {
    return data[groupPosition].size
  }

  override fun getGroup(groupPosition: Int): Any {
    return groups[groupPosition]
  }

  override fun getGroupCount(): Int {
    return groups.size
  }

  override fun getGroupId(groupPosition: Int): Long {
    return groupPosition.toLong()
  }

  override fun getGroupView(
    groupPosition: Int,
    isExpanded: Boolean,
    convertView: View?,
    parent: ViewGroup
  ): View {
    val group = getGroup(groupPosition) as String
    val textView = TextView(context)
    textView.gravity = Gravity.CENTER
    textView.minLines = 2
    textView.text = group
    if (ResultLine.theme_color == 1) {
      textView.setBackgroundColor(Color.rgb(255, 255, 255))
      textView.setTextColor(Color.rgb(0, 0, 0))
    }
    return textView
  }

  override fun hasStableIds(): Boolean {
    return true
  }

  override fun isChildSelectable(arg0: Int, arg1: Int): Boolean {
    return true
  }

  fun setData(data: ArrayList<ResultLine?>?) {
    for (cit in data!!) addItem(cit!!)
  }

  init {
    var idx = 0
    for (it in groups) groupIdx[it] = idx++
  }
}

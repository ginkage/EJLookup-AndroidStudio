package com.ginkage.ejlookup

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import java.util.ArrayList

internal class ResultLine(input: String, var group: String) {
  val data = parseResult(input)

  private inner class Span(val start: Int, val end: Int)

  private fun parseResult(input: String): SpannableString? {
    val result = StringBuilder()
    var i0 = -1
    var i1 = -1
    var i2 = -1
    var i3 = -1
    var i4 = -1
    var i5 = -1
    var kanji = -1
    var kana = -1
    var trans = -1
    var roshi = -1
    var p: Int
    val sdict = StringBuilder()
    val skanji = StringBuilder()
    val skana = StringBuilder()
    var strans = StringBuilder()
    var kd = false
    if (++rescount > resmax) return null
    val text = input.toCharArray()
    val len = text.size
    var i = 0
    while (i < len) {
      if (i0 < 0 && text[i] == ')') i0 = i
      else if (i1 < 0 && i3 < 0 && text[i] == '[') i1 = i
      else if (i2 < 0 && i3 < 0 && text[i] == ']') i2 = i
      else if (i4 < 0 && i3 < 0 && text[i] == '{') i4 = i
      else if (i5 < 0 && i3 < 0 && text[i] == '}') i5 = i
      else if (i3 < 0 && text[i] == '/' && (i == 0 || text[i - 1] != '<')) i3 = i
      i++
    }
    i0 = -1
    sdict.append(group)
    if (group.startsWith("kanjidic")) {
      kanji = i0 + 1
      while (kanji < len && text[kanji] == ' ') kanji++
      p = strChr(text, kanji, ' ')
      if (p >= 0) {
        text[p] = '\u0000'
        kana = p + 1
        p = kana
        while (p < len && text[p] != '\u0000') {
          if (text[p].code > 127) {
            kana = p
            break
          }
          p++
        }
        p = strChr(text, kana, '{')
        if (p >= 0) {
          text[p - 1] = '\u0000'
          trans = p
        }
      }
      kd = true
    } else {
      trans = i0 + 1
      if (i1 >= 0 && i2 >= 0 && i1 < i2) {
        text[i1] = '\u0000'
        text[i2] = '\u0000'
        kana = i1 + 1
        trans = i2 + 1
        if (i0 < i1) {
          kanji = i0 + 1
          while (kanji < len && text[kanji] == ' ') kanji++
        }
      }
      if (i3 >= 0 && i3 > i0 && i3 > i1 && i3 > i2 && i3 > i4 && i3 > i5) {
        if (kana < 0) kana = trans
        text[i3] = '\u0000'
        trans = i3 + 1
      }
      if (i4 >= 0 && i5 >= 0 && i4 < i5) {
        text[i4] = '\u0000'
        text[i5] = '\u0000'
        roshi = i4 + 1
      }
    }
    if (kanji >= 0) {
      var end = kanji
      while (end < len && text[end] != '\u0000') end++
      if (end > kanji) {
        end--
        while (end > kanji && text[end] == ' ') text[end--] = '\u0000'
      }
      addSubStr(skanji, text, kanji)
    }
    if (kana >= 0) {
      p = kana
      while (p < len && text[p] != '\u0000') {
        while (p < len && (text[p] == ' ' || text[p] == ',')) p++
        if (p < len && text[p] != '\u0000') {
          val begin = p
          var end = p - 1
          while (p < len && text[p] != '\u0000' && text[p] != ' ' && text[p] != ',') {
            end = p
            p++
          }
          if (end >= begin) {
            if (skana.isNotEmpty()) skana.append('\n')
            skana.append('[').append(String(text, begin, end - begin + 1))
            if (text[begin].code > 127) {
              skana.append(if (kd) " / " else "]\n[")
              skana.append(Nihongo.romanate(text, begin, end))
            }
            skana.append(']')
          }
        }
      }
    }
    if (roshi >= 0) {
      p = roshi
      while (p < len && text[p] != '\u0000') {
        while (p < len && (text[p] == ' ' || text[p] == ',')) p++
        if (p < len && text[p] != '\u0000') {
          val begin = p
          var end = p - 1
          while (p < len && text[p] != '\u0000' && text[p] != ' ' && text[p] != ',') {
            end = p
            p++
          }
          if (end >= begin) {
            if (skana.isNotEmpty()) skana.append('\n')
            skana.append('[').append(text, begin, end - begin + 1).append(']')
          }
        }
      }
    }
    if (trans >= 0) {
      if (kd) {
        p = trans
        while (p < len && text[p] != '\u0000') {
          while (p < len && (text[p] == '{' || text[p] == '}')) p++
          if (p < len && text[p] != '\u0000') {
            while (p < len && text[p] == ' ') p++
            val begin = p
            var end = p - 1
            while (p < len && text[p] != '\u0000' && text[p] != '{' && text[p] != '}') {
              end = p
              p++
            }
            if (end >= begin) {
              if (strans.isNotEmpty()) strans.append('\n')
              strans.append(text, begin, end - begin + 1)
            }
          }
        }
      } else {
        p = trans
        while (p < len && text[p] != '\u0000') {
          if (text[p] == '/' && (p == trans || text[p - 1] != '<')) {
            text[p] = '\u0000'
            p++
            while (trans < len && text[trans] == ' ') trans++
            if (trans < len && text[trans] != '\u0000') {
              if (strans.isNotEmpty()) strans.append('\n')
              addSubStr(strans, text, trans)
            }
            trans = p
          }
          p++
        }
        if (trans >= 0) {
          while (trans < len && text[trans] == ' ') trans++
          if (trans < len && text[trans] != '\u0000') {
            if (strans.isNotEmpty()) strans.append('\n')
            addSubStr(strans, text, trans)
          }
        }
      }
    }
    var kanjistart = -1
    var kanastart = -1
    var transstart = -1
    if (skanji.isNotEmpty()) {
      if (result.isNotEmpty()) result.append('\n')
      kanjistart = result.length
      result.append(skanji)
    }
    val kanjiend = result.length
    if (skana.isNotEmpty()) {
      if (result.isNotEmpty()) result.append('\n')
      kanastart = result.length
      result.append(skana)
    }
    val kanaend = result.length
    val italic = ArrayList<Span>()
    if (strans.isNotEmpty()) {
      if (result.isNotEmpty()) result.append('\n')
      transstart = result.length
      var begin: Int
      var end: Int
      while (strans.indexOf("<i>").also { begin = it } >= 0) {
        result.append(strans.substring(0, begin))
        end = strans.indexOf("</i>", begin + 1)
        val `is` = result.length
        strans = if (end < 0) {
          result.append(strans.substring(begin + 3))
          StringBuilder()
        } else {
          result.append(strans.substring(begin + 3, end))
          StringBuilder(strans.substring(end + 4))
        }
        italic.add(Span(`is`, result.length))
      }
      result.append(strans)
    }
    val res = SpannableString(result)
    if (font_size == 1) res.setSpan(
      RelativeSizeSpan(1.333333f),
      0,
      res.length,
      0
    ) else if (font_size == 2) res.setSpan(RelativeSizeSpan(1.666666f), 0, res.length, 0)
    if (kanjistart >= 0) {
      res.setSpan(
        ForegroundColorSpan(
          if (theme_color == 0) Color.rgb(170, 127, 85) else Color.rgb(127, 63, 31)
        ),
        kanjistart,
        kanjiend,
        0
      )
      res.setSpan(RelativeSizeSpan(1.333333f), kanjistart, kanjiend, 0)
    }
    if (kanastart >= 0) {
      res.setSpan(
        ForegroundColorSpan(
          if (theme_color == 0) Color.rgb(42, 170, 170) else Color.rgb(31, 63, 63)
        ),
        kanastart,
        kanaend,
        0
      )
    }
    if (transstart >= 0) {
      for (s in italic) res.setSpan(StyleSpan(Typeface.ITALIC), s.start, s.end, 0)
    }
    return res
  }

  companion object {
    private var rescount = 0
    private var resmax = 0
    private var font_size = 0
    var theme_color = 0

    fun startFill(fontSize: Int, themeColor: Int) {
      font_size = fontSize
      theme_color = themeColor
      rescount = 0
      resmax = 250
    }

    private fun addSubStr(str: StringBuilder, text: CharArray, begin: Int) {
      val len = text.size
      var end = -1
      var i: Int = begin
      while (i < len && text[i] != '\u0000') {
        end = i
        i++
      }
      if (end >= begin) str.append(text, begin, end - begin + 1)
    }

    private fun strChr(text: CharArray, begin: Int, c: Char): Int {
      val len = text.size
      var i: Int = begin
      while (i < len && text[i] != '\u0000') {
        if (text[i] == c) return i
        i++
      }
      return -1
    }
  }
}
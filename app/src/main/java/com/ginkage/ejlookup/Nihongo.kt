package com.ginkage.ejlookup

import android.content.res.Resources
import java.io.BufferedReader
import java.io.InputStreamReader

internal object Nihongo {
  private lateinit var roma: Array<CharArray>
  private lateinit var kana: Array<CharArray>
  private val hashTab = CharArray(65536)

  private fun readResource(res: Resources, id: Int): Array<CharArray> {
    val dataIn = res.openRawResource(id)
    val isr = InputStreamReader(dataIn, "UTF-8")
    val br = BufferedReader(isr)
    return generateSequence { br.readLine() }.map { it.toCharArray() }.toList().toTypedArray()
  }

  fun init(res: Resources) {
    kana = readResource(res, R.raw.kanatab)
    roma = readResource(res, R.raw.romatab)
  }

  private fun jAiueoy(c: Char): Boolean {
    return (c in '\u3041'..'\u304A' ||
      c in '\u30A1'..'\u30AA' ||
      c in '\u3083'..'\u3088' ||
      c in '\u30E3'..'\u30E8')
  }

  private fun aiueo(c: Char): Boolean {
    return c == 'a' || c == 'i' || c == 'u' || c == 'e' || c == 'o'
  }

  private fun toLower(c: Char): Char {
    return if (c in 'A'..'Z' || c in '\u0410'..'\u042F') (c + 0x20) else c
  }

  fun letter(c: Char): Boolean {
    return (c in '0'..'9' ||
      c in 'A'..'Z' ||
      c in 'a'..'z' ||
      c in '\u00C0'..'\u02A8' ||
      c in '\u0401'..'\u0451' ||
      c == '\u3005' ||
      c in '\u3041'..'\u30FA' ||
      c in '\u4E00'..'\uFA2D' ||
      c in '\uFF10'..'\uFF19' ||
      c in '\uFF21'..'\uFF3A' ||
      c in '\uFF41'..'\uFF5A' ||
      c in '\uFF66'..'\uFF9F')
  }

  private fun findsub(str: CharArray, offset: Int): Int {
    var a = 0
    var b = roma.size - 1
    var cur: Int
    var psub: Int
    var pstr: Int
    while (b - a > 1) {
      cur = (a + b) / 2
      psub = 0
      pstr = offset
      while (pstr < str.size && roma[cur][psub] != '=') {
        if (toLower(str[pstr]) < roma[cur][psub]) {
          b = cur
          break
        } else if (toLower(str[pstr]) > roma[cur][psub]) {
          a = cur
          break
        }
        pstr++
        psub++
      }
      if (roma[cur][psub++] == '=') return cur else if (pstr >= str.size) return -1
    }
    psub = 0
    pstr = offset
    while (pstr < str.size && roma[a][psub] != '=') {
      if (toLower(str[pstr]) != roma[a][psub]) break
      pstr++
      psub++
    }
    if (roma[a][psub++] == '=') return a else if (pstr >= str.size) return -1
    if (a != b) {
      psub = 0
      pstr = offset
      while (pstr <= str.size && roma[b][psub] != '=') {
        if (toLower(str[pstr]) != roma[b][psub]) break
        pstr++
        psub++
      }
      if (roma[b][psub++] == '=') return b
    }
    return -1
  }

  fun kanate(text: CharArray): String {
    var pk = 0
    var pls = 0
    var prs: Int
    var r: Int
    val out = StringBuilder()
    val kanabuf = CharArray(1024)
    var tsu: Boolean
    var pb = 0
    while (pb < text.size) {
      tsu = false
      if (pb + 1 < text.size &&
          toLower(text[pb]) == toLower(text[pb + 1]) &&
          !aiueo(toLower(text[pb]))
      ) {
        if (pb + 2 < text.size &&
            toLower(text[pb]) == 'n' &&
            toLower(text[pb + 1]) == 'n' &&
            toLower(text[pb + 2]) == 'n'
        ) {
          out.append('\u3093')
          pb++
          pb++
          continue
        }
        tsu = true
        pb++
      }
      if (pb < text.size && findsub(text, pb).also { pls = it } >= 0) {
        if (tsu) {
          if (toLower(text[pb - 1]) == 'n') kanabuf[pk++] = '\u3093' else kanabuf[pk++] = '\u3063'
        }
        r = 0
        while (roma[pls][r++] != '=') pb++
        pb--
        prs = pk
        while (r < roma[pls].size) kanabuf[prs++] = roma[pls][r++]
        pk = prs
      } else if (toLower(text[pb]) == 'n' || toLower(text[pb]) == 'm') kanabuf[pk++] = '\u3093'
      else {
        val tmp = CharArray(4)
        pls = -1
        if (pb + 1 < text.size && toLower(text[pb]) == 't' && toLower(text[pb + 1]) == 's') {
          tmp[0] = 't'
          tmp[1] = 's'
          tmp[2] = 'u'
          tmp[3] = '\u0000'
          pls = findsub(tmp, 0)
        }
        if (pb + 1 < text.size && toLower(text[pb]) == 's' && toLower(text[pb + 1]) == 'h') {
          tmp[0] = 's'
          tmp[1] = 'h'
          tmp[2] = 'i'
          tmp[3] = '\u0000'
          pls = findsub(tmp, 0)
        }
        if (pls >= 0) {
          r = 0
          pb++
          while (roma[pls][r] != '=') r++
          prs = pk
          while (r < roma[pls].size) kanabuf[prs++] = roma[pls][r++]
          pk = prs
        } else {
          if (tsu) out.append(text[pb - 1])
          out.append(text[pb])
        }
      }
      if (pk != 0) {
        out.append(kanabuf, 0, pk)
        pk = 0
      }
      pb++
    }
    return out.toString()
  }

  fun romanate(text: CharArray, begin: Int, end: Int): String {
    val out = StringBuilder()
    var pkana: Int
    var pk: Int
    var pi: Int
    var ps: Int
    var pb = begin
    var tsu = false
    while (pb <= end) {
      if (text[pb] in '\u3041'..'\u3094' || text[pb] in '\u30A1'..'\u30FC') {
        if (text[pb] == '\u3063' || text[pb] == '\u30C3') {
          if (pb + 1 <= end &&
              (text[pb + 1] in '\u3041'..'\u3094' || text[pb + 1] in '\u30A1'..'\u30FC')
          )
            tsu = true
          else out.append("ltsu")
          pb++
          continue
        }
        pkana = kana.size - 1
        while (pkana >= 0) {
          pk = 0
          pi = pb
          while (pi <= end && pk < kana[pkana].size && kana[pkana][pk] != '=') {
            if (kana[pkana][pk] != text[pi] && kana[pkana][pk] != text[pi] - 0x60) break
            pk++
            pi++
          }
          if (kana[pkana][pk] == '=') {
            ps = pk + 1
            if (tsu) {
              out.append(kana[pkana][ps])
              tsu = false
            }
            out.append(kana[pkana], ps, kana[pkana].size - ps)
            if (text[pb] == '\u3093' && pb + 1 <= end && jAiueoy(text[pb + 1])) out.append('\'')
            pb = pi - 1
            break
          }
          pkana--
        }
        if (pkana < 0) out.append(text[pb])
      } else out.append(text[pb])
      pb++
    }
    return out.toString()
  }

  fun normalize(buffer: CharArray): Int {
    var p: Int
    var unibuf: Int = 0.also { p = it }
    while (p < buffer.size && buffer[p] != '\u0000') {
      if (buffer[p] in '\uFF61'..'\uFF9F') {
        when (buffer[p]) {
          '\uFF61' -> buffer[p] = '\u3002'
          '\uFF62' -> buffer[p] = '\u300C'
          '\uFF63' -> buffer[p] = '\u300D'
          '\uFF64' -> buffer[p] = '\u3001'
          '\uFF65' -> buffer[p] = '\u30FB'
          '\uFF66' -> buffer[p] = '\u30F2'
          '\uFF67', '\uFF68', '\uFF69', '\uFF6A', '\uFF6B' ->
            buffer[p] = '\u30A1' + (buffer[p] - '\uFF67') * 2
          '\uFF6C', '\uFF6D', '\uFF6E' -> buffer[p] = '\u30E3' + (buffer[p] - '\uFF6C') * 2
          '\uFF6F' -> buffer[p] = '\u30C3'
          '\uFF70' -> buffer[p] = '\u30FC'
          '\uFF71', '\uFF72', '\uFF73', '\uFF74', '\uFF75' ->
            buffer[p] = '\u30A2' + (buffer[p] - '\uFF71') * 2
          '\uFF76',
          '\uFF77',
          '\uFF78',
          '\uFF79',
          '\uFF7A',
          '\uFF7B',
          '\uFF7C',
          '\uFF7D',
          '\uFF7E',
          '\uFF7F',
          '\uFF80',
          '\uFF81' -> buffer[p] = '\u30AB' + (buffer[p] - '\uFF76') * 2
          '\uFF82', '\uFF83', '\uFF84' -> buffer[p] = '\u30C4' + (buffer[p] - '\uFF82') * 2
          '\uFF85', '\uFF86', '\uFF87', '\uFF88', '\uFF89' ->
            buffer[p] = '\u30CA' + (buffer[p] - '\uFF85')
          '\uFF8A', '\uFF8B', '\uFF8C', '\uFF8D', '\uFF8E' ->
            buffer[p] = '\u30CF' + (buffer[p] - '\uFF8A') * 3
          '\uFF8F', '\uFF90', '\uFF91', '\uFF92', '\uFF93' ->
            buffer[p] = '\u30DE' + (buffer[p] - '\uFF8F')
          '\uFF94', '\uFF95', '\uFF96' -> buffer[p] = '\u30E4' + (buffer[p] - '\uFF94') * 2
          '\uFF97', '\uFF98', '\uFF99', '\uFF9A', '\uFF9B' ->
            buffer[p] = '\u30E9' + (buffer[p] - '\uFF97')
          '\uFF9C' -> buffer[p] = '\u30EF'
          '\uFF9D' -> buffer[p] = '\u30F3'
          '\uFF9E' -> if (unibuf > 0) buffer[unibuf - 1] = buffer[unibuf - 1] + 1
          '\uFF9F' -> if (unibuf > 0) buffer[unibuf - 1] = buffer[unibuf - 1] + 2
        }
      }
      if (buffer[p] != '\uFF9E' && buffer[p] != '\uFF9F' && buffer[p] != '\u0301')
        buffer[unibuf++] = hashTab[buffer[p].code]
      p++
    }
    return unibuf
  }

  init {
    for (j in 0..65535) {
      val i = j.toChar()
      hashTab[j] =
        when (i) {
          in 'A'..'Z', in '\u0410'..'\u042F' -> (i + 0x20)
          '\u0451', '\u0401' -> '\u0435'
          '\u040E', '\u045E' -> '\u0443'
          '\u3000' -> '\u0020'
          in '\u30A1'..'\u30F4' -> (i - 0x60)
          in '\uFF01'..'\uFF20' -> (i - 0xFEE0)
          in '\uFF21'..'\uFF3A' -> (i - 0xFEC0)
          in '\uFF3B'..'\uFF5E' -> (i - 0xFEE0)
          else -> i
        }
    }
  }
}

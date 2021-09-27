package com.ginkage.ejlookup

import android.app.SearchManager
import android.content.Context
import android.content.SearchRecentSuggestionsProvider
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.BaseColumns
import android.text.TextUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet
import java.util.TreeMap
import java.util.TreeSet

class SuggestionProvider : SearchRecentSuggestionsProvider() {
  private val uriMatcher: UriMatcher
  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
  ): Cursor? {
    if (uriMatcher.match(uri) == URI_MATCH_SUGGEST) {
      val text = selectionArgs!![0]
      if (!TextUtils.isEmpty(text)) {
        val result = MatrixCursor(COLUMNS)
        val suggest = getLookupResults(context, text, text)
        if (suggest != null) {
          var id = 0
          for (res in suggest) {
            id++
            result.addRow(arrayOf<Any>(id, res, res))
          }
          if (id > 0) {
            return result
          }
        }
      }
    }
    return super.query(uri, projection, selection, selectionArgs, sortOrder)
  }

  internal class Pair(val line: String, val freq: Int) : Comparable<Pair> {
    override fun compareTo(other: Pair): Int {
      return if (freq != other.freq) other.freq - freq else line.compareTo(
        other.line,
        ignoreCase = true
      )
    }
  }

  companion object {
    const val AUTHORITY = "com.ginkage.ejlookup.suggest"
    private val COLUMNS = arrayOf(
      BaseColumns._ID,
      SearchManager.SUGGEST_COLUMN_TEXT_1,
      SearchManager.SUGGEST_COLUMN_QUERY
    )
    private const val URI_MATCH_SUGGEST = 1
    @Throws(IOException::class)
    private fun Tokenize(
      text: CharArray,
      len: Int,
      fileIdx: DictionaryFile,
      suggest: HashMap<String, Int>,
      task: String,
      sugPos: Long
    ): Int {
      var last = -1
      var p = 0
      while (p < len) {
        if (Nihongo.letter(text[p])
          || (text[p] == '\'' && p > 0 && p + 1 < len && Nihongo.letter(text[p - 1])
            && Nihongo.letter(text[p + 1]))
        ) {
          if (last < 0) {
            last = p
          }
        } else if (last >= 0) {
          last = -1
        }
        p++
      }
      if (last >= 0) // Only search for the last word entered
        Traverse(String(text, last, p - last), fileIdx, 0, "", suggest, task, sugPos)
      return last
    }

    fun getLookupResults(context: Context?, request: String, task: String): ArrayList<String>? {
      var result: ArrayList<String>? = null
      val maxsug =
        PreferenceManager.getDefaultSharedPreferences(context)
          .getString(context!!.getString(R.string.setting_max_suggest), "10")!!.toInt()
      val romanize: Boolean = EJLookupActivity.getPrefBoolean(
        context.getString(R.string.setting_suggest_romaji), true
      )
      val text = CharArray(request.length)
      request.toCharArray(text, 0, 0, request.length)
      val kanareq = Nihongo.Kanate(text)
      val kanatext = CharArray(kanareq.length)
      kanareq.toCharArray(kanatext, 0, 0, kanareq.length)
      val qlen = Nihongo.Normalize(text)
      val klen = Nihongo.Normalize(kanatext)
      val suggest = HashMap<String, Int>()
      var last = -1
      try {
        val sugPos: Long = 0
        val fileIdx = DictionaryFile(context.assets, "suggest.dat")
        last = Tokenize(text, qlen, fileIdx, suggest, task, sugPos)
        if (!Arrays.equals(text, kanatext)) Tokenize(kanatext, klen, fileIdx, suggest, task, sugPos)
        fileIdx.close()
      } catch (e: FileNotFoundException) {
        e.printStackTrace()
      } catch (e: UnsupportedEncodingException) {
        e.printStackTrace()
      } catch (e: IOException) {
        e.printStackTrace()
      }
      if (suggest.isNotEmpty() && task == EJLookupActivity.lastQuery) {
        result = ArrayList(10)
        val freq = TreeSet<Pair>()
        for (str in suggest.keys) {
          val n = suggest[str]
          freq.add(Pair(str, n!!))
        }
        val duplicate = HashSet<String?>()
        val begin = if (last >= 0) request.substring(0, last) else ""
        for (pit in freq) if (result.size < maxsug) {
          val str = pit.line
          var k: String? = str
          if (romanize) {
            var convert = true
            var i = 0
            while (i < str.length) {
              if (str[i].code >= 0x3200) {
                convert = false
                break
              }
              i++
            }
            if (convert) {
              val txt = CharArray(str.length)
              str.toCharArray(txt, 0, 0, str.length)
              k = Nihongo.Romanate(txt, 0, str.length - 1)
            }
          }
          if (!duplicate.contains(k)) {
            result.add(begin + k)
            duplicate.add(k)
          }
        }
      }
      return result
    }

    private fun betole(p: Int): Int {
      return ((p and 0x000000ff shl 24)
        + (p and 0x0000ff00 shl 8)
        + (p and 0x00ff0000 ushr 8)
        + (p and -0x1000000 ushr 24))
    }

    private fun shtoch(p: Int): Char {
      return ((p and 0x000000ff shl 8) + (p and 0x0000ff00 ushr 8)).toChar()
    }

    @Throws(IOException::class)
    private fun Traverse(
      what: String,
      fidx: DictionaryFile,
      pos: Long,
      sstr: String,
      suglist: HashMap<String, Int>,
      task: String,
      sugPos: Long
    ): Boolean {
      var word = what
      var str = sstr
      if (task != EJLookupActivity.lastQuery) return false
      fidx.seek(pos + sugPos)
      val tlen = fidx.readUnsignedByte()
      var c = fidx.readUnsignedByte()
      val freq = betole(fidx.readInt())
      val children = c and 1 != 0
      val unicode = c and 8 != 0
      var exact = word != ""
      var match = 0
      var nlen = 0
      var wlen = word.length
      var p: Int
      var ch: Char
      if (pos > 0) {
        var nword = ""
        if (tlen > 0) {
          if (unicode) {
            val wbuf = CharArray(tlen)
            c = 0
            while (c < tlen) {
              wbuf[c] = shtoch(fidx.readUnsignedShort())
              c++
            }
            nword = String(wbuf)
          } else {
            val wbuf = ByteArray(tlen)
            fidx.read(wbuf, 0, tlen)
            nword = String(wbuf)
          }
        }
        nlen = nword.length
        str += nword
        if (exact) {
          word = word.substring(1)
          wlen--
          while (match < wlen && match < nlen) {
            if (word[match] != nword[match]) break
            match++
          }
        }
      }
      if (match == nlen || match == wlen) {
        val cpos = TreeMap<Int, Char>()
        exact = exact && match == nlen
        if (children) // One way or the other, we'll need a full children list
          do { // Read it from this location once, save for later
            ch = shtoch(fidx.readUnsignedShort())
            p = betole(fidx.readInt())
            if (match < wlen) { // (match == nlen), Traverse children
              if (ch == word[match]) {
                val newWord = word.substring(match) // Traverse children
                return Traverse(
                  newWord,
                  fidx,
                  (p and 0x7fffffff).toLong(),
                  str + ch,
                  suglist,
                  task,
                  sugPos
                )
              }
            } else cpos[p and 0x7fffffff] = ch
          } while (p and -0x80000000 == 0)
        if (match == wlen) {
          if (freq > 0 && !exact) {
            var v = suglist[str]
            if (v == null) v = 0
            v += freq
            suglist[str] = v
          }
          for (child_pos in cpos.keys)  // Traverse everything that begins with this word
            Traverse("", fidx, child_pos.toLong(), str + cpos[child_pos], suglist, task, sugPos)
          return true // Got result
        }
      }
      return false // Nothing found
    }
  }

  init {
    setupSuggestions(AUTHORITY, DATABASE_MODE_QUERIES)
    uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    uriMatcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_MATCH_SUGGEST)
  }
}
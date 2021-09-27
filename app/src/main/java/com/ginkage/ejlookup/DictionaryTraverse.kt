package com.ginkage.ejlookup

import android.content.res.AssetManager
import android.util.SparseBooleanArray
import android.util.SparseIntArray
import java.io.FileNotFoundException
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.ArrayList
import java.util.Arrays
import java.util.TreeSet

internal object DictionaryTraverse {
  var filePath: String? = null
  var maxres = 0
  val fileList = arrayOf(
    "jr-edict",
    "warodai",
    "edict",
    "kanjidic",
    "ediclsd4",
    "classical",
    "compverb",
    "compdic",
    "lingdic",
    "jddict",
    "4jword3",
    "aviation",
    "buddhdic",
    "engscidic",
    "envgloss",
    "findic",
    "forsdic_e",
    "forsdic_s",
    "geodic",
    "lawgledt",
    "manufdic",
    "mktdic",
    "pandpdic",
    "stardict",
    "concrete"
  )
  val fileDesc = arrayOf<String?>(
    "Japanese-Russian electronic dictionary",
    "Big Japanese-Russian Dictionary",
    "Japanese-English electronic dictionary",
    "Kanji information",
    "Japanese/English Life Science",
    "Glenn's Classical Japanese dictionary",
    "Handbook of Japanese Compound Verbs",
    "Computing and telecommunications glossary",
    "Francis Bond's J/E Linguistics Dictionary",
    "Japanese-Deutsche Dictionary",
    "4-kanji ideomatic expressions and proverbs",
    "Ron Schei's E/J Aviation Dictionary",
    "Buddhism words and phrases",
    "Engineering and physical sciences",
    "Environmental terms glossary",
    "Financial terms glossary",
    "Forestry terms, English",
    "Forestry terms, Spanish",
    "Geological terminology",
    "University of Washington Japanese-English Legal Glossary",
    "Manufacturing terms",
    "Adam Rice's business & marketing glossary lists",
    "Jim Minor's Pulp & Paper Industry Glossary",
    "Raphael Garrouty's compilation of constellation names",
    "Gururaj Rao's Concrete Terminology Glossary"
  )

  @Throws(IOException::class)
  private fun DoSearch(
    query: String,
    wnum: Int,
    fileIdx: DictionaryFile,
    exact: SparseIntArray,
    partial: SparseIntArray?,
    kanji: Boolean
  ) {
    val mask = 1 shl wnum
    val lines = SparseBooleanArray()
    Traverse(
      query,
      fileIdx,
      0,
      (query.length > 1 || kanji) && partial != null,
      true,
      lines
    )
    val size = lines.size()
    var i = 0
    while (i < size) {
      val k = lines.keyAt(i)
      val e = lines[k]
      val v = (if (e) exact[k] else partial?.get(k) ?: 0) or mask
      if (e) exact.put(k, v) else partial?.put(k, v)
      i++
    }
  }

  @Throws(IOException::class)
  private fun Tokenize(
    text: CharArray,
    len: Int,
    fileIdx: DictionaryFile,
    exact: SparseIntArray,
    partial: SparseIntArray?
  ): Int {
    var last = -1
    var wnum = 0
    var kanji = false
    var p = 0
    while (p < len) {
      if (Nihongo.letter(text[p])
        || (text[p] == '\'' && p > 0 && p + 1 < len && Nihongo.letter(text[p - 1])
          && Nihongo.letter(text[p + 1]))
      ) {
        if (last < 0) last = p
        if (text[p] >= '\u3200') kanji = true
      } else if (last >= 0) {
        DoSearch(
          String(text, last, p - last),
          wnum++,
          fileIdx,
          exact,
          partial,
          kanji
        )
        kanji = false
        last = -1
      }
      p++
    }
    if (last >= 0) DoSearch(
      String(text, last, p - last),
      wnum++,
      fileIdx,
      exact,
      partial,
      kanji
    )
    return wnum
  }

  private fun LookupDict(
    assetManager: AssetManager,
    fileName: String,
    sexact: TreeSet<String>,
    spartial: TreeSet<String>?,
    text: CharArray,
    qlen: Int,
    kanatext: CharArray,
    klen: Int
  ) {
    val iso = charset("ISO-8859-1")
    val utf = charset("UTF-8")
    try {
      if (!EJLookupActivity.getPrefBoolean(fileName, true)) return
      val fileIdx = DictionaryFile(assetManager, "$fileName.idx")
      val elines = SparseIntArray()
      var plines: SparseIntArray? = null
      if (spartial != null) plines = SparseIntArray()
      var qwnum = Tokenize(text, qlen, fileIdx, elines, plines)
      if (!Arrays.equals(text, kanatext)) {
        val kwnum = Tokenize(kanatext, klen, fileIdx, elines, plines)
        if (qwnum < kwnum) qwnum = kwnum
      }
      fileIdx.close()
      val spos = TreeSet<Int>()
      var i: Int
      var size = elines.size()
      i = 0
      while (i < size) {
        val line = elines.keyAt(i)
        val mask = elines[line]
        if (mask + 1 == 1 shl qwnum) {
          spos.add(line)
          plines?.delete(line)
        } else if (plines != null) {
          val pmask = plines[line]
          if (mask or pmask != pmask) plines.put(line, pmask or mask)
        }
        i++
      }
      val fileDic = DictionaryFile(assetManager, "$fileName.utf")
      for (it in spos) if (sexact.size < maxres) {
        fileDic.seek(it.toLong())
        sexact.add(String(fileDic.readLine()!!.toByteArray(iso), utf))
      }
      if (plines != null) {
        spos.clear()
        i = 0
        size = plines.size()
        while (i < size) {
          val line = plines.keyAt(i)
          val mask = plines[line]
          if (mask + 1 == 1 shl qwnum) spos.add(line)
          i++
        }
        for (it in spos) if (sexact.size + spartial!!.size < maxres) {
          fileDic.seek(it.toLong())
          spartial.add(
            String(fileDic.readLine()!!.toByteArray(iso), utf)
          )
        }
      }
      fileDic.close()
    } catch (e: FileNotFoundException) {
      e.printStackTrace()
    } catch (e: UnsupportedEncodingException) {
      e.printStackTrace()
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  fun getLookupResults(context: EJLookupActivity, request: String): ArrayList<ResultLine?> {
    maxres = context.getPrefString(R.string.setting_max_results, "100")!!.toInt()
    val str_partial = context.getString(R.string.text_dictionary_partial)
    val text = CharArray(request.length)
    request.toCharArray(text, 0, 0, request.length)
    val kanareq = Nihongo.Kanate(text)
    val kanatext = CharArray(kanareq.length)
    kanareq.toCharArray(kanatext, 0, 0, kanareq.length)
    val qlen = Nihongo.Normalize(text)
    val klen = Nihongo.Normalize(kanatext)
    val result: ArrayList<ResultLine?> = ArrayList(maxres)
    val sexact = TreeSet<String>()
    val spartial: Array<TreeSet<String>?> = arrayOfNulls(fileList.size)
    var etotal = 0
    var ptotal = 0
    var i = 0
    while (i < fileList.size && etotal < maxres) {
      spartial[i] = TreeSet()
      LookupDict(
        context.assets,
        fileList[i],
        sexact,
        if (etotal + ptotal < maxres) spartial[i] else null,
        text,
        qlen,
        kanatext,
        klen
      )
      ptotal += spartial[i]!!.size
      etotal += sexact.size
      for (st in sexact) if (result.size < maxres) result.add(ResultLine(st, fileList[i]))
      sexact.clear()
      i++
    }
    i = 0
    while (i < fileList.size && result.size < maxres) {
      val partName = fileList[i] + str_partial
      for (st in spartial[i]!!) if (result.size < maxres) result.add(ResultLine(st, partName))
      i++
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
    partial: Boolean,
    child: Boolean,
    poslist: SparseBooleanArray
  ): Boolean {
    var word = what
    fidx.seek(pos)
    val tlen = fidx.readUnsignedByte()
    var c = fidx.readUnsignedByte()
    val children = c and 1 != 0
    val filepos = c and 2 != 0
    val parents = c and 4 != 0
    val unicode = c and 8 != 0
    var exact = word != ""
    var match = 0
    var nlen = 0
    var wlen = word.length
    var p: Int
    if (!exact) fidx.skipBytes(if (unicode) tlen * 2 else tlen) else if (pos > 0) {
      word = word.substring(1)
      wlen--
      if (tlen > 0) {
        val nword: String
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
        nlen = nword.length
        while (match < wlen && match < nlen) {
          if (word[match] != nword[match]) break
          match++
        }
      }
    }
    if (match == nlen || match == wlen) {
      val cpos = ArrayList<Int>()
      if (children) { // One way or the other, we'll need a full children list
        do { // Read it from this location once, save for later
          c = shtoch(fidx.readUnsignedShort()).code
          p = betole(fidx.readInt())
          if (match < wlen) { // (match == nlen), Traverse children
            if (c == word[match].code) {
              val newWord = word.substring(match) // Traverse children
              return Traverse(
                newWord,
                fidx,
                (p and 0x7fffffff).toLong(),
                partial,
                true,
                poslist
              )
            }
          } else if (partial && child) cpos.add(p and 0x7fffffff)
        } while (p and -0x80000000 == 0)
      }
      if (match == wlen) {
        // Our search was successful, word ends here. We'll need all file positions and relatives
        exact = exact && match == nlen
        if (filepos && (match == nlen || partial)) { // Gather all results from this node
          do {
            p = betole(fidx.readInt())
            val k = p and 0x7fffffff
            val idx = poslist.indexOfKey(k)
            if (idx < 0 || !poslist.valueAt(idx) && exact) poslist.put(k, exact)
          } while (p and -0x80000000 == 0)
        }
        if (partial) {
          val ppos = ArrayList<Int>()
          if (parents) { // One way or the other, we'll need a full parents list
            do { // Read it from this location once, save for later
              p = betole(fidx.readInt())
              ppos.add(p and 0x7fffffff)
            } while (p and -0x80000000 == 0)
          }
          if (child) {
            for (it in cpos) { // Traverse everything that begins with this word
              Traverse("", fidx, it.toLong(), partial, true, poslist)
            }
          }
          for (it in ppos) { // Traverse everything that fully has this word in it
            Traverse("", fidx, it.toLong(), partial, false, poslist)
          }
        }
        return true // Got result
      }
    }
    return false // Nothing found
  }
}
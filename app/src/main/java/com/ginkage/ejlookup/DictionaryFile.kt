package com.ginkage.ejlookup

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException

class DictionaryFile(private val assetManager: AssetManager, private val fileName: String) {
  private val scratch = ByteArray(8)
  private val size = open()
  private lateinit var afd: AssetFileDescriptor
  private lateinit var inputStream: FileInputStream
  private var filePointer: Long = 0

  @Throws(IOException::class)
  private fun open(): Long {
    afd = assetManager.openFd(fileName)
    inputStream = afd.createInputStream()
    return afd.declaredLength
  }

  @Throws(IOException::class)
  fun close() {
    inputStream.close()
    afd.close()
  }

  @Throws(IOException::class)
  fun seek(pos: Long) {
    if (pos < filePointer) {
      close()
      open()
      filePointer = inputStream.skip(pos)
    } else {
      filePointer += inputStream.skip(pos - filePointer)
    }
  }

  @Throws(IOException::class)
  fun skipBytes(n: Int): Int {
    if (n <= 0) {
      return 0
    }
    val pos = filePointer
    var newPos = pos + n
    if (newPos > size) {
      newPos = size
    }
    seek(newPos)

    /* return the actual number of bytes skipped */
    return (newPos - pos).toInt()
  }

  @Throws(IOException::class)
  fun readUnsignedByte(): Int {
    val ch = readByte()
    if (ch < 0) throw EOFException()
    return ch
  }

  @Throws(IOException::class)
  fun readUnsignedShort(): Int {
    val ch1 = readByte()
    val ch2 = readByte()
    if (ch1 or ch2 < 0) throw EOFException()
    return (ch1 shl 8) + (ch2 shl 0)
  }

  @Throws(IOException::class)
  fun readInt(): Int {
    val ch1 = readByte()
    val ch2 = readByte()
    val ch3 = readByte()
    val ch4 = readByte()
    if (ch1 or ch2 or ch3 or ch4 < 0) throw EOFException()
    return (ch1 shl 24) + (ch2 shl 16) + (ch3 shl 8) + (ch4 shl 0)
  }

  @Throws(IOException::class)
  fun readLine(): String? {
    val input = StringBuilder()
    var c = -1
    var eol = false
    while (!eol) {
      when (readByte().also { c = it }) {
        -1, '\n'.code -> eol = true
        '\r'.code -> {
          eol = true
          val cur = filePointer
          if (readByte() != '\n'.code) {
            seek(cur)
          }
        }
        else -> input.append(c.toChar())
      }
    }
    return if (c == -1 && input.isEmpty()) {
      null
    } else input.toString()
  }

  @Throws(IOException::class)
  fun readBytes(b: ByteArray, off: Int, len: Int): Int {
    val got = inputStream.read(b, off, len)
    filePointer += got.toLong()
    return got
  }

  @Throws(IOException::class)
  private fun readByte(): Int {
    return if (readBytes(scratch, 0, 1) != -1) scratch[0].toInt() and 0xff else -1
  }
}

package com.ginkage.ejlookup;

import android.content.res.AssetManager;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class DictionaryFile {
    private final byte[] scratch = new byte[8];

    private final AssetManager assetManager;
    private final String fileName;
    private final long size;
    private InputStream inputStream;
    private long currentPos = 0;

    public DictionaryFile(AssetManager assetManager, String fileName) throws IOException {
        this.assetManager = assetManager;
        this.fileName = fileName;
        this.inputStream = assetManager.open(fileName);
        this.size = inputStream.available();
    }

    public void seek(long pos) throws IOException {
        if (pos < currentPos) {
            inputStream.close();
            inputStream = assetManager.open(fileName);
            currentPos = inputStream.skip(pos);
        } else {
            currentPos += inputStream.skip(pos - currentPos);
        }
    }

    public final int readUnsignedByte() throws IOException {
        int ch = this.read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    public int skipBytes(int n) throws IOException {
        long pos;
        long len;
        long newpos;

        if (n <= 0) {
            return 0;
        }
        pos = getFilePointer();
        len = length();
        newpos = pos + n;
        if (newpos > len) {
            newpos = len;
        }
        seek(newpos);

        /* return the actual number of bytes skipped */
        return (int) (newpos - pos);
    }

    public final int readUnsignedShort() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (ch1 << 8) + (ch2 << 0);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return readBytes(b, off, len);
    }

    public final int readInt() throws IOException {
        int ch1 = this.read();
        int ch2 = this.read();
        int ch3 = this.read();
        int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public void close() throws IOException {
        inputStream.close();
    }

    public String readLine() throws IOException {
        StringBuilder input = new StringBuilder();
        int c = -1;
        boolean eol = false;

        while (!eol) {
            switch (c = read()) {
                case -1:
                case '\n':
                    eol = true;
                    break;
                case '\r':
                    eol = true;
                    long cur = getFilePointer();
                    if ((read()) != '\n') {
                        seek(cur);
                    }
                    break;
                default:
                    input.append((char)c);
                    break;
            }
        }

        if ((c == -1) && (input.length() == 0)) {
            return null;
        }
        return input.toString();
    }

    private long length() {
        return size;
    }

    private long getFilePointer() {
        return currentPos;
    }

    private int read() throws IOException {
        return (read(scratch, 0, 1) != -1) ? scratch[0] & 0xff : -1;
    }

    private int readBytes(byte[] b, int off, int len) throws IOException {
        int got = inputStream.read(b, off, len);
        currentPos += got;
        return got;
    }
}

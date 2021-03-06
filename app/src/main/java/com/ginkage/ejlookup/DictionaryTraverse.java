package com.ginkage.ejlookup;

import android.content.res.AssetManager;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;

class DictionaryTraverse {
    public static String filePath;
    public static int maxres;

    public static final String[] fileList = {
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
    };

    public static final String[] fileDesc = {
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
    };

    private static void DoSearch(
            String query,
            int wnum,
            DictionaryFile fileIdx,
            SparseIntArray exact,
            SparseIntArray partial,
            boolean kanji)
            throws IOException {
        int mask = 1 << wnum;
        SparseBooleanArray lines = new SparseBooleanArray();
        Traverse(
                query,
                fileIdx,
                0,
                (query.length() > 1 || kanji) && (partial != null),
                true,
                lines);
        int i, size = lines.size();
        for (i = 0; i < size; i++) {
            int k = lines.keyAt(i);
            boolean e = lines.get(k);
            int v = (e ? exact.get(k) : (partial == null ? 0 : partial.get(k))) | mask;
            if (e) exact.put(k, v);
            else if (partial != null) partial.put(k, v);
        }
    }

    private static int Tokenize(
            char[] text,
            int len,
            DictionaryFile fileIdx,
            SparseIntArray exact,
            SparseIntArray partial)
            throws IOException {
        int p, last = -1, wnum = 0;
        boolean kanji = false;

        for (p = 0; p < len; p++)
            if (Nihongo.letter(text[p])
                    || (text[p] == '\''
                            && p > 0
                            && p + 1 < len
                            && Nihongo.letter(text[p - 1])
                            && Nihongo.letter(text[p + 1]))) {
                if (last < 0) last = p;
                if (text[p] >= 0x3200) kanji = true;
            } else if (last >= 0) {
                DoSearch(
                        new String(text, last, p - last),
                        wnum++,
                        fileIdx,
                        exact,
                        partial,
                        kanji);
                kanji = false;
                last = -1;
            }

        if (last >= 0)
            DoSearch(
                    new String(text, last, p - last),
                    wnum++,
                    fileIdx,
                    exact,
                    partial,
                    kanji);

        return wnum;
    }

    private static void LookupDict(
            AssetManager assetManager,
            String fileName,
            TreeSet<String> sexact,
            TreeSet<String> spartial,
            char[] text,
            int qlen,
            char[] kanatext,
            int klen) {
        try {
            if (!EJLookupActivity.getPrefBoolean(fileName, true)) return;

            DictionaryFile fileIdx = new DictionaryFile(assetManager, fileName + ".idx");
            SparseIntArray elines = new SparseIntArray();
            SparseIntArray plines = null;
            if (spartial != null) plines = new SparseIntArray();

            int qwnum = Tokenize(text, qlen, fileIdx, elines, plines);
            if (!Arrays.equals(text, kanatext)) {
                int kwnum = Tokenize(kanatext, klen, fileIdx, elines, plines);
                if (qwnum < kwnum) qwnum = kwnum;
            }

            fileIdx.close();

            TreeSet<Integer> spos = new TreeSet<>();
            int i, size = elines.size();
            for (i = 0; i < size; i++) {
                int line = elines.keyAt(i);
                int mask = elines.get(line);
                if (mask + 1 == 1 << qwnum) {
                    spos.add(line);
                    if (plines != null) plines.delete(line);
                } else if (plines != null) {
                    int pmask = plines.get(line);
                    if ((mask | pmask) != pmask)
                        plines.put(line, (pmask | mask));
                }
            }

            DictionaryFile fileDic = new DictionaryFile(assetManager, fileName + ".utf");

            for (int it : spos)
                if (sexact.size() < maxres) {
                    fileDic.seek(it);
                    sexact.add(new String(fileDic.readLine().getBytes("ISO-8859-1"), "UTF-8"));
                }

            if (plines != null) {
                spos.clear();
                for (i = 0, size = plines.size(); i < size; i++) {
                    int line = plines.keyAt(i);
                    int mask = plines.get(line);
                    if (mask + 1 == 1 << qwnum) spos.add(line);
                }

                for (int it : spos)
                    if ((sexact.size() + spartial.size()) < maxres) {
                        fileDic.seek(it);
                        spartial.add(
                                new String(fileDic.readLine().getBytes("ISO-8859-1"), "UTF-8"));
                    }
            }

            fileDic.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<ResultLine> getLookupResults(EJLookupActivity context, String request) {
        ArrayList<ResultLine> result;

        maxres = Integer.parseInt(context.getPrefString(R.string.setting_max_results, "100"));
        String str_partial = context.getString(R.string.text_dictionary_partial);

        char[] text = new char[request.length()];
        request.getChars(0, request.length(), text, 0);

        String kanareq = Nihongo.Kanate(text);
        char[] kanatext = new char[kanareq.length()];
        kanareq.getChars(0, kanareq.length(), kanatext, 0);

        int qlen = Nihongo.Normalize(text);
        int klen = Nihongo.Normalize(kanatext);

        result = new ArrayList<>(maxres);

        TreeSet<String> sexact = new TreeSet<>();
        @SuppressWarnings("unchecked")
        TreeSet<String>[] spartial = new TreeSet[fileList.length];

        int i, etotal = 0, ptotal = 0;
        for (i = 0; i < fileList.length && etotal < maxres; i++) {
            spartial[i] = new TreeSet<>();

            LookupDict(
                    context.getAssets(),
                    fileList[i],
                    sexact,
                    ((etotal + ptotal) < maxres ? spartial[i] : null),
                    text,
                    qlen,
                    kanatext,
                    klen);

            ptotal += spartial[i].size();
            etotal += sexact.size();
            for (String st : sexact)
                if (result.size() < maxres) result.add(new ResultLine(st, fileList[i]));
            sexact.clear();
        }

        for (i = 0; i < fileList.length && result.size() < maxres; i++) {
            String partName = fileList[i] + str_partial;
            for (String st : spartial[i])
                if (result.size() < maxres) result.add(new ResultLine(st, partName));
        }

        return result;
    }

    private static int betole(int p) {
        return ((p & 0x000000ff) << 24)
                + ((p & 0x0000ff00) << 8)
                + ((p & 0x00ff0000) >>> 8)
                + ((p & 0xff000000) >>> 24);
    }

    private static int shtoch(int p) {
        return (char) (((p & 0x000000ff) << 8) + ((p & 0x0000ff00) >>> 8));
    }

    private static boolean Traverse(
            String word,
            DictionaryFile fidx,
            long pos,
            boolean partial,
            boolean child,
            SparseBooleanArray poslist)
            throws IOException {
        fidx.seek(pos);

        int tlen = fidx.readUnsignedByte();
        int c = fidx.readUnsignedByte();
        boolean children = ((c & 1) != 0),
                filepos = ((c & 2) != 0),
                parents = ((c & 4) != 0),
                unicode = ((c & 8) != 0),
                exact = !(word.equals(""));
        int match = 0, nlen = 0, wlen = word.length(), p;

        if (!exact) fidx.skipBytes(unicode ? (tlen * 2) : tlen);
        else if (pos > 0) {
            word = word.substring(1);
            wlen--;

            if (tlen > 0) {
                String nword;

                if (unicode) {
                    char[] wbuf = new char[tlen];
                    for (c = 0; c < tlen; c++) wbuf[c] = (char) shtoch(fidx.readUnsignedShort());
                    nword = new String(wbuf);
                } else {
                    byte[] wbuf = new byte[tlen];
                    fidx.read(wbuf, 0, tlen);
                    nword = new String(wbuf);
                }

                nlen = nword.length();

                while (match < wlen && match < nlen) {
                    if (word.charAt(match) != nword.charAt(match)) break;
                    match++;
                }
            }
        }

        if (match == nlen || match == wlen) {
            ArrayList<Integer> cpos = new ArrayList<>();

            if (children) { // One way or the other, we'll need a full children list
                do { // Read it from this location once, save for later
                    c = shtoch(fidx.readUnsignedShort());
                    p = betole(fidx.readInt());
                    if (match < wlen) { // (match == nlen), Traverse children
                        if (c == word.charAt(match)) {
                            String newWord = word.substring(match); // Traverse children
                            return Traverse(
                                    newWord,
                                    fidx,
                                    (p & 0x7fffffff),
                                    partial,
                                    true,
                                    poslist);
                        }
                    } else if (partial && child) cpos.add(p & 0x7fffffff);
                } while ((p & 0x80000000) == 0);
            }

            if (match == wlen) {
                // Our search was successful, word ends here. We'll need all file positions and relatives
                exact = exact && (match == nlen);
                if (filepos && (match == nlen || partial)) { // Gather all results from this node
                    do {
                        p = betole(fidx.readInt());
                        int k = (p & 0x7fffffff);
                        int idx = poslist.indexOfKey(k);
                        if (idx < 0 || (!poslist.valueAt(idx) && exact)) poslist.put(k, exact);
                    } while ((p & 0x80000000) == 0);
                }

                if (partial) {
                    ArrayList<Integer> ppos = new ArrayList<>();
                    if (parents) { // One way or the other, we'll need a full parents list
                        do { // Read it from this location once, save for later
                            p = betole(fidx.readInt());
                            ppos.add(p & 0x7fffffff);
                        } while ((p & 0x80000000) == 0);
                    }

                    if (child) {
                        for (int it : cpos) { // Traverse everything that begins with this word
                            Traverse("", fidx, it, partial, true, poslist);
                        }
                    }

                    for (int it : ppos) { // Traverse everything that fully has this word in it
                        Traverse("", fidx, it, partial, false, poslist);
                    }
                }

                return true; // Got result
            }
        }

        return false; // Nothing found
    }
}

package com.ginkage.ejlookup;

import android.app.SearchManager;
import android.content.Context;
import android.content.SearchRecentSuggestionsProvider;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static android.app.SearchManager.SUGGEST_COLUMN_QUERY;
import static android.app.SearchManager.SUGGEST_COLUMN_TEXT_1;
import static android.provider.BaseColumns._ID;

public class SuggestionProvider extends SearchRecentSuggestionsProvider {
    public static final String AUTHORITY = "com.ginkage.ejlookup.suggest";
    private static final String[] COLUMNS =
            new String[] {_ID, SUGGEST_COLUMN_TEXT_1, SUGGEST_COLUMN_QUERY};

    private final UriMatcher uriMatcher;
    private static final int URI_MATCH_SUGGEST = 1;

    public SuggestionProvider() {
        setupSuggestions(AUTHORITY, DATABASE_MODE_QUERIES);
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_MATCH_SUGGEST);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        if (uriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            String text = selectionArgs[0];
            if (!TextUtils.isEmpty(text)) {
                MatrixCursor result = new MatrixCursor(COLUMNS);
                ArrayList<String> suggest = getLookupResults(getContext(), text, text);
                if (suggest != null) {
                    int id = 0;
                    for (String res : suggest) {
                        id++;
                        result.addRow(new Object[] {id, res, res});
                    }
                    if (id > 0) {
                        return result;
                    }
                }
            }
        }
        return super.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    private static int Tokenize(
            char[] text,
            int len,
            DictionaryFile fileIdx,
            HashMap<String, Integer> suggest,
            String task,
            long sugPos)
            throws IOException {
        int p, last = -1;

        for (p = 0; p < len; p++)
            if (Nihongo.letter(text[p])
                    || (text[p] == '\''
                            && p > 0
                            && p + 1 < len
                            && Nihongo.letter(text[p - 1])
                            && Nihongo.letter(text[p + 1]))) {
                if (last < 0) {
                    last = p;
                }
            } else if (last >= 0) {
                last = -1;
            }

        if (last >= 0) // Only search for the last word entered
        Traverse(new String(text, last, p - last), fileIdx, 0, "", suggest, task, sugPos);

        return last;
    }

    static class Pair implements Comparable<Pair> {
        final String line;
        final int freq;

        public final int compareTo(Pair other) {
            if (this.freq != other.freq) return other.freq - this.freq;
            return this.line.compareToIgnoreCase(other.line);
        }

        Pair(String line, int freq) {
            this.line = line;
            this.freq = freq;
        }
    }

    public static ArrayList<String> getLookupResults(Context context, String request, String task) {
        ArrayList<String> result = null;

        int maxsug =
                Integer.parseInt(
                        PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(context.getString(R.string.setting_max_suggest), "10"));
        boolean romanize =
                EJLookupActivity.getPrefBoolean(
                        context.getString(R.string.setting_suggest_romaji), true);

        char[] text = new char[request.length()];
        request.getChars(0, request.length(), text, 0);

        String kanareq = Nihongo.Kanate(text);
        char[] kanatext = new char[kanareq.length()];
        kanareq.getChars(0, kanareq.length(), kanatext, 0);

        int qlen = Nihongo.Normalize(text);
        int klen = Nihongo.Normalize(kanatext);

        HashMap<String, Integer> suggest = new HashMap<>();

        int last = -1;
        try {
            long sugPos = 0;
            DictionaryFile fileIdx = new DictionaryFile(context.getAssets(), "suggest.dat");
            last = Tokenize(text, qlen, fileIdx, suggest, task, sugPos);
            if (!Arrays.equals(text, kanatext))
                Tokenize(kanatext, klen, fileIdx, suggest, task, sugPos);
            fileIdx.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!suggest.isEmpty() && task.equals(EJLookupActivity.lastQuery)) {
            result = new ArrayList<>(10);
            TreeSet<Pair> freq = new TreeSet<>();
            for (String str : suggest.keySet()) {
                Integer n = suggest.get(str);
                freq.add(new Pair(str, n));
            }

            HashSet<String> duplicate = new HashSet<>();
            String begin = (last >= 0 ? request.substring(0, last) : "");
            for (Pair pit : freq)
                if (result.size() < maxsug) {
                    String str = pit.line;
                    String k = str;

                    if (romanize) {
                        int i;
                        boolean convert = true;
                        for (i = 0; i < str.length(); i++)
                            if (str.charAt(i) >= 0x3200) {
                                convert = false;
                                break;
                            }

                        if (convert) {
                            char[] txt = new char[str.length()];
                            str.getChars(0, str.length(), txt, 0);
                            k = Nihongo.Romanate(txt, 0, str.length() - 1);
                        }
                    }

                    if (!duplicate.contains(k)) {
                        result.add(begin + k);
                        duplicate.add(k);
                    }
                }
        }

        return result;
    }

    private static int betole(int p) {
        return ((p & 0x000000ff) << 24)
                + ((p & 0x0000ff00) << 8)
                + ((p & 0x00ff0000) >>> 8)
                + ((p & 0xff000000) >>> 24);
    }

    private static char shtoch(int p) {
        return (char) (((p & 0x000000ff) << 8) + ((p & 0x0000ff00) >>> 8));
    }

    private static boolean Traverse(
            String word,
            DictionaryFile fidx,
            long pos,
            String str,
            HashMap<String, Integer> suglist,
            String task,
            long sugPos)
            throws IOException {
        if (!task.equals(EJLookupActivity.lastQuery)) return false;
        fidx.seek(pos + sugPos);

        int tlen = fidx.readUnsignedByte();
        int c = fidx.readUnsignedByte();
        int freq = betole(fidx.readInt());
        boolean children = ((c & 1) != 0), unicode = ((c & 8) != 0), exact = !(word.equals(""));
        int match = 0, nlen = 0, wlen = word.length(), p;
        char ch;

        if (pos > 0) {
            String nword = "";

            if (tlen > 0) {
                if (unicode) {
                    char[] wbuf = new char[tlen];
                    for (c = 0; c < tlen; c++) wbuf[c] = shtoch(fidx.readUnsignedShort());
                    nword = new String(wbuf);
                } else {
                    byte[] wbuf = new byte[tlen];
                    fidx.read(wbuf, 0, tlen);
                    nword = new String(wbuf);
                }
            }

            nlen = nword.length();
            str += nword;

            if (exact) {
                word = word.substring(1);
                wlen--;

                while (match < wlen && match < nlen) {
                    if (word.charAt(match) != nword.charAt(match)) break;
                    match++;
                }
            }
        }

        if (match == nlen || match == wlen) {
            TreeMap<Integer, Character> cpos = new TreeMap<>();
            exact = exact && (match == nlen);

            if (children) // One way or the other, we'll need a full children list
            do { // Read it from this location once, save for later
                    ch = shtoch(fidx.readUnsignedShort());
                    p = betole(fidx.readInt());
                    if (match < wlen) { // (match == nlen), Traverse children
                        if (ch == word.charAt(match)) {
                            String newWord = word.substring(match); // Traverse children
                            return Traverse(
                                    newWord,
                                    fidx,
                                    (p & 0x7fffffff),
                                    str + ch,
                                    suglist,
                                    task,
                                    sugPos);
                        }
                    } else cpos.put(p & 0x7fffffff, ch);
                } while ((p & 0x80000000) == 0);

            if (match == wlen) {
                if (freq > 0 && !exact) {
                    Integer v = suglist.get(str);
                    if (v == null) v = 0;
                    v += freq;
                    suglist.put(str, v);
                }

                for (int child_pos :
                        cpos.keySet()) // Traverse everything that begins with this word
                Traverse("", fidx, child_pos, str + cpos.get(child_pos), suglist, task, sugPos);

                return true; // Got result
            }
        }

        return false; // Nothing found
    }
}

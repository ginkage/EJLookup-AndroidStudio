package com.ginkage.ejlookup;

import android.app.SearchManager;
import android.content.SearchRecentSuggestionsProvider;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;

import static android.app.SearchManager.SUGGEST_COLUMN_QUERY;
import static android.app.SearchManager.SUGGEST_COLUMN_TEXT_1;
import static android.provider.BaseColumns._ID;

public class SuggestionProvider extends SearchRecentSuggestionsProvider {
    public static final String AUTHORITY = "com.ginkage.ejlookup.suggest";
    private static final String[] COLUMNS =
            new String[]{_ID, SUGGEST_COLUMN_TEXT_1, SUGGEST_COLUMN_QUERY};

    private UriMatcher uriMatcher;
    private static final int URI_MATCH_SUGGEST = 1;

    public SuggestionProvider() {
        setupSuggestions(AUTHORITY, DATABASE_MODE_QUERIES);
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_MATCH_SUGGEST);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        if (uriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            String text = selectionArgs[0];
            if (!TextUtils.isEmpty(text)) {
                MatrixCursor result = new MatrixCursor(COLUMNS);
                ArrayList<String> suggest = Suggest.getLookupResults(getContext(), text, null);
                if (suggest != null) {
                    int id = 0;
                    for (String res : suggest) {
                        id++;
                        result.addRow(new Object[]{id, res, res});
                    }
                    if (id > 0) {
                        return result;
                    }
                }
            }
        }
        return super.query(uri, projection, selection, selectionArgs, sortOrder);
    }
}

package com.ginkage.ejlookup;

import static android.content.SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.storage.OnObbStateChangeListener;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import com.google.android.vending.expansion.downloader.Helpers;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

public class EJLookupActivity extends AppCompatActivity {
    private static final int ID_DIALOG_ABOUT = 0;
    private static final int ID_DIALOG_NODICT = 1;
    private static ArrayList<ResultLine> reslist = null;
    private static GetLookupResultsTask getResult = null;
    public static boolean keepMount = false;
    private static SharedPreferences preferences = null;
    private SearchView query = null;
    private Button search = null;
    private ExpandableListView results = null;
    private StorageManager storageManager = null;
    private ClipboardManager clipboard = null;
    private InputMethodManager imm = null;
    private String expFile = null;
    private ProgressDialog waitMount = null;
    private boolean initPath = false;
    private boolean bugKitKat = false;
    private SearchRecentSuggestions suggestions;

    public static String lastQuery = null;

    private boolean expansionFilesMissing() {
        expFile = null;
        for (DictionaryDownloaderActivity.XAPKFile xf : DictionaryDownloaderActivity.xAPKS) {
            String fileName = Helpers.getExpansionAPKFileName(this, xf.mIsMain, xf.mFileVersion);
            if (!Helpers.doesFileExist(this, fileName, xf.mFileSize, false)) return true;
            if (xf.mIsMain) expFile = Helpers.generateSaveFileName(this, fileName);
        }
        return false;
    }

    public static boolean getPrefBoolean(String key, boolean defValue) {
        return preferences.getBoolean(key, defValue);
    }

    public static String getPrefString(String key, String defValue) {
        return preferences.getString(key, defValue);
    }

    public String getPrefString(int key, String defValue) {
        return preferences.getString(getString(key), defValue);
    }

    public static void checkPreferences(Context context) {
        if (preferences == null)
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private void setResults() {
        final MyExpandableListAdapter adResults =
                new MyExpandableListAdapter(
                        results.getContext(), new ArrayList<>(), new ArrayList<>());
        adResults.setData(reslist);

        results.setAdapter(adResults);
        int i, groups = adResults.getGroupCount();
        for (i = 0; i < groups; i++) results.expandGroup(i);
        results.requestFocus();
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        String theme =
                getPrefString(
                        R.string.setting_theme_color,
                        (android.os.Build.BRAND.equals("chromium") ? "1" : "0"));
        setTheme(theme.equals("1") ? R.style.AppThemeLight : R.style.AppTheme);

        super.onCreate(savedInstanceState);

        String def_lang = Locale.getDefault().getLanguage();
        String lang = getPrefString(R.string.setting_language, def_lang);
        if (!lang.equals("0")) {
            Locale locale = new Locale(lang);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getBaseContext()
                    .getResources()
                    .updateConfiguration(
                            config, getBaseContext().getResources().getDisplayMetrics());
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        setProgressBarIndeterminateVisibility(getResult != null);
        if (getResult != null) getResult.curContext = new WeakReference<>(this);

        if (expansionFilesMissing()) {
            startActivity(new Intent(EJLookupActivity.this, DictionaryDownloaderActivity.class));
            finish();
            return;
        }

        if (android.os.Build.BRAND.equals("chromium")) bugKitKat = true;

        Nihongo.Init(getResources());

        suggestions =
                new SearchRecentSuggestions(
                        this, SuggestionProvider.AUTHORITY, DATABASE_MODE_QUERIES);

        storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

        query = findViewById(R.id.editQuery);
        results = findViewById(R.id.listResults);
        search = findViewById(R.id.buttonSearch);

        if (searchManager != null) {
            SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
            query.setSearchableInfo(searchableInfo);
            query.setQueryRefinementEnabled(true);
            query.setIconifiedByDefault(false); // Do not iconify the widget; expand it by default
        }

        results.setOnCreateContextMenuListener(
                (menu, v, menuInfo) -> {
                    ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
                    if (ExpandableListView.getPackedPositionType(info.packedPosition)
                            == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                        menu.setHeaderTitle(getString(R.string.app_name));
                        menu.add(0, v.getId(), 0, getString(R.string.text_menu_copy));
                    }
                });

        if (reslist != null) setResults();

        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        query.setOnQueryTextListener(
                new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        searchClicked(search);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        lastQuery = newText;
                        if (!initPath || (!bugKitKat && !storageManager.isObbMounted(expFile))) {
                            Mount();
                        }
                        return false;
                    }
                });

        search.setOnClickListener(this::searchClicked);

        search.setOnLongClickListener(
                v -> {
                    CharSequence paste = null;
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        paste = clip.getItemAt(0).coerceToText(this);
                    }

                    if (paste == null || paste.length() == 0)
                        Toast.makeText(
                                        EJLookupActivity.this,
                                        getString(R.string.text_clipboard_empty),
                                        Toast.LENGTH_LONG)
                                .show();
                    else {
                        query.setQuery(paste, true);
                    }

                    return true;
                });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String text = intent.getStringExtra(SearchManager.QUERY);
            query.setQuery(text, true);
            }
    }

    private final OnObbStateChangeListener mStateListener =
            new OnObbStateChangeListener() {
                public void onObbStateChange(String path, int state) {
                    switch (state) {
                        case MOUNTED:
                            initPath =
                                    DictionaryTraverse.Init(
                                            storageManager.getMountedObbPath(path), false);
                            break;
                        case UNMOUNTED:
                            break;
                        case ERROR_INTERNAL:
                        case ERROR_COULD_NOT_MOUNT:
                            bugKitKat = true;
                            break;
                        case ERROR_COULD_NOT_UNMOUNT:
                            break;
                        case ERROR_NOT_MOUNTED:
                            break;
                        case ERROR_ALREADY_MOUNTED:
                            break;
                        case ERROR_PERMISSION_DENIED:
                            break;
                        default:
                            break;
                    }

                    if (waitMount != null) {
                        waitMount.dismiss();
                        waitMount = null;
                    }
                }
            };

    void Mount() {
        initPath = false;
        keepMount = false;

        if (storageManager.isObbMounted(expFile) || bugKitKat)
            initPath =
                    DictionaryTraverse.Init(
                            bugKitKat ? expFile : storageManager.getMountedObbPath(expFile),
                            bugKitKat);
        else if (waitMount == null) {
            waitMount =
                    ProgressDialog.show(
                            this,
                            getString(R.string.mount_dlg_text),
                            getString(R.string.mount_dlg_head),
                            true);
            storageManager.mountObb(expFile, null, mStateListener);
        }
    }

    void Unmount() {
        if (storageManager != null && storageManager.isObbMounted(expFile))
            storageManager.unmountObb(expFile, false, mStateListener);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        keepMount = true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (expansionFilesMissing()) {
            finish();
            return;
        }

        Mount();
    }

    @Override
    protected void onStop() {
        if (getResult != null) getResult.curContext = null;
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (!keepMount) Unmount();

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == ID_DIALOG_ABOUT) return createAboutDialog(this);
        else if (id == ID_DIALOG_NODICT) {
            final TextView message = new TextView(this);
            final SpannableString s =
                    new SpannableString(getString(R.string.text_dictionary_missing));
            message.setPadding(5, 5, 5, 5);
            message.setText(s);
            message.setGravity(Gravity.CENTER);

            return new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.app_name))
                    .setCancelable(true)
                    .setIcon(R.drawable.icon)
                    .setPositiveButton(getString(android.R.string.ok), null)
                    .setView(message)
                    .create();
        }

        return super.onCreateDialog(id);
    }

    private static AlertDialog createAboutDialog(Context context) {
        // Try to load the a package matching the name of our own package
        PackageInfo pInfo;
        String versionInfo = "0.01";
        try {
            pInfo =
                    context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            versionInfo = pInfo.versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        String aboutTitle =
                String.format(
                        context.getString(R.string.about_dlg_title),
                        context.getString(R.string.app_name));
        String versionString =
                String.format(context.getString(R.string.about_dlg_version), versionInfo);
        String aboutText = context.getString(R.string.about_dlg_sources);

        final TextView message = new TextView(context);
        final SpannableString s = new SpannableString(aboutText);

        message.setPadding(5, 5, 5, 5);
        message.setText(String.format("%s\n\n%s", versionString, s));
        message.setGravity(Gravity.CENTER);
        Linkify.addLinks(message, Linkify.ALL);

        return new AlertDialog.Builder(context)
                .setTitle(aboutTitle)
                .setCancelable(true)
                .setIcon(R.drawable.icon)
                .setPositiveButton(context.getString(android.R.string.ok), null)
                .setView(message)
                .create();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.itemSettings) {
            Intent i = new Intent(EJLookupActivity.this, Settings.class);
            startActivity(i);
        } else if (item.getItemId() == R.id.itemAbout) showDialog(ID_DIALOG_ABOUT);
        else return false;
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuInfo menuInfo = item.getMenuInfo();
        if (menuInfo instanceof ExpandableListContextMenuInfo) {
            ExpandableListContextMenuInfo info =
                    (ExpandableListContextMenuInfo) item.getMenuInfo();
            TextView textView = (TextView) info.targetView;
            clipboard.setPrimaryClip(ClipData.newPlainText(null,
                    textView.getText().toString()));
        } else return false;
        return true;
    }

    public void searchClicked(View v) {
        if (!initPath || (!bugKitKat && !storageManager.isObbMounted(expFile))) {
            Toast.makeText(this, getString(R.string.text_mount_error), Toast.LENGTH_LONG).show();
            Mount();
        } else if (query.getQuery().length() == 0) {
            Toast.makeText(this, getString(R.string.text_query_missing), Toast.LENGTH_LONG).show();
        } else if (getResult == null) {
            imm.hideSoftInputFromWindow(query.getWindowToken(), 0);

            setProgressBarIndeterminateVisibility(true);
            results.setAdapter((MyExpandableListAdapter) null);

            reslist = null;
            getResult = new GetLookupResultsTask(this);

            String text = query.getQuery().toString();
            suggestions.saveRecentQuery(text, null);
            getResult.execute(text);
        }
    }

    private static class GetLookupResultsTask extends AsyncTask<String, Integer,
            ArrayList<ResultLine>> {
        private WeakReference<EJLookupActivity> curContext;

        private GetLookupResultsTask(EJLookupActivity activity) {
            curContext = new WeakReference<>(activity);
        }

        @Override
        protected ArrayList<ResultLine> doInBackground(String... args) {
            EJLookupActivity activity = curContext == null ? null : curContext.get();
            if (args == null || activity == null) return null;

            String request = args[0];

            int font_size = 0;
            String fsize = activity.getPrefString(R.string.setting_font_size, "0");
            if (fsize.equals("1")) font_size = 1;
            else if (fsize.equals("2")) font_size = 2;

            int theme_color = 0;
            String theme = activity.getPrefString(R.string.setting_theme_color, "0");
            if (theme.equals("1")) theme_color = 1;

            ResultLine.StartFill(font_size, theme_color);
            return DictionaryTraverse.getLookupResults(activity, request);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {}

        @Override
        protected void onPostExecute(ArrayList<ResultLine> lines) {
            reslist = lines;

            EJLookupActivity activity = curContext == null ? null : curContext.get();
            if (activity != null) {
                if (lines == null)
                    Toast.makeText(
                                    activity.getApplicationContext(),
                                    activity.getString(R.string.text_error_unknown),
                                    Toast.LENGTH_LONG)
                            .show();
                else if (lines.size() == 0) {
                    if (DictionaryTraverse.hasDicts)
                        Toast.makeText(
                                        activity.getApplicationContext(),
                                        activity.getString(R.string.text_found_nothing),
                                        Toast.LENGTH_LONG)
                                .show();
                    else activity.showDialog(ID_DIALOG_NODICT);
                } else {
                    if (lines.size() >= DictionaryTraverse.maxres)
                        Toast.makeText(
                                        activity.getApplicationContext(),
                                        String.format(
                                                activity.getString(R.string.text_found_toomuch),
                                                DictionaryTraverse.maxres),
                                        Toast.LENGTH_LONG)
                                .show();

                    activity.setResults();
                }

                activity.setProgressBarIndeterminateVisibility(false);
            }

            getResult = null;
        }

        @Override
        protected void onCancelled() {
            EJLookupActivity activity = curContext == null ? null : curContext.get();
            if (activity == null) return;
            activity.setProgressBarIndeterminateVisibility(false);
            getResult = null;
        }
    }
}

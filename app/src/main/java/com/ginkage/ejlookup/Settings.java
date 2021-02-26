package com.ginkage.ejlookup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.Locale;

public class Settings extends PreferenceActivity {
    SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        final String theme =
                EJLookupActivity.getPrefString(getString(R.string.setting_theme_color), "0");
        setTheme(theme.equals("1") ? R.style.AppThemeLight : R.style.AppTheme);
        final String def_lang = Locale.getDefault().getLanguage();
        final String lang =
                EJLookupActivity.getPrefString(getString(R.string.setting_language), def_lang);

        final Context ctx = this;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        listener =
                (prefs, key) -> {
                    if (key.equals(getString(R.string.setting_theme_color))
                            || key.equals(getString(R.string.setting_language))) {
                        String new_theme =
                                EJLookupActivity.getPrefString(
                                        getString(R.string.setting_theme_color), "0");
                        String new_lang =
                                EJLookupActivity.getPrefString(
                                        getString(R.string.setting_language), def_lang);
                        if ((!new_lang.equals(lang) && !new_lang.equals("0"))
                                || !new_theme.equals(theme)) {
                            final TextView message = new TextView(ctx);
                            message.setText(getString(R.string.restart_msg)); //"");
                            message.setPadding(5, 5, 5, 5);
                            message.setGravity(Gravity.CENTER);

                            AlertDialog alertDialog =
                                    new androidx.appcompat.app.AlertDialog.Builder(ctx)
                                            .setTitle(getString(R.string.app_name))
                                            .setIcon(R.drawable.icon)
                                            .setPositiveButton(getString(android.R.string.ok), null)
                                            .setView(message)
                                            .create();

                            alertDialog.setOnDismissListener(
                                    dialog -> {
                                        Intent i =
                                                getBaseContext()
                                                        .getPackageManager()
                                                        .getLaunchIntentForPackage(
                                                                getBaseContext().getPackageName());
                                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        startActivity(i);
                                        System.exit(0);
                                    });

                            alertDialog.show();
                        }
                    }
                };
        preferences.registerOnSharedPreferenceChangeListener(listener);

        super.onCreate(savedInstanceState);
        EJLookupActivity.checkPreferences(this);
        EJLookupActivity.getPrefBoolean(getString(R.string.setting_suggest_romaji), true);
        addPreferencesFromResource(R.xml.settings);
        PreferenceCategory listDict =
                (PreferenceCategory) findPreference(getString(R.string.setting_dictionaries));

        int i = 0;
        for (String fileName : DictionaryTraverse.fileList) {
            boolean checked = EJLookupActivity.getPrefBoolean(fileName, true);

            CheckBoxPreference cb = new CheckBoxPreference(this);
            cb.setKey(fileName);
            cb.setTitle(fileName);
            cb.setSummary(DictionaryTraverse.fileDesc[i++]);
            cb.setChecked(checked);
            listDict.addPreference(cb);
        }
    }
}

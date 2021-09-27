package com.ginkage.ejlookup

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity
import android.preference.PreferenceCategory
import android.preference.PreferenceManager
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ginkage.ejlookup.EJLookupActivity
import java.util.Locale

class Settings : PreferenceActivity() {
  var listener: OnSharedPreferenceChangeListener? = null
  public override fun onCreate(savedInstanceState: Bundle?) {
    val theme: String? =
      EJLookupActivity.getPrefString(getString(R.string.setting_theme_color), "0")
    setTheme(if (theme == "1") R.style.AppThemeLight else R.style.AppTheme)
    val def_lang = Locale.getDefault().language
    val lang: String? =
      EJLookupActivity.getPrefString(getString(R.string.setting_language), def_lang)
    val ctx: Context = this
    val preferences = PreferenceManager.getDefaultSharedPreferences(this)
    listener = OnSharedPreferenceChangeListener { prefs: SharedPreferences?, key: String ->
      if (key == getString(R.string.setting_theme_color) || key == getString(R.string.setting_language)) {
        val new_theme: String? = EJLookupActivity.getPrefString(
          getString(R.string.setting_theme_color), "0"
        )
        val new_lang: String? = EJLookupActivity.getPrefString(
          getString(R.string.setting_language), def_lang
        )
        if (new_lang != lang && new_lang != "0"
          || new_theme != theme
        ) {
          val message = TextView(ctx)
          message.text = getString(R.string.restart_msg) //"");
          message.setPadding(5, 5, 5, 5)
          message.gravity = Gravity.CENTER
          val alertDialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.app_name))
            .setIcon(R.drawable.icon)
            .setPositiveButton(getString(android.R.string.ok), null)
            .setView(message)
            .create()
          alertDialog.setOnDismissListener { dialog: DialogInterface? ->
            val i = baseContext
              .packageManager
              .getLaunchIntentForPackage(
                baseContext.packageName
              )
            i!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
            System.exit(0)
          }
          alertDialog.show()
        }
      }
    }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    super.onCreate(savedInstanceState)
    EJLookupActivity.checkPreferences(this)
    EJLookupActivity.getPrefBoolean(getString(R.string.setting_suggest_romaji), true)
    addPreferencesFromResource(R.xml.settings)
    val listDict = findPreference(getString(R.string.setting_dictionaries)) as PreferenceCategory

    for ((i, fileName) in DictionaryTraverse.fileList.withIndex()) {
      val checked: Boolean = EJLookupActivity.getPrefBoolean(fileName, true)
      val cb = CheckBoxPreference(this)
      cb.key = fileName
      cb.title = fileName
      cb.summary = DictionaryTraverse.fileDesc[i]
      cb.isChecked = checked
      listDict.addPreference(cb)
    }
  }
}
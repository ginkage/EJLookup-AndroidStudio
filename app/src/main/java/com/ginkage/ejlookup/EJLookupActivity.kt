package com.ginkage.ejlookup

import android.app.Dialog
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SearchRecentSuggestionsProvider
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.SearchRecentSuggestions
import android.text.SpannableString
import android.text.util.Linkify
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ExpandableListView
import android.widget.ExpandableListView.ExpandableListContextMenuInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Locale

class EJLookupActivity : AppCompatActivity() {
  private var query: SearchView? = null
  private var search: Button? = null
  private var results: ExpandableListView? = null
  private var clipboard: ClipboardManager? = null
  private var imm: InputMethodManager? = null
  private var suggestions: SearchRecentSuggestions? = null

  fun getPrefString(key: Int, defValue: String?): String? {
    return preferences!!.getString(getString(key), defValue)
  }

  private fun setResults() {
    val adResults = MyExpandableListAdapter(results!!.context, ArrayList(), ArrayList())
    adResults.setData(reslist)
    results!!.setAdapter(adResults)
    for (i in 0 until adResults.groupCount)
      results!!.expandGroup(i)
    results!!.requestFocus()
  }

  /** Called when the activity is first created.  */
  public override fun onCreate(savedInstanceState: Bundle?) {
    preferences = PreferenceManager.getDefaultSharedPreferences(this)
    val theme = getPrefString(
      R.string.setting_theme_color,
      if (Build.BRAND == "chromium") "1" else "0"
    )
    setTheme(if (theme == "1") R.style.AppThemeLight else R.style.AppTheme)
    super.onCreate(savedInstanceState)
    val defLang = Locale.getDefault().language
    val lang = getPrefString(R.string.setting_language, defLang)
    if (lang != "0") {
      val locale = Locale(lang!!)
      Locale.setDefault(locale)
      val config = Configuration()
      config.locale = locale
      baseContext
        .resources
        .updateConfiguration(
          config, baseContext.resources.displayMetrics
        )
    }
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setContentView(R.layout.main)
    setProgressBarIndeterminateVisibility(getResult != null)
    if (getResult != null) getResult!!.curContext = WeakReference(this)
    Nihongo.init(resources)
    suggestions = SearchRecentSuggestions(
      this,
      SuggestionProvider.AUTHORITY,
      SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES
    )
    clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
    query = findViewById(R.id.editQuery)
    results = findViewById(R.id.listResults)
    search = findViewById(R.id.buttonSearch)
    val searchableInfo = searchManager.getSearchableInfo(componentName)
    query!!.setSearchableInfo(searchableInfo)
    query!!.isQueryRefinementEnabled = true
    query!!.setIconifiedByDefault(false) // Do not iconify the widget; expand it by default
    results!!.setOnCreateContextMenuListener { menu: ContextMenu, v: View, menuInfo: ContextMenuInfo ->
      val info = menuInfo as ExpandableListContextMenuInfo
      if (ExpandableListView.getPackedPositionType(info.packedPosition)
        == ExpandableListView.PACKED_POSITION_TYPE_CHILD
      ) {
        menu.setHeaderTitle(getString(R.string.app_name))
        menu.add(0, v.id, 0, getString(R.string.text_menu_copy))
      }
    }
    if (reslist != null) setResults()
    query!!.imeOptions = EditorInfo.IME_ACTION_SEARCH
    query!!.setOnQueryTextListener(
      object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
          searchClicked()
          return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
          lastQuery = newText
          return false
        }
      })
    search!!.setOnClickListener { searchClicked() }
    search!!.setOnLongClickListener {
      var paste: CharSequence? = null
      val clip = clipboard!!.primaryClip
      if (clip != null && clip.itemCount > 0) {
        paste = clip.getItemAt(0).coerceToText(this)
      }
      if (paste == null || paste.isEmpty()) Toast.makeText(
        this@EJLookupActivity,
        getString(R.string.text_clipboard_empty),
        Toast.LENGTH_LONG
      )
        .show() else {
        query!!.setQuery(paste, true)
      }
      true
    }
    handleIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    handleIntent(intent)
    super.onNewIntent(intent)
  }

  private fun handleIntent(intent: Intent) {
    if (Intent.ACTION_SEARCH == intent.action) {
      val text = intent.getStringExtra(SearchManager.QUERY)
      query!!.setQuery(text, true)
    }
  }

  override fun onStop() {
    if (getResult != null) getResult!!.curContext = null
    super.onStop()
  }

  override fun onCreateDialog(id: Int): Dialog {
    return if (id == ID_DIALOG_ABOUT) createAboutDialog(
      this
    ) else super.onCreateDialog(id)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
        R.id.itemSettings -> {
          val i = Intent(this@EJLookupActivity, Settings::class.java)
          startActivity(i)
        }
        R.id.itemAbout -> showDialog(ID_DIALOG_ABOUT)
        else -> return false
    }
    return true
  }

  override fun onContextItemSelected(item: MenuItem): Boolean {
    val menuInfo = item.menuInfo
    if (menuInfo is ExpandableListContextMenuInfo) {
      val info = item.menuInfo as ExpandableListContextMenuInfo
      val textView = info.targetView as TextView
      clipboard!!.setPrimaryClip(
        ClipData.newPlainText(
          null,
          textView.text.toString()
        )
      )
    } else return false
    return true
  }

  fun searchClicked() {
    if (query!!.query.isEmpty()) {
      Toast.makeText(this, getString(R.string.text_query_missing), Toast.LENGTH_LONG).show()
    } else if (getResult == null) {
      imm!!.hideSoftInputFromWindow(query!!.windowToken, 0)
      setProgressBarIndeterminateVisibility(true)
      results!!.setAdapter(null as MyExpandableListAdapter?)
      reslist = null
      getResult = GetLookupResultsTask(this)
      val text = query!!.query.toString()
      suggestions!!.saveRecentQuery(text, null)
      getResult!!.execute(text)
    }
  }

  private class GetLookupResultsTask(activity: EJLookupActivity) :
    AsyncTask<String?, Int?, ArrayList<ResultLine?>?>() {
    var curContext: WeakReference<EJLookupActivity>? = WeakReference(activity)

    override fun doInBackground(vararg args: String?): ArrayList<ResultLine?>? {
      val activity = curContext?.get()
      if (args == null || activity == null) return null
      val request = args[0]
      var fontSize = 0
      val fsize = activity.getPrefString(R.string.setting_font_size, "0")
      if (fsize == "1") fontSize = 1 else if (fsize == "2") fontSize = 2
      var themeColor = 0
      val theme = activity.getPrefString(R.string.setting_theme_color, "0")
      if (theme == "1") themeColor = 1
      ResultLine.startFill(fontSize, themeColor)
      return DictionaryTraverse.getLookupResults(activity, request!!)
    }

    override fun onProgressUpdate(vararg progress: Int?) {}

    override fun onPostExecute(lines: ArrayList<ResultLine?>?) {
      reslist = lines
      val activity = curContext?.get()
      if (activity != null) {
        when {
            lines == null -> Toast.makeText(
                    activity.applicationContext,
                    activity.getString(R.string.text_error_unknown),
                    Toast.LENGTH_LONG
            )
                    .show()
            lines.size == 0 -> {
              Toast.makeText(
                      activity.applicationContext,
                      activity.getString(R.string.text_found_nothing),
                      Toast.LENGTH_LONG
              )
                      .show()
            }
            else -> {
              if (lines.size >= DictionaryTraverse.maxres) Toast.makeText(
                      activity.applicationContext, String.format(
                      activity.getString(R.string.text_found_toomuch),
                      DictionaryTraverse.maxres
              ),
                      Toast.LENGTH_LONG
              )
                      .show()
              activity.setResults()
            }
        }
        activity.setProgressBarIndeterminateVisibility(false)
      }
      getResult = null
    }

    override fun onCancelled() {
      val activity = curContext?.get() ?: return
      activity.setProgressBarIndeterminateVisibility(false)
      getResult = null
    }
  }

  companion object {
    private const val ID_DIALOG_ABOUT = 0
    private var reslist: ArrayList<ResultLine?>? = null
    private var getResult: GetLookupResultsTask? = null
    private var preferences: SharedPreferences? = null
    var lastQuery: String? = null

    fun getPrefBoolean(key: String?, defValue: Boolean): Boolean {
      return preferences!!.getBoolean(key, defValue)
    }

    fun getPrefString(key: String?, defValue: String?): String? {
      return preferences!!.getString(key, defValue)
    }

    fun checkPreferences(context: Context?) {
      if (preferences == null) preferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun createAboutDialog(context: Context): AlertDialog {
      // Try to load the a package matching the name of our own package
      val pInfo: PackageInfo
      var versionInfo: String? = "0.01"
      try {
        pInfo = context.packageManager
          .getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
        versionInfo = pInfo.versionName
      } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
      }
      val aboutTitle = String.format(
        context.getString(R.string.about_dlg_title),
        context.getString(R.string.app_name)
      )
      val versionString = String.format(context.getString(R.string.about_dlg_version), versionInfo)
      val aboutText = context.getString(R.string.about_dlg_sources)
      val message = TextView(context)
      val s = SpannableString(aboutText)
      message.setPadding(5, 5, 5, 5)
      message.text = String.format("%s\n\n%s", versionString, s)
      message.gravity = Gravity.CENTER
      Linkify.addLinks(message, Linkify.ALL)
      return AlertDialog.Builder(context)
        .setTitle(aboutTitle)
        .setCancelable(true)
        .setIcon(R.drawable.icon)
        .setPositiveButton(context.getString(android.R.string.ok), null)
        .setView(message)
        .create()
    }
  }
}
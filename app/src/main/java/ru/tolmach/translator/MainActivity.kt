package ru.tolmach.translator

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val engine: TranslationEngine get() = (application as TolmachApp).engine
    private val history: HistoryStore get() = (application as TolmachApp).history

    private lateinit var root: View
    private lateinit var crumbs: BreadcrumbsView
    private lateinit var titleTop: TextView
    private lateinit var titleBottom: TextView
    private lateinit var editTop: EditText
    private lateinit var editBottom: EditText
    private lateinit var status: TextView
    private lateinit var headerTop: View
    private lateinit var headerBottom: View
    private lateinit var themeTop: ImageButton
    private lateinit var themeBottom: ImageButton
    private lateinit var copyTop: ImageButton
    private lateinit var copyBottom: ImageButton
    private lateinit var pasteTop: ImageButton
    private lateinit var pasteBottom: ImageButton
    private lateinit var earTop: ImageButton
    private lateinit var earBottom: ImageButton
    private lateinit var fontDownTop: TextView
    private lateinit var fontDownBottom: TextView
    private lateinit var fontUpTop: TextView
    private lateinit var fontUpBottom: TextView

    /** РЇР·С‹Рє РІРµСЂС…РЅРµР№ РїРѕР»РѕРІРёРЅС‹. РќРёР¶РЅСЏСЏ РІСЃРµРіРґР° РїСЂРѕС‚РёРІРѕРїРѕР»РѕР¶РЅР°СЏ. */
    private var topLang = Lang.RU

    /** РџРѕРєР° true, РёР·РјРµРЅРµРЅРёСЏ С‚РµРєСЃС‚Р° СЃРґРµР»Р°РЅС‹ РїСЂРѕРіСЂР°РјРјРѕР№ Рё РЅРµ РґРѕР»Р¶РЅС‹ Р·Р°РїСѓСЃРєР°С‚СЊ РїРµСЂРµРІРѕРґ. */
    private var mute = false

    private var pendingFromTop = true
    private var translateJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val debounce = Runnable { translate(pendingFromTop) }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakingSide = SIDE_NONE
    private var currentUtterance: String? = null

    private val historyLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { applyHistoryEntry(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = (application as TolmachApp).prefs
        setContentView(R.layout.activity_main)

        bindViews()
        applyWindowInsets()
        applyStatusBarIcons()
        wireListeners()
        applyFontSize()
        restoreState(savedInstanceState)
        applyLabels()
        initBreadcrumbs()
        initTts()
        updateSpeakButtons()

        if (!(application as TolmachApp).modelsChecked) {
            warmUpModels()
        }
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        crumbs = findViewById(R.id.crumbs)
        titleTop = findViewById(R.id.titleTop)
        titleBottom = findViewById(R.id.titleBottom)
        editTop = findViewById(R.id.editTop)
        editBottom = findViewById(R.id.editBottom)
        status = findViewById(R.id.status)
        headerTop = findViewById(R.id.headerTop)
        headerBottom = findViewById(R.id.headerBottom)
        themeTop = findViewById(R.id.themeTop)
        themeBottom = findViewById(R.id.themeBottom)
        copyTop = findViewById(R.id.copyTop)
        copyBottom = findViewById(R.id.copyBottom)
        pasteTop = findViewById(R.id.pasteTop)
        pasteBottom = findViewById(R.id.pasteBottom)
        earTop = findViewById(R.id.earTop)
        earBottom = findViewById(R.id.earBottom)
        fontDownTop = findViewById(R.id.fontDownTop)
        fontDownBottom = findViewById(R.id.fontDownBottom)
        fontUpTop = findViewById(R.id.fontUpTop)
        fontUpBottom = findViewById(R.id.fontUpBottom)
    }

    private fun initBreadcrumbs() {
        crumbs.setItems(
            listOf(
                BreadcrumbsView.Item(getString(R.string.crumb_home), current = true),
                BreadcrumbsView.Item(getString(R.string.crumb_history)) {
                    historyLauncher.launch(Intent(this, HistoryActivity::class.java))
                },
                BreadcrumbsView.Item(getString(R.string.crumb_help)) {
                    startActivity(Intent(this, HelpActivity::class.java))
                }
            )
        )
    }

    private fun wireListeners() {
        headerTop.setOnClickListener { swapPanes() }
        headerBottom.setOnClickListener { swapPanes() }

        themeTop.setOnClickListener { toggleTheme() }
        themeBottom.setOnClickListener { toggleTheme() }

        copyTop.setOnClickListener { copyPane(true) }
        copyBottom.setOnClickListener { copyPane(false) }

        pasteTop.setOnClickListener { pasteInto(true) }
        pasteBottom.setOnClickListener { pasteInto(false) }

        earTop.setOnClickListener { toggleSpeak(true) }
        earBottom.setOnClickListener { toggleSpeak(false) }

        fontDownTop.setOnClickListener { changeFontSize(-Prefs.STEP_FONT) }
        fontDownBottom.setOnClickListener { changeFontSize(-Prefs.STEP_FONT) }
        fontUpTop.setOnClickListener { changeFontSize(Prefs.STEP_FONT) }
        fontUpBottom.setOnClickListener { changeFontSize(Prefs.STEP_FONT) }

        editTop.addTextChangedListener(SimpleWatcher { onTextChanged(true) })
        editBottom.addTextChangedListener(SimpleWatcher { onTextChanged(false) })
    }

    // ------------------------------------------------------------------ РІРІРѕРґ

    private fun onTextChanged(fromTop: Boolean) {
        if (mute) return
        if ((speakingSide == SIDE_TOP && fromTop) || (speakingSide == SIDE_BOTTOM && !fromTop)) {
            stopSpeaking()
        }
        pendingFromTop = fromTop
        handler.removeCallbacks(debounce)
        handler.postDelayed(debounce, DEBOUNCE_MS)
    }

    private fun translate(fromTop: Boolean) {
        val src = if (fromTop) editTop else editBottom
        val dst = if (fromTop) editBottom else editTop
        val text = src.text.toString()

        translateJob?.cancel()

        if (text.isBlank()) {
            setTextSilently(dst, "")
            showStatus(getString(R.string.status_idle))
            return
        }

        val from = langOf(fromTop)
        val to = from.other()

        translateJob = lifecycleScope.launch {
            showStatus(getString(R.string.status_translating))
            try {
                val result = engine.translate(from, to, text)
                if (text != src.text.toString()) return@launch
                setTextSilently(dst, result)
history.record(from, to, text, result)
                showStatus(getString(R.string.status_ready_offline))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showStatus(errorText(e))
            }
        }
    }

    private fun setTextSilently(target: EditText, value: String) {
        if (target.text.toString() == value) return
        mute = true
        target.setText(value)
        target.setSelection(target.text.length)
        mute = false
    }

    private fun langOf(fromTop: Boolean): Lang = if (fromTop) topLang else topLang.other()

    // --------------------------------------------------------------- Р·Р°РіРѕР»РѕРІРєРё

    private fun applyLabels() {
        titleTop.text = if (topLang == Lang.RU) getString(R.string.lang_russian) else getString(R.string.lang_english)
        titleBottom.text = if (topLang == Lang.RU) getString(R.string.lang_english) else getString(R.string.lang_russian)
    }

    private fun swapPanes() {
        translateJob?.cancel()
        handler.removeCallbacks(debounce)
        stopSpeaking()

        mute = true
        editTop.setText("")
        editBottom.setText("")
        mute = false

        topLang = topLang.other()
        applyLabels()
        showStatus(getString(R.string.status_idle))
    }

    // ------------------------------------------------------------- Р±СѓС„РµСЂ РѕР±РјРµРЅР°

    private fun copyPane(fromTop: Boolean) {
        val text = (if (fromTop) editTop else editBottom).text.toString()
        if (text.isBlank()) {
            toast(getString(R.string.nothing_to_copy))
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.app_name), text))
        toast(getString(R.string.copied))
    }

    private fun pasteInto(fromTop: Boolean) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        } else {
            ""
        }
        if (text.isEmpty()) {
            toast(getString(R.string.paste_empty))
            return
        }
        val edit = if (fromTop) editTop else editBottom
        val start = min(edit.selectionStart, edit.selectionEnd).coerceAtLeast(0)
        val end = max(edit.selectionStart, edit.selectionEnd).coerceIn(start, edit.text.length)
        val from = start.coerceIn(0, edit.text.length)
        edit.text.replace(from, end, text)
        edit.setSelection(min(edit.text.length, from + text.length))
    }

    // ---------------------------------------------------------------- РѕР·РІСѓС‡РєР°

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val engine = tts
            if (!ttsReady || engine == null) {
                tts = null
                return@TextToSpeech
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    handler.post { finishSpeaking(utteranceId) }
                }

                override fun onError(utteranceId: String?) {
                    handler.post { finishSpeaking(utteranceId) }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    handler.post { finishSpeaking(utteranceId) }
                }
            })
        }
    }

    private fun toggleSpeak(fromTop: Boolean) {
        val side = if (fromTop) SIDE_TOP else SIDE_BOTTOM
        if (speakingSide == side) {
            stopSpeaking()
            return
        }

        val engine = tts
        if (engine == null || !ttsReady) {
            toast(getString(R.string.tts_unavailable))
            return
        }

        val text = (if (fromTop) editTop else editBottom).text.toString().trim()
        if (text.isEmpty()) {
            toast(getString(R.string.nothing_to_say))
            return
        }

        val locale = if (langOf(fromTop) == Lang.RU) Locale("ru", "RU") else Locale.ENGLISH
        val available = engine.isLanguageAvailable(locale)
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast(getString(R.string.tts_no_language))
            return
        }

        engine.stop()
        val limit = TextToSpeech.getMaxSpeechInputLength()
        val spoken = if (text.length > limit) text.substring(0, limit) else text

        val id = "tolmach-" + System.currentTimeMillis()
        currentUtterance = id
        speakingSide = side
        updateSpeakButtons()

        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
        val result = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) {
            finishSpeaking(id)
        }
    }

    private fun stopSpeaking() {
        tts?.stop()
        currentUtterance = null
        speakingSide = SIDE_NONE
        updateSpeakButtons()
    }

    private fun finishSpeaking(utteranceId: String?) {
        if (utteranceId != null && utteranceId != currentUtterance) return
        currentUtterance = null
        speakingSide = SIDE_NONE
        updateSpeakButtons()
    }

    private fun updateSpeakButtons() {
        val active = attrColor(com.google.android.material.R.attr.colorPrimary)
        val idle = attrColor(android.R.attr.textColorPrimary)
        ImageViewCompat.setImageTintList(
            earTop,
            ColorStateList.valueOf(if (speakingSide == SIDE_TOP) active else idle)
        )
        ImageViewCompat.setImageTintList(
            earBottom,
            ColorStateList.valueOf(if (speakingSide == SIDE_BOTTOM) active else idle)
        )
        earTop.contentDescription =
            getString(if (speakingSide == SIDE_TOP) R.string.cd_stop_speak else R.string.cd_speak)
        earBottom.contentDescription =
            getString(if (speakingSide == SIDE_BOTTOM) R.string.cd_stop_speak else R.string.cd_speak)
    }

    // ------------------------------------------------------------------- С‚РµРјР°

    private fun toggleTheme() {
        stopSpeaking()
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val next = if (night) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.nightMode = next
        AppCompatDelegate.setDefaultNightMode(next)
    }

    private fun applyStatusBarIcons() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night
    }

    // ------------------------------------------------------------------ С€СЂРёС„С‚

    private fun applyFontSize() {
        val size = prefs.fontSize
        editTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        editBottom.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)

        val titleSize = max(Prefs.MIN_FONT, size - 2f)
        titleTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
        titleBottom.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSize)
    }

    private fun changeFontSize(delta: Float) {
        prefs.fontSize = prefs.fontSize + delta
        applyFontSize()
    }

    // ------------------------------------------------------------- РѕРєСЂСѓР¶РµРЅРёРµ

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val base = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                base + bars.left,
                base + bars.top,
                base + bars.right,
                base + max(bars.bottom, ime.bottom)
            )
            insets
        }
    }

    private fun warmUpModels() {
        val app = application as TolmachApp
        lifecycleScope.launch {
            showStatus(getString(R.string.status_preparing))
            try {
                engine.prepare(topLang, topLang.other())
                app.modelsChecked = true
                showStatus(getString(R.string.status_ready_offline))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                showStatus(errorText(e))
            }
        }
    }

    private fun errorText(e: Throwable): String {
        val details = e.message?.take(120)?.replace('\n', ' ')
        return if (details.isNullOrBlank()) {
            getString(R.string.status_engine_error)
        } else {
            "${getString(R.string.status_engine_error)} ($details)"
        }
    }

    private fun showStatus(text: String) {
        status.text = text
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun attrColor(attr: Int): Int {
        val typed = TypedValue()
        theme.resolveAttribute(attr, typed, true)
        return if (typed.resourceId != 0) ContextCompat.getColor(this, typed.resourceId) else typed.data
    }

    // ------------------------------------------------------------ РёСЃС‚РѕСЂРёСЏ

    private fun applyHistoryEntry(data: Intent) {
        val from = runCatching {
            Lang.valueOf(data.getStringExtra(HistoryActivity.EXTRA_FROM) ?: Lang.RU.name)
        }.getOrDefault(Lang.RU)
        val source = data.getStringExtra(HistoryActivity.EXTRA_SOURCE).orEmpty()
        val result = data.getStringExtra(HistoryActivity.EXTRA_RESULT).orEmpty()

        translateJob?.cancel()
        handler.removeCallbacks(debounce)
        stopSpeaking()

        mute = true
        topLang = from
        editTop.setText(source)
        editTop.setSelection(editTop.text.length)
        editBottom.setText(result)
        editBottom.setSelection(editBottom.text.length)
        mute = false

        applyLabels()
        showStatus(getString(R.string.status_from_history))
    }

    // ------------------------------------------------------------ СЃРѕСЃС‚РѕСЏРЅРёРµ

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TOP_TEXT, editTop.text.toString())
        outState.putString(KEY_BOTTOM_TEXT, editBottom.text.toString())
        outState.putString(KEY_TOP_LANG, topLang.name)
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) return
        topLang = runCatching { Lang.valueOf(state.getString(KEY_TOP_LANG) ?: Lang.RU.name) }
            .getOrDefault(Lang.RU)
        mute = true
        editTop.setText(state.getString(KEY_TOP_TEXT).orEmpty())
        editBottom.setText(state.getString(KEY_BOTTOM_TEXT).orEmpty())
        mute = false
    }

    override fun onStop() {
        stopSpeaking()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(debounce)
        translateJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private class SimpleWatcher(private val action: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = action()
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
        const val KEY_TOP_TEXT = "top_text"
        const val KEY_BOTTOM_TEXT = "bottom_text"
        const val KEY_TOP_LANG = "top_lang"

        const val SIDE_NONE = 0
        const val SIDE_TOP = 1
        const val SIDE_BOTTOM = 2
    }
}


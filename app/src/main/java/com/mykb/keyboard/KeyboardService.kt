package com.mykb.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

object VoiceBridge {
    @Volatile
    var pending: String? = null
}

class KeyboardService : InputMethodService() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    private lateinit var store: Brain
    private lateinit var sp: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var th: Th = Prefs.themes[0]

    // ---- settings (snapshot, reloaded when the settings screen changes something)
    private var heightPct = 100
    private var textPct = 100
    private var numRow = true
    private var haptic = true
    private var hapMs = 15
    private var sound = false
    private var preview = true
    private var suggestOn = true
    private var learnOn = true
    private var autoCap = true
    private var dblSpace = true
    private var autoSpace = true
    private var bnDigits = false
    private var builtStamp = -1L

    // ---- state
    private var mode = MODE_EN
    private var symPage = 0
    private var bnPage = 0
    private var shift = 0
    private var emojiCat = 0
    private var raw = ""
    private var prev = ""
    private var prev2 = ""
    private var plain = false
    private var lastSpace = 0L

    // ---- views
    private var root: LinearLayout? = null
    private var body: FrameLayout? = null
    private var sugRow: LinearLayout? = null
    private val letterKeys = ArrayList<KeyView>()
    private var shiftKeyView: KeyView? = null
    private var previewPopup: PopupWindow? = null
    private var altPopup: PopupWindow? = null
    private var repeatRunnable: Runnable? = null
    private val glyphPaint = Paint()
    private val glyphCache = HashMap<String, Boolean>()

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { readClip() }

    private val symHints = mapOf(
        "a" to "@", "s" to "#", "d" to "\$", "f" to "%", "g" to "&", "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";", "n" to "!", "m" to "?"
    )
    private val digitHints = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5", "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0"
    )

    private val emojiCats: List<Pair<String, String>> = listOf(
        "😀" to "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 👽 👾 🤖 🎃 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
        "👋" to "👋 🤚 ✋ 🖖 👌 🤌 🤏 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 💅 🤳 💪 🦾 👀 👅 👄 👶 🧒 👦 👧 🧑 👨 👩 🧔 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 💃 🕺 👯 🚶 🏃 👪 💑 💏",
        "🐱" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🦂 🐢 🐍 🦎 🦖 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🐫 🦒 🦘 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🐇 🐁 🐀 🌵 🎄 🌲 🌳 🌴 🌱 🌿 🍀 🍃 🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌙 🌎 🌍 🌏 💫 ⭐ 🌟 ✨ ⚡ 🔥 💥 🌈 ⛄ 🌊 💧 💦 ☔",
        "🍔" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🥔 🍠 🍞 🥐 🥖 🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥙 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾 🥄 🍴 🍽",
        "⚽" to "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏏 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 🎿 ⛸ 🏂 🏋 🤸 🤼 🤺 🏊 🚴 🚵 🏇 🧘 🎮 🕹 🎯 🎲 🧩 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🏆 🥇 🥈 🥉 🏅 🎖 🎉 🎊 🎁 🎈 🎀 🎆 🎇",
        "🚗" to "🚗 🚕 🚙 🚌 🚎 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 🚁 🛩 🛫 🛬 🚀 🛸 🚢 ⛵ 🛶 ⚓ 🏠 🏡 🏢 🏣 🏥 🏦 🏨 🏪 🏫 🏭 🏯 🏰 💒 🗼 🗽 ⛪ 🕌 🕍 ⛲ ⛺ 🌁 🌃 🌆 🌇 🌉 🌋 🗻 🏖 🏝 🏜 ⛰",
        "💡" to "📱 💻 🖥 📷 📹 🎥 📺 📻 🔋 🔌 💡 🔦 💰 💳 💵 💴 💶 💷 💸 🛒 📦 📫 📧 📝 📖 📚 📰 📅 📆 📌 📍 📎 🔒 🔓 🔑 🔨 🔧 🔩 🧰 🧲 💣 🔪 🔮 💊 💉 🩺 🧬 🔬 🔭 📡 💾 💽 📀 ⌚ ⏰ ⌛ ⏳ 🔔 📢 🔍 🔎 🧹 🧺 🧻 🧼 🧽 🚪 🛏 🛋 🚿 🛁 🚽",
        "\u2764\uFE0F" to "\u2764\uFE0F 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💞 💓 💗 💖 💘 💝 💟 ✅ ❌ ❓ ❗ ⭕ 🚫 💯 ➕ ➖ ➗ 💤 💢 💬 💭 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔶 🔷 🔸 🔹 🆗 🆒 🆕 🆓 🔥 ✨ 🎵 🎶",
        "🏁" to "🇧🇩 🇮🇳 🇵🇰 🇳🇵 🇱🇰 🇧🇹 🇲🇻 🇺🇸 🇬🇧 🇨🇦 🇦🇺 🇸🇦 🇦🇪 🇶🇦 🇰🇼 🇴🇲 🇧🇭 🇲🇾 🇸🇬 🇮🇩 🇯🇵 🇨🇳 🇰🇷 🇹🇷 🇮🇷 🇮🇶 🇪🇬 🇿🇦 🇳🇬 🇩🇪 🇫🇷 🇮🇹 🇪🇸 🇷🇺 🇧🇷 🇦🇷 🇲🇽 🇺🇳 🏁 🚩"
    )

    private fun dpf(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun rowPx(): Int = dpf(50f * heightPct / 100f)

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        store = Store.get(this)
        sp = Prefs.sp(this)
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun loadSettings() {
        heightPct = sp.getInt(Prefs.HEIGHT, 100)
        textPct = sp.getInt(Prefs.TEXT, 100)
        numRow = sp.getBoolean(Prefs.NUMROW, true)
        haptic = sp.getBoolean(Prefs.HAPTIC, true)
        hapMs = sp.getInt(Prefs.HAPMS, 15)
        sound = sp.getBoolean(Prefs.SOUND, false)
        preview = sp.getBoolean(Prefs.PREVIEW, true)
        suggestOn = sp.getBoolean(Prefs.SUGGEST, true)
        learnOn = sp.getBoolean(Prefs.LEARN, true)
        autoCap = sp.getBoolean(Prefs.AUTOCAP, true)
        dblSpace = sp.getBoolean(Prefs.DBLSPACE, true)
        autoSpace = sp.getBoolean(Prefs.AUTOSPACE, true)
        bnDigits = sp.getBoolean(Prefs.BNDIGITS, false)
        th = Prefs.theme(this)
    }

    override fun onCreateInputView(): View {
        loadSettings()
        return buildRoot()
    }

    private fun buildRoot(): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.setBackgroundColor(th.bg)
        r.setPadding(0, 0, 0, dpf(4f))
        r.addView(toolbar())

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.layoutParams = LinearLayout.LayoutParams(MATCH, dpf(40f))
        val sr = LinearLayout(this)
        sr.orientation = LinearLayout.HORIZONTAL
        sr.gravity = Gravity.CENTER_VERTICAL
        hs.addView(sr, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MATCH))
        r.addView(hs)
        sugRow = sr

        val b = FrameLayout(this)
        b.layoutParams = LinearLayout.LayoutParams(MATCH, rowPx() * 5)
        r.addView(b)
        body = b
        root = r
        builtStamp = sp.getLong(Prefs.STAMP, 0)
        return r
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        commitPendingVoice()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        raw = ""
        prev = ""
        prev2 = ""
        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val v = type and InputType.TYPE_MASK_VARIATION
        val pw = (cls == InputType.TYPE_CLASS_TEXT && (v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noPers = ((info?.imeOptions ?: 0) and 0x1000000) != 0
        val special = cls != InputType.TYPE_CLASS_TEXT || v == InputType.TYPE_TEXT_VARIATION_URI ||
            v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        plain = pw || noPers || special
        if (root != null && sp.getLong(Prefs.STAMP, 0) != builtStamp) {
            loadSettings()
            setInputView(buildRoot())
        }
        mode = if (plain) MODE_EN else sp.getInt(Prefs.MODE, MODE_EN)
        symPage = 0
        bnPage = 0
        shift = 0
        renderKeys()
        refreshSuggestions()
        updateAutoCap()
        commitPendingVoice()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        hideAllPopups()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        raw = ""
        currentInputConnection?.finishComposingText()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (raw.isNotEmpty() && candidatesEnd >= 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            raw = ""
            currentInputConnection?.finishComposingText()
            refreshSuggestions()
        }
    }

    // ------------------------------------------------------------------ text logic

    private fun display(): String = if (mode == MODE_AVRO) Avro.convert(raw) else raw

    private fun updateComposing() {
        currentInputConnection?.setComposingText(display(), 1)
        refreshSuggestions()
    }

    private fun pushPrev(w: String) {
        prev2 = prev
        prev = w
    }

    private fun learnWord(w: String, r: String) {
        if (plain || !learnOn || w.isEmpty()) return
        if (w.none { it.isLetter() }) return
        if (w.length < 2 && w[0].code < 0x80) return
        store.learn(mode, prev2, prev, r, w)
    }

    private fun finishWord() {
        if (raw.isEmpty()) return
        var w = display()
        if (mode == MODE_EN && (w == "i" || w.startsWith("i'"))) w = "I" + w.substring(1)
        currentInputConnection?.commitText(w, 1)
        learnWord(w, raw)
        pushPrev(w)
        raw = ""
    }

    private fun pickSuggestion(w: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(if (autoSpace) "$w " else w, 1)
        if (raw.isNotEmpty()) learnWord(w, raw) else if (!plain && learnOn) store.learn(mode, prev2, prev, "", w)
        pushPrev(w)
        raw = ""
        refreshSuggestions()
        if (autoSpace) updateAutoCap()
    }

    /** word = true: part of a word (goes into the composing buffer). */
    private fun onText(s: String, word: Boolean) {
        val ic = currentInputConnection ?: return
        if (word && !plain) {
            raw += s
            updateComposing()
            return
        }
        if (word) {
            ic.commitText(s, 1)
            return
        }
        if (s == "'" && mode == MODE_EN && raw.isNotEmpty()) {
            raw += s
            updateComposing()
            return
        }
        finishWord()
        ic.commitText(s, 1)
        if (s == "." || s == "!" || s == "?" || s == "।") {
            prev = ""
            prev2 = ""
        }
        refreshSuggestions()
    }

    /** Insert a long-press alternative. Bangla marks attach to the word being typed. */
    private fun commitAlt(s: String) {
        if (raw.isNotEmpty() && (s == "ঁ" || s == "ঃ" || s == "ৎ")) {
            if (mode == MODE_AVRO) {
                raw += when (s) {
                    "ঁ" -> "^"
                    "ঃ" -> ":"
                    else -> "t``"
                }
                updateComposing()
                return
            }
            if (mode == MODE_BN) {
                onText(s, true)
                return
            }
        }
        onText(s, false)
    }

    private fun onSpace() {
        val ic = currentInputConnection ?: return
        if (raw.isEmpty() && dblSpace && !plain) {
            val now = System.currentTimeMillis()
            if (now - lastSpace < 450) {
                val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
                if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(if (mode == MODE_EN) ". " else "। ", 1)
                    lastSpace = 0
                    prev = ""
                    prev2 = ""
                    refreshSuggestions()
                    updateAutoCap()
                    return
                }
            }
        }
        finishWord()
        ic.commitText(" ", 1)
        lastSpace = System.currentTimeMillis()
        refreshSuggestions()
        updateAutoCap()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (raw.isNotEmpty()) {
            raw = raw.dropLast(1)
            if (raw.isEmpty()) ic.commitText("", 1) else ic.setComposingText(display(), 1)
            refreshSuggestions()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun enter() {
        finishWord()
        val info = currentInputEditorInfo
        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noAction = (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        prev = ""
        prev2 = ""
        refreshSuggestions()
        updateAutoCap()
    }

    private fun updateAutoCap() {
        if (!autoCap || plain || mode != MODE_EN || shift == 2) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val want = if (ic.getCursorCapsMode(info.inputType) != 0) 1 else 0
        if (shift != want) {
            shift = want
            updateShiftUi()
        }
    }

    private fun typeLetter(base: String) {
        val s = if (shift > 0) base.uppercase() else base
        if (shift == 1) {
            shift = 0
            updateShiftUi()
        }
        onText(s, true)
    }

    private fun setMode(m: Int) {
        if (plain) return
        finishWord()
        mode = m
        sp.edit().putInt(Prefs.MODE, mode).apply()
        symPage = 0
        bnPage = 0
        shift = 0
        renderKeys()
        refreshSuggestions()
        updateAutoCap()
    }

    private fun cycleMode() = setMode((mode + 1) % 3)

    private fun readClip() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            if (clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            val t = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            store.addClip(t)
        } catch (e: Exception) {
        }
    }

    private fun startVoice() {
        finishWord()
        try {
            val i = Intent(this, VoiceActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra("lang", if (mode == MODE_EN) "en-US" else "bn-BD")
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    private fun commitPendingVoice() {
        val t = VoiceBridge.pending ?: return
        val ic = currentInputConnection ?: return
        VoiceBridge.pending = null
        ic.commitText("$t ", 1)
    }

    private fun openSettings() {
        try {
            val i = Intent(this, SettingsActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------------ feedback / popups

    private fun feedback() {
        if (haptic) {
            try {
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createOneShot(hapMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vib.vibrate(hapMs.toLong())
                }
            } catch (e: Exception) {
            }
        }
        if (sound) {
            try {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
            } catch (e: Exception) {
            }
        }
    }

    private fun hidePreview() {
        try {
            previewPopup?.dismiss()
        } catch (e: Exception) {
        }
        previewPopup = null
    }

    private fun hideAllPopups() {
        hidePreview()
        try {
            altPopup?.dismiss()
        } catch (e: Exception) {
        }
        altPopup = null
    }

    private fun showPreview(v: View, text: String) {
        if (!preview) return
        try {
            hidePreview()
            val tv = TextView(this)
            tv.text = text
            tv.gravity = Gravity.CENTER
            tv.textSize = 28f
            tv.setTextColor(th.text)
            val g = GradientDrawable()
            g.setColor(th.key)
            g.cornerRadius = dpf(10f).toFloat()
            g.setStroke(dpf(1f), th.pressed)
            tv.background = g
            val w = dpf(58f)
            val h = dpf(66f)
            val pw = PopupWindow(tv, w, h)
            pw.isTouchable = false
            pw.isClippingEnabled = false
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            pw.showAtLocation(v, Gravity.NO_GRAVITY, loc[0] + v.width / 2 - w / 2, loc[1] - h - dpf(4f))
            previewPopup = pw
        } catch (e: Exception) {
        }
    }

    private fun showAlts(anchor: View, alts: List<String>, onPick: (String) -> Unit) {
        hideAllPopups()
        try {
            val lin = LinearLayout(this)
            lin.orientation = LinearLayout.HORIZONTAL
            val g = GradientDrawable()
            g.setColor(th.key)
            g.cornerRadius = dpf(10f).toFloat()
            g.setStroke(dpf(1f), th.pressed)
            lin.background = g
            lin.setPadding(dpf(4f), dpf(4f), dpf(4f), dpf(4f))
            val itemW = dpf(46f)
            val itemH = dpf(50f)
            for (a in alts) {
                val tv = TextView(this)
                tv.text = a
                tv.textSize = 20f
                tv.setTextColor(th.text)
                tv.gravity = Gravity.CENTER
                tv.layoutParams = LinearLayout.LayoutParams(itemW, itemH)
                tv.setOnClickListener {
                    hideAllPopups()
                    onPick(a)
                }
                lin.addView(tv)
            }
            val w = itemW * alts.size + dpf(8f)
            val h = itemH + dpf(8f)
            val pw = PopupWindow(lin, w, h)
            pw.isOutsideTouchable = true
            pw.isFocusable = false
            pw.isClippingEnabled = false
            pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val sw = resources.displayMetrics.widthPixels
            val x = (loc[0] + anchor.width / 2 - w / 2).coerceIn(0, maxOf(0, sw - w))
            pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, loc[1] - h - dpf(4f))
            altPopup = pw
        } catch (e: Exception) {
        }
    }

    private fun startRepeat(a: () -> Unit) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() {
                a()
                handler.postDelayed(this, 45)
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, 380)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // ------------------------------------------------------------------ key builder

    /** style: 0 normal, 1 control, 2 accent, 3 flat (toolbar) */
    private fun key(
        label: String,
        weight: Float = 1f,
        style: Int = 0,
        size: Float = 20f,
        hint: String = "",
        repeat: Boolean = false,
        previewOf: (() -> String)? = null,
        onLong: ((KeyView) -> Unit)? = null,
        action: () -> Unit
    ): KeyView {
        val v = KeyView(this)
        v.label = label
        v.hint = hint
        v.fill = when (style) {
            1 -> th.ctrl
            2 -> th.accent
            3 -> th.bg
            else -> th.key
        }
        v.fillPressed = th.pressed
        v.textColor = if (style == 2) Color.WHITE else th.text
        v.hintColor = th.dim
        val d = resources.displayMetrics.density
        v.textSizePx = size * d * textPct / 100f
        v.hintSizePx = 10f * d * textPct / 100f
        v.radius = 7f * d
        val lp = LinearLayout.LayoutParams(0, MATCH, weight)
        lp.setMargins(dpf(2.5f), dpf(3f), dpf(2.5f), dpf(3f))
        v.layoutParams = lp
        var longFired = false
        var cancelled = false
        var longR: Runnable? = null
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    longFired = false
                    cancelled = false
                    feedback()
                    if (previewOf != null) showPreview(v, previewOf())
                    if (onLong != null) {
                        val r = Runnable {
                            longFired = true
                            hidePreview()
                            onLong(v)
                        }
                        longR = r
                        handler.postDelayed(r, 350)
                    } else {
                        action()
                        if (repeat) startRepeat(action)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!cancelled && (e.x < 0 || e.y < 0 || e.x > view.width || e.y > view.height)) {
                        cancelled = true
                        longR?.let { handler.removeCallbacks(it) }
                        view.isPressed = false
                        hidePreview()
                        stopRepeat()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    hidePreview()
                    longR?.let { handler.removeCallbacks(it) }
                    stopRepeat()
                    if (onLong != null && !longFired && !cancelled) action()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    hidePreview()
                    longR?.let { handler.removeCallbacks(it) }
                    stopRepeat()
                }
            }
            true
        }
        return v
    }

    private fun row(views: List<View>, hPx: Int = rowPx()): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = LinearLayout.LayoutParams(MATCH, hPx)
        r.setPadding(dpf(2f), 0, dpf(2f), 0)
        for (v in views) r.addView(v)
        return r
    }

    private fun spacer(w: Float): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(0, MATCH, w)
        return v
    }

    private fun backKey(w: Float): KeyView = key("⌫", w, 1, 20f, repeat = true) { backspace() }

    private fun shiftLabel(): String = if (shift == 2) "⇪" else "⇧"
    private fun shiftStyle(): Int = if (shift == 0) 1 else 2

    private fun shiftKey(w: Float): KeyView {
        val k = key(shiftLabel(), w, shiftStyle(), 20f) {
            shift = (shift + 1) % 3
            updateShiftUi()
        }
        shiftKeyView = k
        return k
    }

    private fun updateShiftUi() {
        for (k in letterKeys) {
            val b = k.tag as String
            k.label = if (shift > 0) b.uppercase() else b
            k.invalidate()
        }
        val sk = shiftKeyView
        if (sk != null) {
            sk.label = shiftLabel()
            sk.fill = if (shift == 0) th.ctrl else th.accent
            sk.invalidate()
        }
    }

    private fun bnDigit(d: Char): String = ('০' + (d - '0')).toString()

    private fun modeLabel(): String = when (mode) {
        MODE_BN -> "বাং"
        MODE_AVRO -> "অভ্র"
        else -> "EN"
    }

    private fun spaceLabel(): String = when (mode) {
        MODE_BN -> "বাংলা"
        MODE_AVRO -> "অভ্র (Avro)"
        else -> "English"
    }

    // ------------------------------------------------------------------ top bars

    private fun toolbar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.layoutParams = LinearLayout.LayoutParams(MATCH, dpf(42f))
        bar.setPadding(dpf(4f), 0, dpf(4f), 0)
        bar.addView(key("😀", 1f, 3, 20f) { showEmoji() })
        bar.addView(key("📋", 1f, 3, 20f) { showClipboard() })
        bar.addView(key("🎤", 1f, 3, 20f) { startVoice() })
        bar.addView(key("⚙", 1f, 3, 20f) { openSettings() })
        bar.addView(spacer(3f))
        bar.addView(key("⌄", 1f, 3, 22f) { requestHideSelf(0) })
        return bar
    }

    private fun sugItem(text: String, primary: Boolean): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(if (primary) th.accent else th.text)
        tv.textSize = 18f * textPct / 100f
        if (primary) tv.setTypeface(null, Typeface.BOLD)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.setPadding(dpf(16f), 0, dpf(16f), 0)
        tv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MATCH)
        tv.isClickable = true
        tv.setOnClickListener { pickSuggestion(text) }
        return tv
    }

    private fun refreshSuggestions() {
        val rowv = sugRow ?: return
        rowv.removeAllViews()
        if (!suggestOn || plain) return
        val list = store.suggest(mode, raw, display(), prev2, prev, 7)
        val items = ArrayList<String>()
        if (raw.isNotEmpty()) items.add(display())
        for (w in list) if (!items.contains(w)) items.add(w)
        items.forEachIndexed { i, w ->
            if (i > 0) {
                val d = View(this)
                d.setBackgroundColor(th.pressed)
                val lp = LinearLayout.LayoutParams(dpf(1f), dpf(18f))
                d.layoutParams = lp
                rowv.addView(d)
            }
            rowv.addView(sugItem(w, i == 0 && raw.isNotEmpty()))
        }
        (rowv.parent as? HorizontalScrollView)?.scrollTo(0, 0)
    }

    // ------------------------------------------------------------------ keyboard pages

    private fun renderKeys() {
        val b = body ?: return
        hideAllPopups()
        b.removeAllViews()
        letterKeys.clear()
        shiftKeyView = null
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        val rows: List<View> = when {
            symPage > 0 -> symRows()
            mode == MODE_BN -> bnRows()
            else -> latinRows()
        }
        for (r in rows) col.addView(r)
        col.addView(bottomRow())
        setBodyRows(rows.size + 1)
        b.addView(col)
    }

    private fun setBodyRows(n: Int) {
        body?.layoutParams = LinearLayout.LayoutParams(MATCH, rowPx() * n)
    }

    private fun letterKey(base: String): KeyView {
        val alt = (if (!numRow) digitHints[base] else null) ?: symHints[base] ?: ""
        val k = key(
            if (shift > 0) base.uppercase() else base, 1f, 0, 21f, alt,
            previewOf = { if (shift > 0) base.uppercase() else base },
            onLong = if (alt.isNotEmpty()) { _ -> commitAlt(alt) } else null
        ) { typeLetter(base) }
        k.tag = base
        letterKeys.add(k)
        return k
    }

    private fun latinRows(): List<View> {
        val rows = ArrayList<View>()
        if (numRow) {
            rows.add(row("1234567890".map { d ->
                val t = if (mode == MODE_AVRO && bnDigits) bnDigit(d) else d.toString()
                key(t, 1f, 1, 17f) { onText(t, false) }
            }))
        }
        rows.add(row("qwertyuiop".map { letterKey(it.toString()) }))
        rows.add(row(listOf(spacer(0.5f)) + "asdfghjkl".map { letterKey(it.toString()) } + listOf(spacer(0.5f))))
        rows.add(row(listOf<View>(shiftKey(1.5f)) + "zxcvbnm".map { letterKey(it.toString()) } + listOf<View>(backKey(1.5f))))
        return rows
    }

    private fun symKey(s: String): KeyView = key(s, 1f, 0, 19f, previewOf = { s }) { onText(s, false) }

    private fun symRows(): List<View> {
        val rows = ArrayList<View>()
        rows.add(row("1234567890".map { d -> key(d.toString(), 1f, 1, 17f) { onText(d.toString(), false) } }))
        val b: String
        val c: String
        when (symPage) {
            1 -> {
                b = "@#\$%&-+()/"
                c = "*\"':;!?"
            }
            2 -> {
                b = "~`|•√π÷×¶∆"
                c = "£¢€¥৳^°={}"
            }
            else -> {
                b = "[]<>\\%©®™✓"
                c = "_…±¿¡«»≠≈"
            }
        }
        rows.add(row(b.map { symKey(it.toString()) }))
        val pageKey = key("$symPage/3", 1.4f, 1, 15f) {
            symPage = symPage % 3 + 1
            renderKeys()
        }
        rows.add(row(listOf<View>(pageKey) + c.map { symKey(it.toString()) } + listOf<View>(backKey(1.4f))))
        return rows
    }

    private fun bnChar(label: String, text: String = label, word: Boolean = true): KeyView =
        key(label, 1f, 0, 21f, previewOf = { label }) { onText(text, word) }

    private fun bnRows(): List<View> {
        val rows = ArrayList<View>()
        if (bnPage == 0) {
            for (line in listOf("কখগঘঙচছজঝঞ", "টঠডঢণতথদধন", "পফবভমযরলশষ")) {
                rows.add(row(line.map { bnChar(it.toString()) }))
            }
            val last = listOf("স", "হ", "\u09DC", "\u09DD", "\u09DF", "ৎ", "ং", "ঃ", "ঁ")
            rows.add(row(last.map { bnChar(it) } + listOf<View>(backKey(1.5f))))
        } else {
            rows.add(row(listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও", "ঔ").map { bnChar(it) }))
            val kars = listOf("া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ", "্")
            rows.add(row(kars.map { bnChar("\u25CC" + it, it, true) }))
            rows.add(row("০১২৩৪৫৬৭৮৯".map { bnChar(it.toString(), it.toString(), false) }))
            val last = listOf("ক্ষ", "জ্ঞ", "ত্র", "দ্র", "শ্র", "ক্র")
            rows.add(
                row(
                    last.map { bnChar(it) } + listOf("।", "?", "!").map { bnChar(it, it, false) } + listOf<View>(backKey(1.5f))
                )
            )
        }
        return rows
    }

    private fun bottomRow(): View {
        val v = ArrayList<View>()
        val left = if (symPage > 0) (if (mode == MODE_BN) "কখ" else "ABC") else "?123"
        v.add(key(left, 1.3f, 1, 15f) {
            symPage = if (symPage > 0) 0 else 1
            renderKeys()
        })
        if (mode == MODE_BN && symPage == 0) {
            v.add(key(if (bnPage == 0) "অ আ" else "ক খ", 1.2f, 1, 15f) {
                bnPage = 1 - bnPage
                renderKeys()
            })
        }
        v.add(key(modeLabel(), 1.2f, 1, 15f, onLong = { kv ->
            showAlts(kv, listOf("EN", "বাং", "অভ্র")) { pick ->
                setMode(
                    when (pick) {
                        "EN" -> MODE_EN
                        "বাং" -> MODE_BN
                        else -> MODE_AVRO
                    }
                )
            }
        }) { cycleMode() })
        v.add(key(",", 1f, 0, 20f) { onText(",", false) })
        v.add(key(spaceLabel(), 3.6f, 0, 14f, onLong = {
            try {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            } catch (e: Exception) {
            }
        }) { onSpace() })
        val dot = if (mode == MODE_EN) "." else "।"
        val dotAlts = if (mode == MODE_EN) listOf("!", "?", "'", "\"", ":", ";", "-") else listOf(".", "!", "?", "ঁ", "ঃ", "ৎ", "৳")
        v.add(key(dot, 1f, 0, 21f, onLong = { kv -> showAlts(kv, dotAlts) { commitAlt(it) } }) { onText(dot, false) })
        v.add(key("⏎", 1.4f, 2, 20f) { enter() })
        return row(v)
    }

    // ------------------------------------------------------------------ emoji

    private fun supported(e: String): Boolean = glyphCache.getOrPut(e) {
        try {
            glyphPaint.hasGlyph(e)
        } catch (x: Exception) {
            true
        }
    }

    private fun recentList(): List<String> = (sp.getString(Prefs.RECENT, "") ?: "").split(" ").filter { it.isNotBlank() }

    private fun addRecent(e: String) {
        val l = ArrayList(recentList())
        l.remove(e)
        l.add(0, e)
        sp.edit().putString(Prefs.RECENT, l.take(48).joinToString(" ")).apply()
    }

    private fun showEmoji() {
        val b = body ?: return
        finishWord()
        hideAllPopups()
        b.removeAllViews()
        setBodyRows(5)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.layoutParams = LinearLayout.LayoutParams(MATCH, dpf(46f))
        tabs.setPadding(dpf(2f), 0, dpf(2f), 0)
        tabs.addView(key("ABC", 1.3f, 1, 15f) { renderKeys() })
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.layoutParams = LinearLayout.LayoutParams(0, MATCH, 7f)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.HORIZONTAL
        val cats = listOf("🕒" to "") + emojiCats
        cats.forEachIndexed { i, c ->
            val k = key(c.first, 1f, if (i == emojiCat) 2 else 1, 18f, onLong = { }) {
                emojiCat = i
                showEmoji()
            }
            val lp = LinearLayout.LayoutParams(dpf(44f), MATCH)
            lp.setMargins(dpf(2.5f), dpf(3f), dpf(2.5f), dpf(3f))
            k.layoutParams = lp
            inner.addView(k)
        }
        hs.addView(inner)
        tabs.addView(hs)
        tabs.addView(backKey(1.3f))
        col.addView(tabs)

        val sv = ScrollView(this)
        sv.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val items = if (emojiCat == 0) recentList().filter { supported(it) }
        else emojiCats[emojiCat - 1].second.split(" ").filter { it.isNotBlank() && supported(it) }
        if (items.isEmpty()) {
            val hint = TextView(this)
            hint.text = "এখনো কোনো ইমোজি ব্যবহার হয়নি"
            hint.setTextColor(th.dim)
            hint.textSize = 14f
            hint.gravity = Gravity.CENTER
            hint.setPadding(dpf(16f), dpf(24f), dpf(16f), dpf(24f))
            list.addView(hint)
        }
        val cols = 8
        for (chunk in items.chunked(cols)) {
            val views = ArrayList<View>()
            for (e in chunk) {
                val tv = TextView(this)
                tv.text = e
                tv.textSize = 26f
                tv.gravity = Gravity.CENTER
                tv.layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
                tv.setOnClickListener {
                    finishWord()
                    currentInputConnection?.commitText(e, 1)
                    addRecent(e)
                    feedback()
                }
                views.add(tv)
            }
            for (k in chunk.size until cols) views.add(spacer(1f))
            list.addView(row(views, dpf(50f)))
        }
        sv.addView(list)
        col.addView(sv)
        b.addView(col)
    }

    // ------------------------------------------------------------------ clipboard

    private fun showClipboard() {
        val b = body ?: return
        finishWord()
        hideAllPopups()
        readClip()
        b.removeAllViews()
        setBodyRows(5)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.layoutParams = LinearLayout.LayoutParams(MATCH, dpf(46f))
        head.setPadding(dpf(2f), 0, dpf(2f), 0)
        head.addView(key("ABC", 1.3f, 1, 15f) { renderKeys() })
        val title = TextView(this)
        title.text = "ক্লিপবোর্ড"
        title.setTextColor(th.text)
        title.textSize = 16f
        title.gravity = Gravity.CENTER
        title.layoutParams = LinearLayout.LayoutParams(0, MATCH, 3f)
        head.addView(title)
        head.addView(key("🗑", 1.2f, 1, 17f) {
            store.clearClips()
            showClipboard()
        })
        head.addView(backKey(1.2f))
        col.addView(head)

        val sv = ScrollView(this)
        sv.layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val clips = store.getClips()
        if (clips.isEmpty()) {
            val hint = TextView(this)
            hint.text = "ক্লিপবোর্ড খালি। কিছু কপি করলে এখানে আসবে।"
            hint.setTextColor(th.dim)
            hint.textSize = 14f
            hint.gravity = Gravity.CENTER
            hint.setPadding(dpf(16f), dpf(24f), dpf(16f), dpf(24f))
            list.addView(hint)
        }
        for (c in clips) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dpf(6f), dpf(3f), dpf(6f), dpf(3f))
            r.layoutParams = lp

            val tv = TextView(this)
            tv.text = c.text
            tv.setTextColor(th.text)
            tv.textSize = 15f
            tv.maxLines = 2
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.minHeight = dpf(44f)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setPadding(dpf(10f), dpf(6f), dpf(10f), dpf(6f))
            val g = GradientDrawable()
            g.setColor(th.key)
            g.cornerRadius = dpf(8f).toFloat()
            tv.background = g
            tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tv.setOnClickListener {
                currentInputConnection?.commitText(c.text, 1)
                feedback()
            }
            r.addView(tv)

            val pin = TextView(this)
            pin.text = if (c.pinned) "★" else "☆"
            pin.setTextColor(if (c.pinned) Color.parseColor("#FFC107") else th.dim)
            pin.textSize = 20f
            pin.gravity = Gravity.CENTER
            pin.layoutParams = LinearLayout.LayoutParams(dpf(40f), dpf(44f))
            pin.setOnClickListener {
                store.togglePin(c.id)
                showClipboard()
            }
            r.addView(pin)

            val del = TextView(this)
            del.text = "✕"
            del.setTextColor(th.dim)
            del.textSize = 18f
            del.gravity = Gravity.CENTER
            del.layoutParams = LinearLayout.LayoutParams(dpf(40f), dpf(44f))
            del.setOnClickListener {
                store.deleteClip(c.id)
                showClipboard()
            }
            r.addView(del)
            list.addView(r)
        }
        sv.addView(list)
        col.addView(sv)
        b.addView(col)
    }
}

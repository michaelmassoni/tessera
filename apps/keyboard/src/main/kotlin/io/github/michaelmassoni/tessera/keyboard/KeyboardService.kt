package io.github.michaelmassoni.tessera.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.michaelmassoni.tessera.core.settings.TesseraAppTheme
import io.github.michaelmassoni.tessera.keyboard.engine.ActionKey
import io.github.michaelmassoni.tessera.keyboard.engine.Dictionary
import io.github.michaelmassoni.tessera.keyboard.engine.EnterAction
import io.github.michaelmassoni.tessera.keyboard.engine.Key
import io.github.michaelmassoni.tessera.keyboard.engine.Layout
import io.github.michaelmassoni.tessera.keyboard.engine.Shift
import io.github.michaelmassoni.tessera.keyboard.engine.ShiftState
import io.github.michaelmassoni.tessera.keyboard.engine.Slot
import io.github.michaelmassoni.tessera.keyboard.engine.Suggester
import io.github.michaelmassoni.tessera.keyboard.ui.KeyboardActions
import io.github.michaelmassoni.tessera.keyboard.ui.KeyboardState
import io.github.michaelmassoni.tessera.keyboard.ui.KeyboardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The input method. Compose needs a lifecycle and saved-state owner on the view tree, which an
 * InputMethodService doesn't provide, so the service provides them itself.
 */
class KeyboardService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val shift = Shift()
    private var state by mutableStateOf(KeyboardState())
    private val prefs by lazy { KeyboardPrefs(this) }

    @Volatile private var suggester: Suggester? = null

    @Volatile private var learned: Set<String> = emptySet()

    /** What the current field allows; set in [onStartInputView]. */
    private var suggestionsOn = false
    private var autocorrectOn = false
    private var learnOn = false

    /** The last autocorrection, which delete (or its Undo slot) reverts while it's still just before the cursor. */
    private var lastCorrection: Correction? = null

    /** A picked suggestion added a space; punctuation typed next replaces it ("word."). */
    private var pickedSpace = false

    /** The strip most recently computed, and the word it was for. */
    private var strip: Pair<String, List<Slot?>>? = null
    private var stripJob: Job? = null

    private data class Correction(
        val original: String,
        val corrected: String,
        val separator: String,
    )

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        learned = prefs.learned
        lifecycleScope.launch(Dispatchers.IO) {
            val dictionary = runCatching { assets.open("en.dict").bufferedReader().useLines { Dictionary.parse(it) } }.getOrNull()
            if (dictionary != null) suggester = Suggester(dictionary, learned = { learned }, blockOffensive = { prefs.blockOffensive })
        }
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
            // The keyboard window extends behind the navigation area. In gesture navigation the
            // system draws its own keyboard bar (hide, switch keyboard) there, so the keys must
            // sit above whichever is taller: the navigation bar or that tappable bar.
            window.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
            setContent { TesseraAppTheme { KeyboardView(state, Actions()) } }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onStartInputView(
        info: EditorInfo,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val words =
            cls == InputType.TYPE_CLASS_TEXT && variation !in SENSITIVE && info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0
        suggestionsOn = words && prefs.suggestions
        autocorrectOn = words && prefs.autocorrect && variation != InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        learnOn = info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING == 0
        lastCorrection = null
        pickedSpace = false
        strip = null
        learned = prefs.learned
        val layout =
            when (cls) {
                InputType.TYPE_CLASS_PHONE -> Layout.Phone
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layout.Numbers
                else -> Layout.Letters
            }
        val decimal =
            cls == InputType.TYPE_CLASS_NUMBER &&
                info.inputType and (InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED) != 0
        state =
            KeyboardState(
                layout = layout,
                decimal = decimal,
                numberRow = prefs.numberRow,
                enter = ActionKey.of(info.imeOptions, info.inputType),
                haptics = prefs.haptics,
            )
        if (shift.state != ShiftState.Locked) updateCaps()
        refreshSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateCaps()
        refreshSuggestions()
    }

    /**
     * Auto-capitalisation. The field's own caps flags always apply; with the setting on, sentence
     * starts are capitalised in ordinary text fields too, because many apps don't ask for it.
     * Never in passwords, emails or URLs.
     */
    private fun updateCaps() {
        val info = currentInputEditorInfo ?: return
        val type = info.inputType
        val textField = type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val sensitive = type and InputType.TYPE_MASK_VARIATION in SENSITIVE
        var modes =
            type and
                (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        if (prefs.autoCaps && textField) modes = modes or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val caps = textField && !sensitive && modes != 0 && (currentInputConnection?.getCursorCapsMode(modes) ?: 0) != 0
        shift.auto(caps)
        state = state.copy(shift = shift.state, accents = null)
    }

    /**
     * The word just before the cursor, if the cursor is at the end of a word that stands on its
     * own (not inside a word, an email address, a URL or a number).
     */
    private fun currentWord(): String? {
        val ic = currentInputConnection ?: return null
        if (!ic.getSelectedText(0).isNullOrEmpty()) return null
        val after = ic.getTextAfterCursor(1, 0)?.firstOrNull()
        if (after != null && (after.isLetterOrDigit() || after in APOSTROPHES)) return null
        val before = ic.getTextBeforeCursor(MAX_WORD + 1, 0)?.toString() ?: return null
        val word = before.takeLastWhile { it.isLetter() || it in APOSTROPHES }.trimStart(*APOSTROPHES.toCharArray())
        if (word.isEmpty() || word.length > MAX_WORD) return null
        val preceding = before.getOrNull(before.length - word.length - 1)
        return if (preceding == null || preceding.isWhitespace() || preceding in OPENERS) word else null
    }

    /** Updates the strip for the word at the cursor, computing it off the main thread. */
    private fun refreshSuggestions() {
        val correction = lastCorrection
        if (correction != null && !stillAfterCorrection(correction)) lastCorrection = null
        if (!suggestionsOn) return showStrip(emptyList())
        val word = currentWord()
        lastCorrection?.let { if (word == null) return showStrip(listOf(null, Slot(it.original, Slot.Kind.Undo), null)) }
        val suggester = suggester
        if (word == null || suggester == null) return showStrip(emptyList())
        strip?.let { (forWord, slots) -> if (forWord == word) return showStrip(slots) }
        stripJob?.cancel()
        stripJob =
            lifecycleScope.launch {
                val slots = withContext(Dispatchers.Default) { suggester.strip(word) }
                strip = word to slots
                if (currentWord() == word) showStrip(slots)
            }
    }

    private fun showStrip(slots: List<Slot?>) {
        if (state.suggestions != slots) state = state.copy(suggestions = slots)
    }

    private fun stillAfterCorrection(c: Correction): Boolean {
        val text = c.corrected + c.separator
        return currentInputConnection?.getTextBeforeCursor(text.length, 0)?.toString() == text
    }

    /** Puts back what was typed before the last autocorrection, and learns it. */
    private fun revertCorrection(): Boolean {
        val c = lastCorrection ?: return false
        lastCorrection = null
        if (!stillAfterCorrection(c)) return false
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(c.corrected.length + c.separator.length, 0)
        ic.commitText(c.original + c.separator, 1)
        ic.endBatchEdit()
        learn(c.original)
        return true
    }

    private fun learn(word: String) {
        if (!learnOn) return
        learned = (learned + Dictionary.key(word)).let { if (it.size > MAX_LEARNED) it.drop(it.size - MAX_LEARNED).toSet() else it }
        prefs.learned = learned
    }

    /**
     * Ends the word at the cursor with [separator], autocorrecting it first if needed. The strip
     * already worked out the correction for this word unless the user typed very fast.
     */
    private fun commitSeparator(separator: String) {
        val ic = currentInputConnection ?: return
        val word = if (autocorrectOn) currentWord() else null
        val correction =
            word?.let { w ->
                val cached = strip?.takeIf { it.first == w }?.second
                if (cached != null) cached.firstOrNull { it?.kind == Slot.Kind.Correction }?.text else suggester?.autocorrection(w)
            }
        ic.beginBatchEdit()
        if (word != null && correction != null && correction != word) {
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(correction + separator, 1)
            lastCorrection = Correction(word, correction, separator)
        } else {
            ic.commitText(separator, 1)
        }
        ic.endBatchEdit()
    }

    private inner class Actions : KeyboardActions {
        override fun type(text: String) {
            val ic = currentInputConnection ?: return
            val upper = shift.state != ShiftState.Off && state.layout == Layout.Letters
            val out = if (upper) text.uppercase() else text
            lastCorrection = null
            val afterPick = pickedSpace
            pickedSpace = false
            // Double space types ". " after a word, like other keyboards.
            if (out == " " && prefs.doubleSpacePeriod) {
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    afterTyping()
                    return
                }
            }
            // Punctuation straight after a picked suggestion goes where its space was.
            if (afterPick && out in PUNCTUATION && ic.getTextBeforeCursor(1, 0)?.toString() == " ") ic.deleteSurroundingText(1, 0)
            if (out == " " || out in PUNCTUATION) commitSeparator(out) else ic.commitText(out, 1)
            afterTyping()
        }

        private fun afterTyping() {
            shift.typed()
            state = state.copy(shift = shift.state, accents = null)
            updateCaps()
        }

        override fun backspace() {
            val ic = currentInputConnection ?: return
            pickedSpace = false
            if (revertCorrection()) return
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        override fun enter() {
            val ic = currentInputConnection ?: return
            val info = currentInputEditorInfo
            lastCorrection = null
            pickedSpace = false
            if (state.enter == EnterAction.Newline || info == null) {
                commitSeparator("\n")
            } else {
                val id = info.imeOptions and EditorInfo.IME_MASK_ACTION
                ic.performEditorAction(
                    if (id == EditorInfo.IME_ACTION_UNSPECIFIED ||
                        id == EditorInfo.IME_ACTION_NONE
                    ) {
                        EditorInfo.IME_ACTION_DONE
                    } else {
                        id
                    },
                )
            }
        }

        override fun pick(slot: Slot) {
            if (slot.kind == Slot.Kind.Undo) {
                revertCorrection()
                return
            }
            val ic = currentInputConnection ?: return
            val word = currentWord()
            lastCorrection = null
            ic.beginBatchEdit()
            if (word != null) ic.deleteSurroundingText(word.length, 0)
            ic.commitText(slot.text + " ", 1)
            ic.endBatchEdit()
            if (slot.kind == Slot.Kind.Literal) learn(slot.text)
            pickedSpace = true
            afterTyping()
        }

        override fun shift() {
            shift.tap(System.currentTimeMillis())
            state = state.copy(shift = shift.state)
        }

        override fun switchLayout(layout: Layout) {
            state = state.copy(layout = layout, accents = null)
        }

        override fun showAccents(
            key: Key.Char,
            options: List<String>,
        ) {
            state = state.copy(accents = options)
        }

        override fun dismissAccents() {
            state = state.copy(accents = null)
        }
    }

    private companion object {
        const val MAX_WORD = 32
        const val MAX_LEARNED = 2000
        const val APOSTROPHES = "'’"
        const val OPENERS = "\"“‘([{"
        val PUNCTUATION = setOf(".", ",", "!", "?", ";", ":")

        /** Field variations where words are never suggested, corrected or capitalised. */
        val SENSITIVE =
            setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
            )
    }
}

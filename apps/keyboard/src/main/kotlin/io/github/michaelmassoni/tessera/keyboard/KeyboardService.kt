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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.michaelmassoni.tessera.core.settings.TesseraAppTheme
import io.github.michaelmassoni.tessera.keyboard.engine.ActionKey
import io.github.michaelmassoni.tessera.keyboard.engine.EnterAction
import io.github.michaelmassoni.tessera.keyboard.engine.Key
import io.github.michaelmassoni.tessera.keyboard.engine.Layout
import io.github.michaelmassoni.tessera.keyboard.engine.Shift
import io.github.michaelmassoni.tessera.keyboard.engine.ShiftState
import io.github.michaelmassoni.tessera.keyboard.ui.KeyboardActions
import io.github.michaelmassoni.tessera.keyboard.ui.KeyboardState
import io.github.michaelmassoni.tessera.keyboard.ui.KeyboardView

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

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
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
        val prefs = KeyboardPrefs(this)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
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
    }

    /** Auto-capitalisation where the field asks for it (never in passwords, emails or URLs). */
    private fun updateCaps() {
        val info = currentInputEditorInfo ?: return
        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        val textField = type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val noCaps =
            variation in
                setOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_URI,
                )
        val caps = textField && !noCaps && (currentInputConnection?.getCursorCapsMode(type) ?: 0) != 0
        shift.auto(caps)
        state = state.copy(shift = shift.state, accents = null)
    }

    private inner class Actions : KeyboardActions {
        override fun type(text: String) {
            val ic = currentInputConnection ?: return
            val upper = shift.state != ShiftState.Off && state.layout == Layout.Letters
            val out = if (upper) text.uppercase() else text
            // Double space types ". " after a word, like other keyboards.
            if (out == " " && KeyboardPrefs(this@KeyboardService).doubleSpacePeriod) {
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    afterTyping()
                    return
                }
            }
            ic.commitText(out, 1)
            afterTyping()
        }

        private fun afterTyping() {
            shift.typed()
            state = state.copy(shift = shift.state, accents = null)
            updateCaps()
        }

        override fun backspace() {
            val ic = currentInputConnection ?: return
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        override fun enter() {
            val ic = currentInputConnection ?: return
            val info = currentInputEditorInfo
            if (state.enter == EnterAction.Newline || info == null) {
                ic.commitText("\n", 1)
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
}

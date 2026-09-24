package app.andrewliang.patches.line.hideagenti

import app.andrewliang.patches.line.shared.ServerFlagFingerprint
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * The Compose lambda of the Home tab header (`wm2.j` in 26.14.0). It draws the header icons:
 * Agent i, the services grid, the bell and Settings. It draws Agent i only if its first boolean
 * field is true. It reads that field with the first `iget-boolean`, then labels the icon with
 * `access_agenti` (0x7f15006c). Nothing else in the APK uses that label.
 */
internal object HomeHeaderAgentIFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        literal(0x7f15006c),
    ),
)

/**
 * The server flag for the Agent i entry at the end of the search bar (`g45.i2.b()` in 26.14.0).
 * The search bar shows `main_tab_ai_entry_icon_container` only if this flag is true.
 */
internal object SearchBarAiEntryFlagFingerprint :
    ServerFlagFingerprint("function.search.line_ai_entry.enabled")

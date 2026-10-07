package app.andrewliang.patches.line.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.Opcode

/**
 * The static initializer of LINE's main Settings list, `ka5/o2.<clinit>` in 26.14.0. It builds every
 * row of the list once and keeps them in a static `List`.
 *
 * Its class and its row types are obfuscated, but the "About LINE" row reads two enum constants
 * whose names R8 keeps: its row key `AboutLine` and its analytics event
 * `MORETAB_SETTINGS_ABOUTLINE`. No other method reads both. The `sget-object` opcode keeps out the
 * initializers of the two enums, which write these constants.
 */
internal object SettingsRowsFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        fieldAccess(name = "AboutLine", opcode = Opcode.SGET_OBJECT),
        fieldAccess(name = "MORETAB_SETTINGS_ABOUTLINE", opcode = Opcode.SGET_OBJECT),
    ),
)

package app.andrewliang.patches.line.hidegift

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import com.android.tools.smali.dexlib2.Opcode

/**
 * The constructor of the "+" attach-menu **LINE GIFT** tile. It reads the attach-item type
 * constant `GIFT` and loads the tile icon `chat_ui_plus_gift`. Other constructors read a `GIFT`
 * constant too, but only this one also loads that drawable.
 */
internal object GiftAttachButtonFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "GIFT", opcode = Opcode.SGET_OBJECT),
        literal({ resourceId("drawable", "chat_ui_plus_gift") }),
    ),
)

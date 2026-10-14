package app.andrewliang.patches.line.hidetransfer

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import com.android.tools.smali.dexlib2.Opcode

/**
 * The constructor of the "+" attach-menu **Transfer** tile (LINE Pay money transfer). It loads
 * the tile icon `chat_ui_attach_ic_pay_new_design`, then reads the attach-item type constant
 * `PAY`. Other constructors read a `PAY` constant too, but only this one also loads that
 * drawable.
 */
internal object TransferAttachButtonFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        literal({ resourceId("drawable", "chat_ui_attach_ic_pay_new_design") }),
        fieldAccess(name = "PAY", opcode = Opcode.SGET_OBJECT),
    ),
)

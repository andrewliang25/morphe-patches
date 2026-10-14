package app.andrewliang.patches.line.hidepremiumunsend

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * `UnsendMessageLdsDialog.onViewCreated` — the unsend-confirmation dialog. For the `UnsendSilently`
 * request variant (`…$a$c`) it reveals a premium "Unsend discreetly" icon + label (string
 * `chat_premiumunsend_popupbutton_unsenddiscreetly`). Matching the guarding `instance-of` (the
 * only one before that label) and forcing it false hides both, leaving the ordinary
 * "Unsend / Close" dialog intact — its action buttons are separate views.
 *
 * That string is globally unique. `opcode(INSTANCE_OF)` avoids pinning the drift-prone
 * inner-class name `…$a$c`.
 */
internal object UnsendDiscreetlyButtonFingerprint : Fingerprint(
    definingClass =
        "Lcom/linecorp/line/chat/ui/impl/message/contextmenu/UnsendMessageLdsDialog;",
    name = "onViewCreated",
    filters = listOf(
        opcode(Opcode.INSTANCE_OF),
        literal({ resourceId("string", "chat_premiumunsend_popupbutton_unsenddiscreetly") }),
    ),
)

/**
 * Locates the "How to unsend discreetly" promo view by its globally-unique link string
 * `chat_premiumunsend_link_learnmoreaboutfeature`. Its constructor builds the promo link only when
 * a chat-support check returns `SUPPORTED_CHAT`, and the check's first argument is the premium
 * flag. Forcing that argument false leaves the link handler null. The literal pins only the
 * obfuscated class. The patch then finds the call site by scanning the class.
 */
internal object UnsendPromoLinkFingerprint : Fingerprint(
    filters = listOf(
        literal({ resourceId("string", "chat_premiumunsend_link_learnmoreaboutfeature") }),
    ),
)

/**
 * The provider `a(Context, ?, ?, Z)` that decides whether the long-press menu shows an "Unsend"
 * item. Its age gate is `sentTime + window >= now`, where `window` is the PREMIUM window (~7 days)
 * for premium-eligible chats, else the FREE one (~1h). The two reads sit in one if/else:
 * `iget <premium>`, `goto`, `iget <free>`. That premium window is why the item survives up to
 * ~7 days and, when tapped, triggers the "Give yourself more time" upsell in
 * `UnsendLitePlanUpgradePopupFragment`. Past ~7 days the gate fails and no item is added.
 *
 * The readable enum member `PREMIUM_UNSEND_MESSAGE` and the signature pin the method. The first
 * plain `iget` is the premium-window read. The earlier field reads are `iget-boolean`.
 */
internal object UnsendMenuAgeGateFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("Landroid/content/Context;", "L", "L", "Z"),
    filters = listOf(
        opcode(Opcode.IGET),
        opcode(Opcode.GOTO, location = MatchAfterImmediately()),
        opcode(Opcode.IGET, location = MatchAfterImmediately()),
        fieldAccess(name = "PREMIUM_UNSEND_MESSAGE", opcode = Opcode.SGET_OBJECT),
    ),
)

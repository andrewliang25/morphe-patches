package app.andrewliang.patches.line.hidepremiumunsend

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * `UnsendMessageLdsDialog.onViewCreated` — the unsend-confirmation dialog. When the dialog's
 * request param is the `UnsendSilently` variant (`…$a$c`) it reveals a premium "Unsend discreetly"
 * icon + label (string id `0x7f150bff`). We match the guarding `instance-of` (the only one before
 * that label) and force it false so the discreet icon/label always hide — leaving the app's own
 * ordinary "Unsend / Close" dialog (its action buttons are separate views, untouched).
 *
 * The literal `0x7f150bff` is globally unique; `opcode(INSTANCE_OF)` avoids pinning the drift-prone
 * obfuscated inner-class name `…$a$c`.
 */
internal object UnsendDiscreetlyButtonFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.INSTANCE_OF),
        literal(0x7f150bff),
    ),
)

/**
 * Locates the class `wi1.j4` via the globally-unique "How to unsend discreetly" promo-link string
 * id (`0x7f150bf8`). The class's constructor builds that promo link only when
 * `ne1.k2.a(i1.W() && i1.X(), …) == SUPPORTED_CHAT`; the patch forces that first argument false so
 * the link handler is never created. The literal only pins the (obfuscated) class; the `k2.a` call
 * site is then found by scanning the class.
 */
internal object UnsendPromoLinkFingerprint : Fingerprint(
    filters = listOf(
        literal(0x7f150bf8),
    ),
)

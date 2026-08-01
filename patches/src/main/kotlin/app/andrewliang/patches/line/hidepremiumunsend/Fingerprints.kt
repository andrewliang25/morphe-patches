package app.andrewliang.patches.line.hidepremiumunsend

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
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

/**
 * `ne1.y0$y.a(...)` — the candidate predicate that decides whether the long-press message menu
 * shows an "Unsend" item. Its age gate is `sentTime + window >= now`, where `window` is the PREMIUM
 * window (`Lj51/a;->p:I`, ~7 days) for premium-eligible chats, else the FREE window
 * (`Lj51/a;->o:I`, ~1h). Using the premium window is why the item survives for messages up to ~7
 * days and, when tapped, triggers the "Give yourself more time" upsell in `oe1.c0.a`; past ~7 days
 * the gate fails and the item is never added.
 *
 * We match the premium-window read (`fieldAccess Lj51/a;->p`) as the instruction to rewrite, and
 * disambiguate to this method via the readable enum member `Lj51/c;->PREMIUM_UNSEND_MESSAGE` (the
 * obfuscated class descriptors drift; the enum member name is stable). Only `y0$y.a` accesses both.
 * All three obfuscated descriptors here (`Lj51/a;`, `Lj51/c;`, fields `p`/`o`) must be re-verified
 * on a LINE version bump.
 */
internal object UnsendMenuAgeGateFingerprint : Fingerprint(
    returnType = "Lj51/c;",
    filters = listOf(
        fieldAccess(definingClass = "Lj51/a;", name = "p"),
        fieldAccess(definingClass = "Lj51/c;", name = "PREMIUM_UNSEND_MESSAGE"),
    ),
)

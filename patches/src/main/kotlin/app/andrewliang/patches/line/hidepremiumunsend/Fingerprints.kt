package app.andrewliang.patches.line.hidepremiumunsend

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * `UnsendMessageLdsDialog.onViewCreated` — the unsend-confirmation dialog. For the `UnsendSilently`
 * request variant (`…$a$c`) it reveals a premium "Unsend discreetly" icon + label (string id
 * `0x7f150bff`). Matching the guarding `instance-of` (the only one before that label) and forcing it
 * false hides both, leaving the ordinary "Unsend / Close" dialog intact — its action buttons are
 * separate views.
 *
 * The literal `0x7f150bff` is globally unique. `opcode(INSTANCE_OF)` avoids pinning the drift-prone
 * inner-class name `…$a$c`.
 */
internal object UnsendDiscreetlyButtonFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.INSTANCE_OF),
        literal(0x7f150bff),
    ),
)

/**
 * Locates `wi1.j4` via the globally-unique "How to unsend discreetly" promo-link string id
 * (`0x7f150bf8`). Its constructor builds the promo link only when
 * `ne1.k2.a(i1.W() && i1.X(), …) == SUPPORTED_CHAT`, so forcing that first argument false leaves the
 * link handler null. The literal pins only the obfuscated class. The `k2.a` call site is then found
 * by scanning it.
 */
internal object UnsendPromoLinkFingerprint : Fingerprint(
    filters = listOf(
        literal(0x7f150bf8),
    ),
)

/**
 * `ne1.y0$y.a(...)` — the candidate predicate deciding whether the long-press menu shows an "Unsend"
 * item. Its age gate is `sentTime + window >= now`, where `window` is the PREMIUM window
 * (`Lj51/a;->p:I`, ~7 days) for premium-eligible chats, else the FREE one (`Lj51/a;->o:I`, ~1h).
 * That premium window is why the item survives up to ~7 days and, when tapped, triggers the "Give
 * yourself more time" upsell in `oe1.c0.a`; past ~7 days the gate fails and no item is added.
 *
 * The premium-window read (`fieldAccess Lj51/a;->p`) is the instruction to rewrite; the readable enum
 * member `Lj51/c;->PREMIUM_UNSEND_MESSAGE` disambiguates the method, because only `y0$y.a` accesses
 * both. Re-verify all three obfuscated descriptors (`Lj51/a;`, `Lj51/c;`, fields `p`/`o`) on a
 * version bump.
 */
internal object UnsendMenuAgeGateFingerprint : Fingerprint(
    returnType = "Lj51/c;",
    filters = listOf(
        fieldAccess(definingClass = "Lj51/a;", name = "p"),
        fieldAccess(definingClass = "Lj51/c;", name = "PREMIUM_UNSEND_MESSAGE"),
    ),
)

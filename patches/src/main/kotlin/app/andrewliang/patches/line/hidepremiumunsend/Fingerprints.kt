package app.andrewliang.patches.line.hidepremiumunsend

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * `UnsendMessageLdsDialog.onViewCreated` — the unsend-confirmation dialog. For the `UnsendSilently`
 * request variant (`…$a$c`) it reveals a premium "Unsend discreetly" icon + label (string id
 * `0x7f150c81`). Matching the guarding `instance-of` (the only one before that label) and forcing it
 * false hides both, leaving the ordinary "Unsend / Close" dialog intact — its action buttons are
 * separate views.
 *
 * The literal `0x7f150c81` is globally unique. `opcode(INSTANCE_OF)` avoids pinning the drift-prone
 * inner-class name `…$a$c`.
 */
internal object UnsendDiscreetlyButtonFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.INSTANCE_OF),
        literal(0x7f150c81),
    ),
)

/**
 * Locates `nl1.b5` via the globally-unique "How to unsend discreetly" promo-link string id
 * (`0x7f150c7a`). Its constructor builds the promo link only when
 * `kh1.i2.a(i1.Z() && i1.a0(), …) == SUPPORTED_CHAT`, so forcing that first argument false leaves the
 * link handler null. The literal pins only the obfuscated class. The `k2.a` call site is then found
 * by scanning it.
 */
internal object UnsendPromoLinkFingerprint : Fingerprint(
    filters = listOf(
        literal(0x7f150c7a),
    ),
)

/**
 * `kh1.x0$b0.a(...)` — the candidate predicate deciding whether the long-press menu shows an "Unsend"
 * item. Its age gate is `sentTime + window >= now`, where `window` is the PREMIUM window
 * (`Lc81/a;->p:I`, ~7 days) for premium-eligible chats, else the FREE one (`Lc81/a;->o:I`, ~1h).
 * That premium window is why the item survives up to ~7 days and, when tapped, triggers the "Give
 * yourself more time" upsell in `UnsendLitePlanUpgradePopupFragment`; past ~7 days the gate fails and no item is added.
 *
 * The premium-window read (`fieldAccess Lc81/a;->p`) is the instruction to rewrite; the readable enum
 * member `Lc81/c;->PREMIUM_UNSEND_MESSAGE` disambiguates the method, because only `x0$b0.a` accesses
 * both. Re-verify all three obfuscated descriptors (`Lc81/a;`, `Lc81/c;`, fields `p`/`o`) on a
 * version bump.
 */
internal object UnsendMenuAgeGateFingerprint : Fingerprint(
    returnType = "Lc81/c;",
    filters = listOf(
        fieldAccess(definingClass = "Lc81/a;", name = "p"),
        fieldAccess(definingClass = "Lc81/c;", name = "PREMIUM_UNSEND_MESSAGE"),
    ),
)

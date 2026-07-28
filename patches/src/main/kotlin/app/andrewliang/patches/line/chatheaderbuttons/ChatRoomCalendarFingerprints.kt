package app.andrewliang.patches.line.chatheaderbuttons

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal

/**
 * Fingerprints for the four calendar buttons that live INSIDE a chat room (as opposed to the
 * Chats-tab header button handled by [CalendarButtonFingerprint]). Each anchors on a token that
 * survives LINE's obfuscation — a Kotlin enum-constant name (`CALENDAR` / `CALENDAR_BUTTON`) or a
 * resource id — so the fingerprint names itself in a stack trace if it ever stops matching.
 */

/**
 * The "+" attach-menu calendar tile is `hg1.b` (CalendarButtonType). We match its constructor —
 * the only method that READS `fg1.a$b.CALENDAR` (the attach-menu item-type enum constant); the
 * only other reference is the enum's own `<clinit>` write, excluded here by the constructor's
 * parameter signature. From the resolved constructor we take `definingClass` (`Lhg1/b;`) and
 * neuter that class's availability predicate `j(...)`.
 */
internal object AttachMenuCalendarButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ln/c;", "Lgg1/c;", "Lv01/c;", "Ljp0/d;", "Ljp0/g;"),
    filters = listOf(
        fieldAccess(definingClass = "Lfg1/a\$b;", name = "CALENDAR"),
    ),
)

/**
 * The chat-room top-toolbar calendar button is added inside `ed1.d0.a(...)` at two sites (one per
 * chat-type branch), each a `sget-object <ed1.g1.CALENDAR_BUTTON>` immediately followed by the
 * `ed1.s1.g(...)` "add header button" call. Two `CALENDAR_BUTTON` field-access filters (in program
 * order) pin this method — it is the only one that reads the constant twice — and yield both
 * `sget-object` indices to remove. (`ed1.u0$b` reads it only once, so it can't satisfy two filters.)
 */
internal object ChatRoomToolbarCalendarButtonFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = "Led1/g1;", name = "CALENDAR_BUTTON"),
        fieldAccess(definingClass = "Led1/g1;", name = "CALENDAR_BUTTON"),
    ),
)

/**
 * The slide-out chat-menu "Calendar" row is `d00.o`. Its constructor loads the calendar row's
 * drawable `R.drawable.chatmenu_ic_list_calendar` (0x7f0807cc) — a literal that appears in only
 * this one class — then forwards its first boolean arg as the row's `isVisible` (`d00.a.e`) field.
 * The menu builder renders a row only when that field is true, so we force the arg false here.
 */
internal object ChatMenuCalendarRowFingerprint : Fingerprint(
    definingClass = "Ld00/o;",
    name = "<init>",
    returnType = "V",
    parameters = listOf("Z", "Lf11/b;", "Ld00/n1;"),
    filters = listOf(
        literal(0x7f0807cc),
    ),
)

/**
 * The message long-press context menu asks each `ne1.y0` provider for a `j51.c` action (null =
 * hide). The calendar provider is `ne1.y0$c`; its `a(Context, v01.a, j51.a, Z)` reads
 * `j51.c.CALENDAR` and returns that action or null. Matching the return type + parameters + the
 * `CALENDAR` read lands uniquely on that provider method, which we force to return null. This is
 * builder-independent (covers every `qe1.a` consumer of the provider list).
 */
internal object ContextMenuCalendarProviderFingerprint : Fingerprint(
    returnType = "Lj51/c;",
    parameters = listOf("Landroid/content/Context;", "Lv01/a;", "Lj51/a;", "Z"),
    filters = listOf(
        fieldAccess(definingClass = "Lj51/c;", name = "CALENDAR"),
    ),
)

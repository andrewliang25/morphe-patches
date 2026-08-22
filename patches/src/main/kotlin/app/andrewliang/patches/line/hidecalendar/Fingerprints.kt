package app.andrewliang.patches.line.hidecalendar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall

/**
 * Fingerprints for every LINE Calendar button in the messenger: the Chats-tab header
 * ([CalendarButtonFingerprint]) plus four inside a chat room (top toolbar, "+" attach menu,
 * slide-out chat menu, message long-press menu). Each anchors on a token obfuscation cannot touch —
 * an enum-constant name (`CALENDAR` / `CALENDAR_BUTTON`) or a resource id.
 */

// The Chats-tab header button enum (`az0.q`). The constant names survive obfuscation.
private const val BUTTON_ENUM = "Laz0/q;"

// The header button list is a `fb8/b`. Pinning the `add`'s definingClass separates it from the
// green-dot icon set (ChatTabHeaderStateImpl$greenDotVisibleIconSet), which reads the same
// constants but adds to `fb8/j` — without it the pair could match the green-dot method instead.
private const val HEADER_LIST_ADD = "Lfb8/b;"

/**
 * The Chats-tab header calendar button is added in the (obfuscated) ChatTabHeaderStateImpl
 * constructor as `sget-object <az0.q.CALENDAR>` + `add(...)`. That pair lands uniquely there — the
 * enum's WhenMappings table reads the same constant but follows it with `ordinal()`. The sibling
 * OPEN_CHAT button is anchored the same way in `chatheaderbuttons`.
 */
internal object CalendarButtonFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = BUTTON_ENUM, name = "CALENDAR"),
        methodCall(definingClass = HEADER_LIST_ADD, name = "add"),
    ),
)

/**
 * The "+" attach-menu calendar tile is `hg1.b` (CalendarButtonType). Matches its constructor — the
 * only method that READS `fg1.a$b.CALENDAR`. The sole other reference is the enum's own `<clinit>`
 * write, excluded by the constructor's parameter signature. `definingClass` (`Lhg1/b;`) then gives
 * the class whose availability predicate `j(...)` gets neutered.
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
 * chat-type branch), each an `sget-object <ed1.g1.CALENDAR_BUTTON>` followed by the `ed1.s1.g(...)`
 * "add header button" call. Two `CALENDAR_BUTTON` filters pin this method — the only one reading the
 * constant twice — and yield both `sget-object` indices. (`ed1.u0$b` reads it once, so it cannot
 * satisfy two filters.)
 */
internal object ChatRoomToolbarCalendarButtonFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = "Led1/g1;", name = "CALENDAR_BUTTON"),
        fieldAccess(definingClass = "Led1/g1;", name = "CALENDAR_BUTTON"),
    ),
)

/**
 * The slide-out chat-menu "Calendar" row is `d00.o`. Its constructor loads the row's drawable
 * `R.drawable.chatmenu_ic_list_calendar` (0x7f0807cc) — a literal appearing in this class only —
 * then forwards its first boolean arg as the row's `isVisible` (`d00.a.e`) field. The menu builder
 * renders a row only when that field is true, so the arg is forced false.
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
 * hide). The calendar provider `ne1.y0$c.a(Context, v01.a, j51.a, Z)` reads `j51.c.CALENDAR` and
 * returns that action or null. Return type + parameters + the `CALENDAR` read land uniquely on it,
 * and it is forced to return null. Builder-independent: covers every `qe1.a` consumer of the list.
 */
internal object ContextMenuCalendarProviderFingerprint : Fingerprint(
    returnType = "Lj51/c;",
    parameters = listOf("Landroid/content/Context;", "Lv01/a;", "Lj51/a;", "Z"),
    filters = listOf(
        fieldAccess(definingClass = "Lj51/c;", name = "CALENDAR"),
    ),
)

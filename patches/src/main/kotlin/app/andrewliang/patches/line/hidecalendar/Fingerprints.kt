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

// The Chats-tab header button enum (`q11.n`). The constant names survive obfuscation.
private const val BUTTON_ENUM = "Lq11/n;"

// The header button list is a `ki8/b`. Pinning the `add`'s definingClass separates it from the
// green-dot icon set (ChatTabHeaderStateImpl$greenDotVisibleIconSet), which reads the same
// constants but adds to `ki8/j` — without it the pair could match the green-dot method instead.
private const val HEADER_LIST_ADD = "Lki8/b;"

/**
 * The Chats-tab header calendar button is added in the (obfuscated) ChatTabHeaderStateImpl
 * constructor as `sget-object <q11.n.CALENDAR>` + `add(...)`. That pair lands uniquely there — the
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
 * The "+" attach-menu calendar tile is `yi1.b` (CalendarButtonType). Matches its constructor — the
 * only method that READS `wi1.b$b.CALENDAR`. The sole other reference is the enum's own `<clinit>`
 * write, excluded by the constructor's parameter signature. `definingClass` (`Lyi1/b;`) then gives
 * the class whose availability predicate `j(...)` gets neutered.
 */
internal object AttachMenuCalendarButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ln/c;", "Lxi1/b;", "Ll31/c;", "Lor0/d;", "Lor0/g;"),
    filters = listOf(
        fieldAccess(definingClass = "Lwi1/b\$b;", name = "CALENDAR"),
    ),
)

/**
 * The chat-room top-toolbar calendar button is added inside `ag1.e0.a(...)` at two sites (one per
 * chat-type branch), each an `sget-object <ag1.i1.CALENDAR_BUTTON>` followed by the `ag1.t1.g(...)`
 * "add header button" call. Two `CALENDAR_BUTTON` filters pin this method — the only one reading the
 * constant twice — and yield both `sget-object` indices. (`ag1.w0$b` reads it once, so it cannot
 * satisfy two filters.)
 */
internal object ChatRoomToolbarCalendarButtonFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = "Lag1/i1;", name = "CALENDAR_BUTTON"),
        fieldAccess(definingClass = "Lag1/i1;", name = "CALENDAR_BUTTON"),
    ),
)

/**
 * The slide-out chat-menu "Calendar" row is `z00.l`. Its constructor loads the row's drawable
 * `R.drawable.chatmenu_ic_list_calendar` (0x7f0807c1) — a literal appearing in this class only —
 * then forwards its first boolean arg as the row's `isVisible` (`z00.a.e`) field. The menu builder
 * renders a row only when that field is true, so the arg is forced false.
 */
internal object ChatMenuCalendarRowFingerprint : Fingerprint(
    definingClass = "Lz00/l;",
    name = "<init>",
    returnType = "V",
    parameters = listOf("Z", "Lv31/b;", "Les0/m0;"),
    filters = listOf(
        literal(0x7f0807c1),
    ),
)

/**
 * The message long-press context menu asks each `kh1.x0` provider for a `c81.c` action (null =
 * hide). The calendar provider `kh1.x0$c.a(Context, l31.a, c81.a, Z)` reads `c81.c.CALENDAR` and
 * returns that action or null. Return type + parameters + the `CALENDAR` read land uniquely on it,
 * and it is forced to return null. Builder-independent: covers every `lh1.a` consumer of the list.
 */
internal object ContextMenuCalendarProviderFingerprint : Fingerprint(
    returnType = "Lc81/c;",
    parameters = listOf("Landroid/content/Context;", "Ll31/a;", "Lc81/a;", "Z"),
    filters = listOf(
        fieldAccess(definingClass = "Lc81/c;", name = "CALENDAR"),
    ),
)

package app.andrewliang.patches.line.chatheaderbuttons

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall

private const val BUTTON_ENUM = "Laz0/q;"

/**
 * The Chats-tab header button set is built in the ChatTabHeaderStateImpl constructor
 * (obfuscated), which adds each button as `sget-object <az0.q constant>` + `add(...)` to the
 * button list. The button enum constant names (CALENDAR, OPEN_CHAT, …) are non-obfuscated
 * (Kotlin enum names survive), so we anchor on them.
 *
 * Each fingerprint matches its own enum constant's `sget-object` immediately followed by the
 * list `add` call — that pair uniquely lands in the constructor (the enum's WhenMappings
 * table accesses the same constants but follows them with `ordinal()`, not `add`). Anchoring
 * each button independently means the two patches don't depend on each other and work in
 * either order when both are enabled.
 */
// The header button list is a `fb8/b` (the `add` calls target `Lfb8/b;`). The `add`'s
// definingClass is pinned to disambiguate from the green-dot icon set (ChatTabHeaderStateImpl
// $greenDotVisibleIconSet), which references the same OPEN_CHAT constant but adds to a
// different list class (`fb8/j`) — without this, OPEN_CHAT + add could silently match the
// green-dot method instead of the header builder.
private const val HEADER_LIST_ADD = "Lfb8/b;"

internal object CalendarButtonFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = BUTTON_ENUM, name = "CALENDAR"),
        methodCall(definingClass = HEADER_LIST_ADD, name = "add"),
    ),
)

internal object CommunityButtonFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = BUTTON_ENUM, name = "OPEN_CHAT"),
        methodCall(definingClass = HEADER_LIST_ADD, name = "add"),
    ),
)

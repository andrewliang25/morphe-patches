package app.andrewliang.patches.line.chatheaderbuttons

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall

private const val BUTTON_ENUM = "Laz0/q;"

/**
 * The ChatTabHeaderStateImpl constructor (obfuscated) builds the Chats-tab header button set.
 * It adds each button to the button list as `sget-object <az0.q constant>` + `add(...)`. The
 * button enum constant names (OPEN_CHAT, CALENDAR, …) are non-obfuscated, because Kotlin enum
 * names survive, so we anchor on them.
 *
 * The fingerprint matches its enum constant's `sget-object` followed at once by the list
 * `add` call. That pair lands only in the constructor. The enum's WhenMappings table reads the
 * same constants, but follows them with `ordinal()`, not `add`. The `hidecalendar` package
 * anchors the Chats-tab header calendar button the same way. Thus the two patches do not depend
 * on each other and work in either order when both are on.
 */
// The header button list is a `fb8/b` (the `add` calls target `Lfb8/b;`). Pinning the `add`'s
// definingClass separates it from the green-dot icon set (ChatTabHeaderStateImpl
// $greenDotVisibleIconSet), which reads the same OPEN_CHAT constant but adds to a different
// list class (`fb8/j`). Without this pin, OPEN_CHAT + add could silently match the green-dot
// method instead of the header builder.
private const val HEADER_LIST_ADD = "Lfb8/b;"

internal object CommunityButtonFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = BUTTON_ENUM, name = "OPEN_CHAT"),
        methodCall(definingClass = HEADER_LIST_ADD, name = "add"),
    ),
)

package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.line.shared.ServerFlagFingerprint
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

/**
 * The ChatTabHeaderStateImpl constructor (obfuscated) builds the Chats-tab header button set.
 * It adds each button to the button list as `sget-object <button constant>` + `add(...)` at once
 * after it. The button enum constant names (OPEN_CHAT, CALENDAR, …) are non-obfuscated, because
 * Kotlin enum names survive, so we anchor on them.
 *
 * The OPEN_CHAT + `add` pair lands only in this constructor. The enum's WhenMappings table reads
 * the same constant, but follows it with `ordinal()`, not `add`. The green-dot icon set
 * (ChatTabHeaderStateImpl$greenDotVisibleIconSet) also adds OPEN_CHAT to a list, but it is a
 * lambda, not a constructor. The `hidecalendar` package anchors the Chats-tab header calendar
 * button the same way. Thus the two patches do not depend on each other and work in either
 * order when both are on.
 */
internal object CommunityButtonFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "OPEN_CHAT", opcode = Opcode.SGET_OBJECT),
        methodCall(
            name = "add",
            returnType = "Z",
            parameters = listOf("Ljava/lang/Object;"),
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The server flag for the AI Friends button . The header builder adds
 * `AI_FRIEND` if this flag is true, and `ALBUM` in its place if it is false.
 */
internal object AiFriendsFlagFingerprint :
    ServerFlagFingerprint("function.ai.character.display_button")

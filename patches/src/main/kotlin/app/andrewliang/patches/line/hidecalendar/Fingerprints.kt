package app.andrewliang.patches.line.hidecalendar

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

/**
 * Fingerprints for every LINE Calendar button in the messenger: the Chats-tab header
 * ([CalendarButtonFingerprint]) plus four inside a chat room (top toolbar, "+" attach menu,
 * slide-out chat menu, message long-press menu). Each anchors on a token obfuscation cannot touch —
 * an enum-constant name (`CALENDAR` / `CALENDAR_BUTTON`) or a resource name.
 */

/**
 * The Chats-tab header calendar button is added in the (obfuscated) ChatTabHeaderStateImpl
 * constructor as `sget-object CALENDAR` + a list `add(...)` at once after it. That pair lands
 * only there. The enum's WhenMappings table reads the same constant but follows it with
 * `ordinal()`, and the green-dot icon set is a lambda, not a constructor. The sibling OPEN_CHAT
 * button is anchored the same way in `chatheaderbuttons`.
 */
internal object CalendarButtonFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "CALENDAR", opcode = Opcode.SGET_OBJECT),
        methodCall(
            name = "add",
            returnType = "Z",
            parameters = listOf("Ljava/lang/Object;"),
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The constructor of the "+" attach-menu calendar tile (CalendarButtonType). It reads the
 * attach-item type constant `CALENDAR` and then loads the tile icon `chat_ui_context_calendar`.
 * Other methods read a `CALENDAR` constant or load that drawable, but only this one does both.
 */
internal object AttachMenuCalendarButtonFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "CALENDAR", opcode = Opcode.SGET_OBJECT),
        literal({ resourceId("drawable", "chat_ui_context_calendar") }),
    ),
)

/**
 * The chat-room top-toolbar calendar button is added at two sites (one per chat-type branch),
 * each an `sget-object CALENDAR_BUTTON` followed by the "add header button" call. Two
 * `CALENDAR_BUTTON` filters pin this method — the only `V` method that reads the constant twice —
 * and yield both `sget-object` indices.
 */
internal object ChatRoomToolbarCalendarButtonFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "CALENDAR_BUTTON", opcode = Opcode.SGET_OBJECT),
        fieldAccess(name = "CALENDAR_BUTTON", opcode = Opcode.SGET_OBJECT),
    ),
)

/**
 * The constructor of the slide-out chat-menu "Calendar" row. It loads the row's drawable
 * `chatmenu_ic_list_calendar` — a literal that appears in this class only — then forwards its
 * first boolean arg as the row's `isVisible` field. The menu builder renders a row only when that
 * field is true, so the arg is forced false.
 */
internal object ChatMenuCalendarRowFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Z", "L", "L"),
    filters = listOf(
        literal({ resourceId("drawable", "chatmenu_ic_list_calendar") }),
    ),
)

/**
 * The message long-press context menu asks each provider for an action (null = hide). The
 * calendar provider `a(Context, ?, ?, Z)` reads the action constant `CALENDAR` and returns that
 * action or null. The signature plus the `CALENDAR` read land only on it, and it is forced to
 * return null. Builder-independent: covers every consumer of the list.
 */
internal object ContextMenuCalendarProviderFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("Landroid/content/Context;", "L", "L", "Z"),
    filters = listOf(
        fieldAccess(name = "CALENDAR", opcode = Opcode.SGET_OBJECT),
    ),
)

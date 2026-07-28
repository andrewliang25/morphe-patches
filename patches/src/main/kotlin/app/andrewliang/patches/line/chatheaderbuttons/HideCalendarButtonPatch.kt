package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

@Suppress("unused")
val hideCalendarButtonPatch = bytecodePatch(
    name = "Hide calendar button",
    description = "Removes every LINE Calendar button: the one in the Chats-tab header, and the " +
        "four inside a chat room — the top toolbar, the + attach menu, the slide-out chat menu, " +
        "and the message long-press menu. Also hides the related \"Events\" row in the chat menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // 1. Chats-tab header: remove the `sget-object CALENDAR` + following list `add(...)` pair
        //    so the header button is never added. instructionMatches[0] = the CALENDAR sget.
        val calendarSgetIndex = CalendarButtonFingerprint.instructionMatches.first().index
        CalendarButtonFingerprint.method.removeInstructions(calendarSgetIndex, 2)

        // 2. Chat-room "+" attach menu: the CalendarButtonType (hg1.b) is shown only if its
        //    availability predicate `j(gi1.b)Z` returns true (the attach-menu filter hg1.r.f
        //    gates on it). Neuter that predicate to false; the item is then dropped by the
        //    existing filter in gg1.e. Anchor via the constructor (the sole reader of the
        //    fg1.a$b.CALENDAR enum constant), then select `j` by its unique descriptor.
        val attachMenuCalendarClass =
            mutableClassDefBy(AttachMenuCalendarButtonFingerprint.method.definingClass)
        val availabilityMethod = attachMenuCalendarClass.methods.first { method ->
            method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf("Lgi1/b;")
        }
        availabilityMethod.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """,
        )

        // 3. Chat-room top toolbar: ed1.d0.a adds the button at two chat-type branches, each a
        //    `sget-object CALENDAR_BUTTON` + following `ed1.s1.g(...)` add call. Remove both
        //    pairs. instructionMatches[0] = earlier site, [1] = later; remove the higher index
        //    first so the earlier one stays valid.
        val toolbarMatches = ChatRoomToolbarCalendarButtonFingerprint.instructionMatches
        ChatRoomToolbarCalendarButtonFingerprint.method.apply {
            removeInstructions(toolbarMatches[1].index, 2)
            removeInstructions(toolbarMatches[0].index, 2)
        }

        // 4. Slide-out chat menu: the calendar row (d00.o) forwards its first ctor bool as the
        //    row's isVisible field (d00.a.e); the menu builder only renders rows whose e is true.
        //    Force that bool false at method entry (p1 is the first param) so the row is filtered
        //    out.
        ChatMenuCalendarRowFingerprint.method.addInstructions(0, "const/4 p1, 0x0")

        // 5. Message long-press menu: the calendar provider ne1.y0$c.a(...) returns a j51.c action
        //    or null (null = hide). Force it to return null. .locals 3 -> v0 is free.
        ContextMenuCalendarProviderFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )

        // 6. Chat-menu "Events" row (a separate feature folded in by request): the row is a shared
        //    d00.z item built in ChatHistoryMenuFragment, gated by the boolean it loads from
        //    Lyz/s4;->l:Z right before the Events label. Replace that `iget-boolean` (matched
        //    filter [0]) with a const 0 into the same destination register, so only the Events row
        //    is dropped. The loaded value flows straight into the row's isVisible ctor arg.
        val eventsFlagMatch = EventsMenuRowFingerprint.instructionMatches.first()
        val eventsFlagReg = (eventsFlagMatch.instruction as TwoRegisterInstruction).registerA
        EventsMenuRowFingerprint.method.apply {
            removeInstruction(eventsFlagMatch.index)
            addInstructions(eventsFlagMatch.index, "const/16 v$eventsFlagReg, 0x0")
        }
    }
}

package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideCalendarButtonPatch = bytecodePatch(
    name = "Hide calendar button",
    description = "Removes the calendar button from the top of the Chats tab header.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Remove the `sget-object CALENDAR` + following `add(...)` pair so the button is never
    // added to the header list. instructionMatches[0] = the CALENDAR sget-object; the add is
    // the immediately-following instruction.
    execute {
        val calendarSgetIndex = CalendarButtonFingerprint.instructionMatches.first().index
        CalendarButtonFingerprint.method.removeInstructions(calendarSgetIndex, 2)
    }
}

package app.andrewliang.patches.line.hidecalendar

import app.andrewliang.patches.line.shared.hideAttachTile
import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.line.shared.resolveHomeState
import app.andrewliang.patches.line.shared.skipIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val CALENDAR_MODULE_TYPE = "FriendsSubTabCalendar"
private const val FEATURE = "hideCalendar"

@Suppress("unused")
val hideCalendarButtonPatch = bytecodePatch(
    name = "[Chat] Hide calendar buttons",
    description = "Removes every LINE Calendar button: in the Chats tab, the chat toolbar and " +
        "menus, and the Home tab.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch)

    dependsOn(lineSettingsExtensionPatch)

    // With the switch off, every lever runs the original code.
    execute {
        // Resolve the Home-state constructor before any mutation. The patcher does not undo a
        // partial execute, so a lookup that throws after an earlier lever has already been
        // written would ship a half-applied patch that nothing reports and nothing reverses.
        val homeState = resolveHomeState()

        // 1. Chats-tab header: skip the `sget-object CALENDAR` + following list `add(...)` pair
        //    so the header button is never added. instructionMatches[0] = the CALENDAR sget. The
        //    switch uses the register of the sget: the sget writes it, and the code after the add
        //    writes it before it reads it.
        val calendarSget = CalendarButtonFingerprint.instructionMatches.first()
        CalendarButtonFingerprint.method.skipIfLineSetting(
            calendarSget.index,
            2,
            FEATURE,
            calendarSget.instruction.register(),
        )

        // 2. Chat-room "+" attach menu: hide the calendar tile.
        hideAttachTile(AttachMenuCalendarButtonFingerprint.method.definingClass, FEATURE)

        // 3. Chat-room top toolbar: the button is added at two chat-type branches, each a
        //    `sget-object CALENDAR_BUTTON` + following add call. Skip both pairs.
        //    instructionMatches[0] = earlier site, [1] = later. Patch the higher index first so
        //    the earlier one stays valid. The switch uses the register of each sget, as in lever 1.
        val toolbarMatches = ChatRoomToolbarCalendarButtonFingerprint.instructionMatches
        ChatRoomToolbarCalendarButtonFingerprint.method.apply {
            skipIfLineSetting(toolbarMatches[1].index, 2, FEATURE, toolbarMatches[1].instruction.register())
            skipIfLineSetting(toolbarMatches[0].index, 2, FEATURE, toolbarMatches[0].instruction.register())
        }

        // 4. Slide-out chat menu: the calendar row forwards its first ctor bool as the row's
        //    isVisible field. The menu builder only renders rows whose isVisible is true. Clear
        //    that bool at method entry (p1 is the first param), so that the menu builder leaves
        //    out the row.
        ChatMenuCalendarRowFingerprint.method.addInstructions(0, hideIfLineSetting(FEATURE, "p1"))

        // 5. Message long-press menu: the calendar provider returns an action or null
        //    (null = hide). Force it to return null. .locals 3 -> v0 is free.
        val provider = ContextMenuCalendarProviderFingerprint.method
        provider.addInstructionsWithLabels(
            0,
            readLineSetting(FEATURE, "v0") +
                """
                    if-eqz v0, :stock
                    const/4 v0, 0x0
                    return-object v0
                """,
            ExternalLabel("stock", provider.getInstruction(0)),
        )

        // 6. Friends sub-tab: the Calendar block is a server-driven module of type
        //    "FriendsSubTabCalendar", not a statically-built button, so none of the five levers
        //    above reaches it. Drop it from the module list the Home state renders. The literal
        //    is the receiver of equals(), so a null type is safe.
        with(homeState) {
            addModuleFilter(
                "filterCalendarModules",
                """
                    const-string v4, "$CALENDAR_MODULE_TYPE"
                    invoke-virtual {v4, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v3
                """,
                FEATURE,
            )
        }
        markLineSettingIncluded(FEATURE)
    }
}

/** The destination register of an `sget-object`, as smali. */
private fun Instruction.register() = "v${(this as OneRegisterInstruction).registerA}"

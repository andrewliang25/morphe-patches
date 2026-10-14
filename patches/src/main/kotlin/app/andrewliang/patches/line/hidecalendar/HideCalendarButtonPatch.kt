package app.andrewliang.patches.line.hidecalendar

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.line.shared.returnFalseIfLineSetting
import app.andrewliang.patches.line.shared.skipIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Llb2/g\$a;"
private const val FILTER_NAME = "filterCalendarModules"
private const val FILTER_DESC = "(Ljava/util/List;)Ljava/util/List;"
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

    dependsOn(lineSettingsExtensionPatch)

    // With the switch off, every lever runs the original code.
    execute {
        // Resolve the Home-state constructor before any mutation. The patcher does not undo a
        // partial execute, so a lookup that throws after an earlier lever has already been
        // written would ship a half-applied patch that nothing reports and nothing reverses.
        val homeState = mutableClassDefBy(HOME_STATE)
        val homeStateCtor = HomeStateCtorFingerprint.method

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

        // 2. Chat-room "+" attach menu: the CalendarButtonType (yi1.b) is shown only if its
        //    availability predicate `j(xk1.b)Z` returns true (the attach-menu filter yi1.p.f
        //    gates on it). Neuter that predicate to false. The item is then dropped by the
        //    existing filter in xi1.c. Anchor via the constructor (the sole reader of the
        //    wi1.b$b.CALENDAR enum constant), then select `j` by its unique descriptor. The
        //    switch uses p0: `j` writes it before it reads it.
        val attachMenuCalendarClass =
            mutableClassDefBy(AttachMenuCalendarButtonFingerprint.method.definingClass)
        val availabilityMethod = attachMenuCalendarClass.methods.first { method ->
            method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf("Lxk1/b;")
        }
        availabilityMethod.returnFalseIfLineSetting(FEATURE, "p0")

        // 3. Chat-room top toolbar: ag1.e0.a adds the button at two chat-type branches, each a
        //    `sget-object CALENDAR_BUTTON` + following `ag1.t1.g(...)` add call. Skip both
        //    pairs. instructionMatches[0] = earlier site, [1] = later. Patch the higher index
        //    first so the earlier one stays valid. The switch uses the register of each sget, as
        //    in lever 1.
        val toolbarMatches = ChatRoomToolbarCalendarButtonFingerprint.instructionMatches
        ChatRoomToolbarCalendarButtonFingerprint.method.apply {
            skipIfLineSetting(toolbarMatches[1].index, 2, FEATURE, toolbarMatches[1].instruction.register())
            skipIfLineSetting(toolbarMatches[0].index, 2, FEATURE, toolbarMatches[0].instruction.register())
        }

        // 4. Slide-out chat menu: the calendar row (z00.l) forwards its first ctor bool as the
        //    row's isVisible field (z00.a.e). The menu builder only renders rows whose e is true.
        //    Clear that bool at method entry (p1 is the first param), so that the menu builder
        //    leaves out the row.
        ChatMenuCalendarRowFingerprint.method.addInstructions(0, hideIfLineSetting(FEATURE, "p1"))

        // 5. Message long-press menu: the calendar provider kh1.x0$c.a(...) returns a c81.c action
        //    or null (null = hide). Force it to return null. .locals 3 -> v0 is free.
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
        //    above reaches it. Drop it from the module list the Compose state renders.
        //
        //    The loop lives in a new method. Injecting a backward-branching loop inline corrupts
        //    an existing method's branch layout, and ART then throws a VerifyError.
        val filter = MutableMethod(
            ImmutableMethod(
                HOME_STATE,
                FILTER_NAME,
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(6),
            ),
        )
        homeState.methods.add(filter)
        // p0 = input List. v0 = switch, then result ArrayList, v1 = iterator, v2 = element,
        // v3 = type/bool, v4 = the literal. The literal is the receiver of equals(), so a null type
        // is safe. With the switch off, the method returns the input list.
        filter.addInstructions(
            0,
            readLineSetting(FEATURE, "v0") +
                """
                if-nez v0, :filter
                return-object p0
                :filter
                new-instance v0, Ljava/util/ArrayList;
                invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                invoke-interface {p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v1
                :loop
                invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
                move-result v2
                if-eqz v2, :done
                invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v2
                check-cast v2, Ly82/j0;
                iget-object v3, v2, Ly82/j0;->e:Ly82/k0;
                invoke-interface {v3}, Ly82/k0;->getType()Ljava/lang/String;
                move-result-object v3
                const-string v4, "$CALENDAR_MODULE_TYPE"
                invoke-virtual {v4, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v3
                if-nez v3, :loop
                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                return-object v0
            """,
        )

        //    Replace the list parameter (p1) with the filtered copy at the top of the ctor, before
        //    it is stored. The call is branchless (invoke + move-result) and reuses p1.
        homeStateCtor.addInstructions(
            0,
            """
                invoke-static {p1}, $HOME_STATE->$FILTER_NAME$FILTER_DESC
                move-result-object p1
            """,
        )
        markLineSettingIncluded(FEATURE)
    }
}

/** The destination register of an `sget-object`, as smali. */
private fun Instruction.register() = "v${(this as OneRegisterInstruction).registerA}"

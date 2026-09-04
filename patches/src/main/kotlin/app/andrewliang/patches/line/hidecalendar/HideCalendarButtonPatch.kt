package app.andrewliang.patches.line.hidecalendar

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HOME_STATE = "Llb2/g\$a;"
private const val FILTER_NAME = "filterCalendarModules"
private const val FILTER_DESC = "(Ljava/util/List;)Ljava/util/List;"
private const val CALENDAR_MODULE_TYPE = "FriendsSubTabCalendar"

@Suppress("unused")
val hideCalendarButtonPatch = bytecodePatch(
    name = "Hide calendar buttons",
    description = "Removes every LINE Calendar surface inside the messenger. One is in the " +
        "Chats-tab header. Four are in a chat room: the top toolbar, the + attach menu, the " +
        "slide-out chat menu, and the message long-press menu. The last is the Calendar block " +
        "in the Friends sub-tab of the Home tab.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // Resolve the Home-state constructor before any mutation. The patcher does not undo a
        // partial execute, so a lookup that throws after an earlier lever has already been
        // written would ship a half-applied patch that nothing reports and nothing reverses.
        val homeState = mutableClassDefBy(HOME_STATE)
        val homeStateCtor = HomeStateCtorFingerprint.method

        // 1. Chats-tab header: remove the `sget-object CALENDAR` + following list `add(...)` pair
        //    so the header button is never added. instructionMatches[0] = the CALENDAR sget.
        val calendarSgetIndex = CalendarButtonFingerprint.instructionMatches.first().index
        CalendarButtonFingerprint.method.removeInstructions(calendarSgetIndex, 2)

        // 2. Chat-room "+" attach menu: the CalendarButtonType (yi1.b) is shown only if its
        //    availability predicate `j(xk1.b)Z` returns true (the attach-menu filter yi1.p.f
        //    gates on it). Neuter that predicate to false. The item is then dropped by the
        //    existing filter in xi1.c. Anchor via the constructor (the sole reader of the
        //    wi1.b$b.CALENDAR enum constant), then select `j` by its unique descriptor.
        val attachMenuCalendarClass =
            mutableClassDefBy(AttachMenuCalendarButtonFingerprint.method.definingClass)
        val availabilityMethod = attachMenuCalendarClass.methods.first { method ->
            method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf("Lxk1/b;")
        }
        availabilityMethod.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """,
        )

        // 3. Chat-room top toolbar: ag1.e0.a adds the button at two chat-type branches, each a
        //    `sget-object CALENDAR_BUTTON` + following `ag1.t1.g(...)` add call. Remove both
        //    pairs. instructionMatches[0] = earlier site, [1] = later. Remove the higher index
        //    first so the earlier one stays valid.
        val toolbarMatches = ChatRoomToolbarCalendarButtonFingerprint.instructionMatches
        ChatRoomToolbarCalendarButtonFingerprint.method.apply {
            removeInstructions(toolbarMatches[1].index, 2)
            removeInstructions(toolbarMatches[0].index, 2)
        }

        // 4. Slide-out chat menu: the calendar row (z00.l) forwards its first ctor bool as the
        //    row's isVisible field (z00.a.e). The menu builder only renders rows whose e is true.
        //    Force that bool false at method entry (p1 is the first param) so the row is filtered
        //    out.
        ChatMenuCalendarRowFingerprint.method.addInstructions(0, "const/4 p1, 0x0")

        // 5. Message long-press menu: the calendar provider kh1.x0$c.a(...) returns a c81.c action
        //    or null (null = hide). Force it to return null. .locals 3 -> v0 is free.
        ContextMenuCalendarProviderFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
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
        // p0 = input List. v0 = result ArrayList, v1 = iterator, v2 = element, v3 = type/bool,
        // v4 = the literal. The literal is the receiver of equals(), so a null type is safe.
        filter.addInstructions(
            0,
            """
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
    }
}

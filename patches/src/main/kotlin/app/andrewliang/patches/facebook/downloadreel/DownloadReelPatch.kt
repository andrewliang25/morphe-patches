package app.andrewliang.patches.facebook.downloadreel

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HANDLER = "Lapp/andrewliang/extension/ReelDownload;"

private const val VIDEO_DATA_SOURCE = "Lcom/facebook/video/engine/api/VideoDataSource;"
private const val VIDEO_PLAYER_PARAMS = "Lcom/facebook/video/engine/api/VideoPlayerParams;"
private const val FB_USER_SESSION = "Lcom/facebook/auth/usersession/FbUserSession;"
private const val CONTEXT = "Landroid/content/Context;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val LIST = "Ljava/util/List;"
private const val ARRAY_LIST = "Ljava/util/ArrayList;"

/** The name the sidebar component reports for itself, inside the method that builds it. */
private const val SIDEBAR = "UDDSideBarComponent"

/** The row Facebook shows on your own video. Its icon is the one this button borrows. */
private const val DOWNLOAD_ROW = "fds_control_download_video"

/** What this button is called in the slot the sidebar fills with `share_button`. */
private const val TEST_ID = "download_button"

/** The label. The factory takes it as a plain string, so no resource is needed. */
private const val LABEL = "Download"

/**
 * What is written under the icon.
 *
 * The other buttons of the strip put a count here, so it is sized for three or four characters and
 * a word wraps across two lines. The icon already says what the button does.
 */
private const val CAPTION = ""

/**
 * Which of the factory's handlers is the tap.
 *
 * Measured, not guessed. Every slot was given a handler that reported the event it received: the
 * touch slot fires twice per press with a `MotionEvent`, a visibility slot fires by itself, and
 * this one fires once per press with an event holding only the `View`, which is a click.
 */
private const val TAP_SLOT = 1

/** The helper this patch adds to the component. */
private const val HELPER = "andrewDownloadButton"

/**
 * Adds a download button beside every reel.
 *
 * Facebook has a download row and builds it only for a video that you posted. Ownership chooses
 * the whole viewer rather than a flag inside one, so for anybody else's reel the sheet that holds
 * that row is never asked for, and there is nothing to force. `docs/facebook-ads-map.md` records
 * the four device rounds that established this, and the two readings of it that were wrong.
 *
 * So this adds a button to the sidebar beside the reel, which every reel has. Nothing is invented.
 * The sidebar builds all of its own buttons through one factory, and that factory takes the label
 * as a plain string and the icon as an enum constant, so no resource and no downloaded string pack
 * is involved. This calls the same factory, with the icon Facebook's own download row draws.
 *
 * The handler holds the player of that one item, read off a field of the component, so the file
 * saved is the reel on the screen. The app prepares the reels that come next, so a handler reading
 * a shared place would save the wrong one.
 */
@Suppress("unused")
val downloadReelPatch = bytecodePatch(
    name = "[Reels] Download any reel",
    description = "Adds a Download button beside every reel, and not only the reels that you " +
        "posted. It saves the video the player is streaming, at the best quality the player " +
        "holds, to Movies/Facebook.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)
    extendWith("extensions/extension.mpe")

    execute {
        // ---- the real names of the two address fields -----------------------------------------
        //
        // The source keeps a debug dump that pairs each field with the name it reports for it, so
        // the patch reads those names out of the app rather than writing down letters that change
        // every release. It matters: the third address on that object is the subtitles, so "the
        // first Uri" would quietly save the wrong thing.
        val dump = mutableClassDefBy(VIDEO_DATA_SOURCE).methods.firstOrNull { method ->
            method.instructions().any { it.stringReference() == "videoHdUri" }
        }

        check(dump != null) { "$VIDEO_DATA_SOURCE reports no field names" }

        val names = fieldNames(dump.instructions(), VIDEO_DATA_SOURCE)
        val hdField = names["videoHdUri"]
        val sdField = names["videoUri"]

        check(hdField != null && sdField != null) {
            "Expected videoHdUri and videoUri on the source, found ${names.keys}"
        }

        // ---- the sidebar ------------------------------------------------------------------------
        val sidebars = mutableListOf<Pair<String, String>>()

        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val list = method.instructions()

                // The name alone is in a dozen places: state helpers, lambdas, update calls. The
                // one wanted is the method that both names the sidebar and builds a button, which
                // is the call taking four handlers.
                val namesSidebar = list.any { it.stringReference() == SIDEBAR }
                val buildsButton = list.any { instruction ->
                    instruction.methodReference()?.parameterTypes
                        ?.count { it.toString() == FUNCTION1 } == 4
                }

                if (namesSidebar && buildsButton) sidebars += classDef.type to method.name
            }
        }

        check(sidebars.size == 1) {
            "Expected 1 sidebar builder, found ${sidebars.size}: " +
                sidebars.joinToString { "${it.first}->${it.second}" }
        }

        val (sidebarClass, sidebarName) = sidebars.single()
        val component = mutableClassDefBy(sidebarClass)
        val sidebar = component.methods.single { it.name == sidebarName }
        val instructions = sidebar.instructions()

        val scopedType = sidebar.parameterTypes.single().toString()

        // ---- the button factory -----------------------------------------------------------------
        //
        // One call here takes four handlers. That is the factory, and its own parameter list then
        // names every wrapper the button needs, so none of them has to be searched for separately.
        val factories = instructions.mapNotNull { it.methodReference() }
            .filter { reference ->
                reference.parameterTypes.count { it.toString() == FUNCTION1 } == 4 &&
                    reference.parameterTypes.firstOrNull()?.toString() == FB_USER_SESSION
            }
            .distinctBy { "${it.definingClass}->${it.name}" }

        check(factories.size == 1) {
            "Expected 1 button factory in $sidebarClass->$sidebarName, found ${factories.size}"
        }

        val factory = factories.single()
        val parameters = factory.parameterTypes.map(CharSequence::toString)

        check(parameters.size == 19) {
            "The button factory takes ${parameters.size} parameters, expected 19"
        }

        val modeType = parameters[1]
        val primaryType = parameters[2]
        val labelType = parameters[3]
        val iconType = parameters[5]

        // The mode constant this method already uses for its own buttons.
        val mode = instructions.firstNotNullOfOrNull { instruction ->
            instruction.fieldReference()?.takeIf {
                instruction.opcode == Opcode.SGET_OBJECT && it.type == modeType
            }
        }

        check(mode != null) { "No constant of $modeType is used in $sidebarName" }

        // The icon is wrapped by a class that extends the icon parameter and takes the enum.
        val iconWrapper = instructions.firstNotNullOfOrNull { instruction ->
            instruction.methodReference()?.takeIf {
                it.name == "<init>" &&
                    it.parameterTypes.size == 1 &&
                    mutableClassDefByOrNull(it.definingClass)?.superclass == iconType
            }
        }

        check(iconWrapper != null) { "No wrapper of $iconType is built in $sidebarName" }

        val iconEnumType = iconWrapper.parameterTypes.single().toString()

        // The icon itself is whichever constant Facebook's own download row draws, so the button
        // looks like the one Facebook would have shown.
        var icon: FieldReference? = null

        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val list = method.instructions()
                if (list.none { it.stringReference() == DOWNLOAD_ROW }) return@forEach

                list.forEach { instruction ->
                    val field = instruction.fieldReference() ?: return@forEach
                    if (instruction.opcode == Opcode.SGET_OBJECT && field.type == iconEnumType) {
                        if (icon == null) icon = field
                    }
                }
            }
        }

        check(icon != null) { "The download row of Facebook draws no icon of $iconEnumType" }

        // ---- what the component holds --------------------------------------------------------
        //
        // Both by type. The player params are the field whose own type holds the player, and the
        // session is the only field of its kind.
        val playerField = component.fields.single { field ->
            mutableClassDefByOrNull(field.type.toString())?.fields?.any {
                it.type.toString() == VIDEO_PLAYER_PARAMS
            } == true
        }

        val sessionField = component.fields.single { it.type.toString() == FB_USER_SESSION }
        val contextField = mutableClassDefBy(scopedType).fields
            .single { it.type.toString() == CONTEXT }

        // ---- the helper -------------------------------------------------------------------------
        //
        // Its own method, so it has a fresh set of registers. The sidebar runs with more than
        // ninety and every one of them is live somewhere.
        val helper = ImmutableMethod(
            sidebarClass,
            HELPER,
            listOf(
                ImmutableMethodParameter(FB_USER_SESSION, null, null),
                ImmutableMethodParameter(scopedType, null, null),
                ImmutableMethodParameter(playerField.type.toString(), null, null),
            ),
            factory.returnType.toString(),
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            MutableMethodImplementation(64),
        ).toMutable().apply {
            addInstructions(
                0,
                buildButton(
                    factory = factory,
                    parameters = parameters,
                    mode = mode,
                    icon = icon!!,
                    iconWrapperClass = iconWrapper.definingClass,
                    iconEnumType = iconEnumType,
                    primaryType = primaryType,
                    labelType = labelType,
                    scopedType = scopedType,
                    contextField = contextField.name,
                    hdField = hdField,
                    sdField = sdField,
                ),
            )
        }

        component.methods.add(helper)

        // ---- the injection ----------------------------------------------------------------------
        //
        // The sidebar hands three lists to the call that assembles it: one of buttons, one of a
        // marker for each, one of a name for each. The injection goes just before that call, where
        // the low registers have been copied into the argument block and are free again, and where
        // the list is still the same object the call will receive.
        val assemblyIndex = instructions.indexOfFirst { instruction ->
            instruction.methodReference()?.parameterTypes?.count { it.toString() == ARRAY_LIST } == 2
        }

        check(assemblyIndex > 0) { "$sidebarName does not assemble a sidebar" }

        val assembly = instructions[assemblyIndex]
        val assemblyReference = assembly.methodReference()!!
        val start = (assembly as RegisterRangeInstruction).startRegister

        // The buttons are the list, not either of the two marker arrays.
        val listParameter = assemblyReference.parameterTypes
            .indexOfFirst { it.toString() == LIST }

        check(listParameter >= 0) { "The sidebar assembly takes no list of buttons" }

        val listRegister = start + listParameter

        // That argument register is filled from a local a few instructions earlier. Adding to the
        // local adds to the same object, so the search is for where it was copied from.
        val sourceRegister = traceSource(instructions, assemblyIndex, listRegister)

        check(sourceRegister != null) { "Could not trace the button list of $sidebarName" }

        // The session and the scoped context are two of the arguments this very call receives, so
        // their registers come from its own parameter list and their types are whatever it says.
        //
        // The parameter registers of the method itself cannot be used. This method reuses them as
        // locals long before the end, so reading `p0` here yields whatever was last put there --
        // which is how the first attempt earned a VerifyError.
        fun argumentRegister(type: String): Int {
            val at = assemblyReference.parameterTypes.indexOfFirst { it.toString() == type }
            check(at >= 0) { "The sidebar assembly takes no $type" }
            return start + at
        }

        // The player of this item is read once in the prologue and parked, because the register
        // holding the component is overwritten soon after. Follow it to where it was parked.
        val playerRegister = instructions.withIndex().firstNotNullOfOrNull { (index, instruction) ->
            val field = instruction.fieldReference() ?: return@firstNotNullOfOrNull null

            if (field.definingClass != sidebarClass || field.name != playerField.name) {
                return@firstNotNullOfOrNull null
            }

            val read = instruction as? TwoRegisterInstruction ?: return@firstNotNullOfOrNull null
            val next = instructions.getOrNull(index + 1) as? TwoRegisterInstruction

            next?.takeIf {
                instructions[index + 1].opcode == Opcode.MOVE_OBJECT_FROM16 &&
                    it.registerB == read.registerA
            }?.registerA
        }

        check(playerRegister != null) { "The player of the item is not parked in $sidebarName" }

        // It has to survive the whole method, because the button is built at the end of it.
        val parkedOnce = instructions.count { instruction ->
            (instruction as? TwoRegisterInstruction)?.registerA == playerRegister &&
                instruction.opcode == Opcode.MOVE_OBJECT_FROM16
        }

        check(parkedOnce == 1) {
            "v$playerRegister is written $parkedOnce times, so it does not hold the player throughout"
        }

        // Each button of the strip is registered twice: the component in one list, and a marker
        // for what kind of button it is in another. A component added without its marker draws
        // but does not answer a tap.
        val markers = instructions.mapNotNull { it.methodReference() }
            .filter {
                it.parameterTypes.size == 1 &&
                    it.parameterTypes.single().toString() == iconEnumType &&
                    it.returnType != "V"
            }
            .distinctBy { "${it.definingClass}->${it.name}" }

        check(markers.size == 1) {
            "Expected 1 marker for an icon, found ${markers.size}"
        }

        val marker = markers.single()
        val markerRegister = traceSource(instructions, assemblyIndex, argumentRegister(ARRAY_LIST))

        check(markerRegister != null) { "Could not trace the marker list of $sidebarName" }

        // Three instructions before the call: the object moves are done, so v0 to v2 are free.
        sidebar.addInstructions(
            assemblyIndex - 3,
            """
                move-object/from16 v0, v${argumentRegister(FB_USER_SESSION)}
                move-object/from16 v1, v${argumentRegister(scopedType)}
                move-object/from16 v2, v$playerRegister
                invoke-static { v0, v1, v2 }, $sidebarClass->$HELPER($FB_USER_SESSION$scopedType${playerField.type})${factory.returnType}
                move-result-object v1
                move-object/from16 v0, v$sourceRegister
                invoke-virtual { v0, v1 }, Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z
                sget-object v2, ${icon!!.definingClass}->${icon!!.name}:$iconEnumType
                invoke-static { v2 }, ${marker.definingClass}->${marker.name}($iconEnumType)${marker.returnType}
                move-result-object v2
                move-object/from16 v0, v$markerRegister
                invoke-virtual { v0, v2 }, Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z
            """,
        )

        println("REEL sidebar=$sidebarClass->$sidebarName assembly@$assemblyIndex list=v$sourceRegister player=v$playerRegister")
        println("REEL factory=${factory.definingClass}->${factory.name}${factory.returnType}")
        println("REEL icon=${icon!!.definingClass}->${icon!!.name}")
        println("REEL fields hd=$hdField sd=$sdField player=${playerField.name} session=${sessionField.name}")
    }
}

/**
 * The body of the helper that builds one button.
 *
 * Register use here is dictated by the instruction formats, not by taste. An `invoke-direct` with
 * arguments and an `iget` both take **4-bit** registers, so everything they touch has to sit in
 * `v0` to `v15`. The factory takes nineteen arguments and therefore needs nineteen **consecutive**
 * registers, so its block sits high at `v40` and each value is moved up once it is built.
 * `new-instance` and `const-string` take 8-bit registers, so those can write high directly.
 */
private fun buildButton(
    factory: MethodReference,
    parameters: List<String>,
    mode: FieldReference,
    icon: FieldReference,
    iconWrapperClass: String,
    iconEnumType: String,
    primaryType: String,
    labelType: String,
    scopedType: String,
    contextField: String,
    hdField: String,
    sdField: String,
): String {
    val signature = "${factory.definingClass}->${factory.name}" +
        parameters.joinToString("", "(", ")") + factory.returnType

    return """
        move-object/from16 v0, p1
        iget-object v0, v0, $scopedType->$contextField:$CONTEXT
        move-object/from16 v5, v0
        move-object/from16 v4, p2
        move-object/from16 v6, p0

${handlers(hdField, sdField)}
        const-string v1, "$LABEL"

        new-instance v2, $primaryType
        invoke-direct { v2, v1, v8 }, $primaryType-><init>(Ljava/lang/String;$FUNCTION1)V
        move-object/from16 v42, v2

        new-instance v2, $labelType
        invoke-direct { v2, v1, v9 }, $labelType-><init>(Ljava/lang/String;$FUNCTION1)V
        move-object/from16 v43, v2

        new-instance v2, $labelType
        invoke-direct { v2, v1, v10 }, $labelType-><init>(Ljava/lang/String;$FUNCTION1)V
        move-object/from16 v44, v2

        sget-object v7, ${icon.definingClass}->${icon.name}:$iconEnumType
        new-instance v2, $iconWrapperClass
        invoke-direct { v2, v7 }, $iconWrapperClass-><init>($iconEnumType)V
        move-object/from16 v45, v2

        move-object/from16 v40, v6
        sget-object v41, ${mode.definingClass}->${mode.name}:${mode.type}
        sget-object v46, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
        sget-object v47, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
        const-string v48, "$TEST_ID"
        const-string v49, "$CAPTION"
        const/16 v50, 0x0
        move-object/from16 v51, v11
        move-object/from16 v52, v12
        move-object/from16 v53, v13
        move-object/from16 v54, v14
        const/16 v55, 0x11
        const/16 v56, 0x0
        const/16 v57, 0x1
        const/16 v58, 0x0

        invoke-static/range { v40 .. v58 }, $signature
        move-result-object v0
        return-object v0
    """
}

/**
 * One handler for each slot the factory takes, each knowing which slot it is.
 *
 * Only the tap slot saves. The others are still given a handler rather than null, because the
 * factory is not documented to accept null, and a handler that returns without a word costs
 * nothing.
 */
private fun handlers(hdField: String, sdField: String) = (0..6).joinToString("\n") { slot ->
    val saves = if (slot == TAP_SLOT) 1 else 0

    """
        new-instance v20, $HANDLER
        move-object/from16 v21, v4
        move-object/from16 v22, v5
        const-string v23, "$hdField"
        const-string v24, "$sdField"
        const/16 v25, 0x$slot
        const/16 v26, 0x$saves
        invoke-direct/range { v20 .. v26 }, $HANDLER-><init>(Ljava/lang/Object;${CONTEXT}Ljava/lang/String;Ljava/lang/String;IZ)V
        move-object/from16 v${8 + slot}, v20
    """
}

/**
 * Every `reported name -> field` pair that a debug dump of [owner] writes.
 *
 * The dump reads a field, then loads the name, then calls the reporter. Nearness alone does not
 * pair them: the tag of the whole class is loaded between the first field and its name, and is
 * nearer to it than the name is. So the pairing is made against the **call**, which is what
 * actually receives the name -- the last string loaded before the reporter runs.
 *
 * One class writes the name before the field instead of after, so a backward window is tried when
 * the forward one finds nothing.
 */
private fun fieldNames(instructions: List<Instruction>, owner: String): Map<String, String> {
    val names = mutableMapOf<String, String>()

    fun scan(from: Int, step: Int): String? {
        var name: String? = null

        var index = from
        while (index in instructions.indices) {
            val instruction = instructions[index]

            // The reporter call ends the window: everything loaded before it is its arguments.
            if (instruction.opcode.name.startsWith("invoke")) return name

            instruction.stringReference()?.let { name = it }
            index += step
        }

        return name
    }

    instructions.forEachIndexed { index, instruction ->
        val field = instruction.fieldReference() ?: return@forEachIndexed
        if (field.definingClass != owner) return@forEachIndexed
        if (!instruction.opcode.name.startsWith("iget")) return@forEachIndexed

        val name = scan(index + 1, 1) ?: scan(index - 1, -1)
        if (name != null) names.putIfAbsent(name, field.name)
    }

    return names
}

/** The local a call argument was copied from, so adding to it adds to the same object. */
private fun traceSource(instructions: List<Instruction>, callIndex: Int, argument: Int): Int? =
    (callIndex - 1 downTo maxOf(0, callIndex - 40))
        .asSequence()
        .mapNotNull { index ->
            val instruction = instructions[index]
            (instruction as? TwoRegisterInstruction)?.takeIf {
                instruction.opcode == Opcode.MOVE_OBJECT_FROM16 && it.registerA == argument
            }?.registerB
        }
        .firstOrNull()

private fun Instruction.methodReference() =
    (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.fieldReference() =
    (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.stringReference() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

private fun MutableMethod.instructions(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

private fun com.android.tools.smali.dexlib2.iface.Method.instructions(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

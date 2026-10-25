package app.andrewliang.patches.facebook.downloadreel

import app.andrewliang.patches.facebook.shared.enableSaveAsH264
import app.andrewliang.patches.facebook.shared.reportedFieldNames
import app.andrewliang.patches.facebook.shared.saveAsH264Option
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HANDLER = "Lapp/andrewliang/extension/ReelMenuDownload;"

private const val VIDEO_DATA_SOURCE = "Lcom/facebook/video/engine/api/VideoDataSource;"
private const val PLAYER_ORIGIN = "Lcom/facebook/video/common/playerorigin/PlayerOrigin;"
private const val CONTEXT = "Landroid/content/Context;"
private const val STRING = "Ljava/lang/String;"
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
private const val LIST = "Ljava/util/List;"
private const val OBJECT = "Ljava/lang/Object;"

/** The id of the More sheet's Clear mode row, in the one method that builds every row. */
private const val CLEAR_MODE_ROW = "fds_control_clear_mode"

/** The id of Facebook's own download row, offered only on a video you posted. Its icon is borrowed. */
private const val OWN_DOWNLOAD_ROW = "fds_control_download_video"

/** The id the added row carries, where Facebook's own rows carry theirs. */
private const val ROW_ID = "download_reel"

private const val LABEL = "Download"

/** The helper this patch adds to the row builder's own class. */
private const val HELPER = "andrewDownloadRow"

/**
 * The More sheet's row builder, as this patch finds it.
 *
 * Every row of the sheet (Clear mode, Captions, Audio description) is added by one static method,
 * handed the sheet's row list. That one method also looks up the reel's player state by its player
 * origin and video id, and builds its own row as `new Row(click, icon, Boolean, title, subtitle,
 * id, Integer)`. Every name the Download row needs comes from that one method; nothing obfuscated
 * is written down.
 */
private class MoreSheet(
    val builder: MutableMethod,
    val lookup: MethodReference,
    val lookupInvoke: Opcode,
    val row: MethodReference,
    val click: ClassDef,
    val clickMethod: String,
    val iconWrap: MethodReference,
    val downloadIcon: FieldReference,
)

private fun BytecodePatchContext.moreSheet(): MoreSheet {
    val hits = mutableListOf<Pair<String, String>>()

    classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (holds(method, CLEAR_MODE_ROW)) hits += classDef.type to method.name
        }
    }

    check(hits.size == 1) {
        "Expected 1 More sheet row builder, found ${hits.size}: " +
            hits.joinToString { "${it.first}->${it.second}" }
    }

    val (builderClass, builderName) = hits.single()
    val builderDef = mutableClassDefBy(builderClass)
    val builder = builderDef.methods.single { it.name == builderName }

    check(AccessFlags.STATIC.isSet(builder.accessFlags) && builder.returnType == "V") {
        "$builderClass->$builderName isn't a static method adding a row"
    }

    val instructions = builder.instructions()
    val parameters = builder.parameterTypes.map(CharSequence::toString)

    // The player state: looked up on one of the builder's own parameters by origin and video id.
    val lookupCall = instructions.firstNotNullOfOrNull { instruction ->
        val reference = instruction.methodReference() ?: return@firstNotNullOfOrNull null
        val types = reference.parameterTypes.map(CharSequence::toString)
        instruction.takeIf {
            types == listOf(PLAYER_ORIGIN, STRING) && reference.definingClass in parameters
        }
    }

    check(lookupCall != null) {
        "$builderClass->$builderName: no (PlayerOrigin, String) lookup on one of its own parameters"
    }

    val lookup = lookupCall.methodReference()!!
    val lookupInvoke = lookupCall.opcode

    // The row: the constructor called right after the Clear mode row's id, taking a CharSequence
    // (its title).
    val idAt = instructions.indexOfFirst { it.stringReference() == CLEAR_MODE_ROW }

    check(idAt >= 0) { "$builderClass->$builderName no longer holds \"$CLEAR_MODE_ROW\"" }

    val row = instructions.drop(idAt).firstNotNullOfOrNull { instruction ->
        instruction.methodReference()?.takeIf {
            it.name == "<init>" && it.parameterTypes.any { type -> type.toString() == CHAR_SEQUENCE }
        }
    }

    check(row != null) { "$builderClass->$builderName: no row is built after \"$CLEAR_MODE_ROW\"" }

    val rowParameters = row.parameterTypes.map(CharSequence::toString)

    // The click: the row's one parameter that is a public interface with a single no-arg void
    // method. The handler is added from outside Facebook's package, so a package-private interface
    // would fail to load with it.
    val click = rowParameters.firstNotNullOfOrNull { type ->
        mutableClassDefByOrNull(type)?.takeIf(::isClickInterface)
    }

    check(click != null) { "The row ${row.definingClass} takes no public click interface" }

    // The icon: a static method wrapping an enum constant into one of the row's own parameter types.
    val iconWrap = instructions.firstNotNullOfOrNull { instruction ->
        instruction.methodReference()?.takeIf { reference ->
            reference.returnType.toString() in rowParameters &&
                reference.parameterTypes.size == 1 &&
                mutableClassDefByOrNull(reference.parameterTypes.single().toString())?.superclass == "Ljava/lang/Enum;"
        }
    }

    check(iconWrap != null) { "No icon wrapper is built for the More sheet's rows" }

    val iconEnumType = iconWrap.parameterTypes.single().toString()

    // Facebook's own per-video download row names the icon to borrow.
    var downloadIcon: FieldReference? = null

    classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (!holds(method, OWN_DOWNLOAD_ROW)) return@forEach
            method.instructions().forEach { instruction ->
                val field = instruction.fieldReference() ?: return@forEach
                if (instruction.opcode == Opcode.SGET_OBJECT && field.type.toString() == iconEnumType) {
                    if (downloadIcon == null) downloadIcon = field
                }
            }
        }
    }

    check(downloadIcon != null) { "Facebook's \"$OWN_DOWNLOAD_ROW\" row draws no icon of $iconEnumType" }

    return MoreSheet(
        builder = builder,
        lookup = lookup,
        lookupInvoke = lookupInvoke,
        row = row,
        click = click,
        clickMethod = click.methods.single().name,
        iconWrap = iconWrap,
        downloadIcon = downloadIcon!!,
    )
}

private fun isClickInterface(classDef: ClassDef) =
    AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
        AccessFlags.PUBLIC.isSet(classDef.accessFlags) &&
        classDef.methods.count() == 1 &&
        classDef.methods.single().let { it.parameterTypes.isEmpty() && it.returnType == "V" }

/**
 * Makes [handlerType] implement [sheet]'s click interface, its one method calling the handler's
 * own `run()`. The interface itself is obfuscated and only known at patch time, so this adds it to
 * an already-compiled extension class with dexlib2 rather than writing it at compile time.
 */
private fun BytecodePatchContext.implementClick(sheet: MoreSheet, handlerType: String) {
    val handler = mutableClassDefBy(handlerType)

    if (sheet.click.type !in handler.interfaces) handler.interfaces.add(sheet.click.type)

    if (handler.methods.none { it.name == sheet.clickMethod && it.parameterTypes.isEmpty() }) {
        handler.methods.add(
            ImmutableMethod(
                handlerType,
                sheet.clickMethod,
                emptyList(),
                "V",
                AccessFlags.PUBLIC.value,
                null,
                null,
                MutableMethodImplementation(1),
            ).toMutable().apply {
                addInstructions(0, "invoke-virtual { p0 }, $handlerType->run()V\nreturn-void")
            },
        )
    }
}

/**
 * Adds a Download row to every reel's More sheet.
 *
 * Facebook has a download row and builds it only for a video that you posted. Ownership chooses
 * the whole viewer rather than a flag inside one, so for anybody else's reel the row is never
 * built and there is nothing to force. `docs/facebook-ads-map.md` records the rounds that
 * established this, and why the sidebar (every reel's action strip) was tried first and abandoned:
 * its builder method is register-starved on 584 and has no spare register left to carry a value
 * from the lookup through to the click handler's construction. The More sheet's row builder is a
 * much smaller method with no such constraint, and already looks up the reel's player state for
 * its own rows (Clear mode, Captions), which this patch reuses rather than re-deriving.
 *
 * The handler holds the player state the lookup answered for this one reel, so the file saved is
 * the reel on screen, never a shared or stale value.
 */
@Suppress("unused")
val downloadReelPatch = bytecodePatch(
    name = "[Reels] Download any reel",
    description = "Adds a Download option to a reel's \"More\" menu. Videos save at the best " +
        "quality the player streams.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)
    extendWith("extensions/extension.mpe")

    val saveAsH264 = saveAsH264Option()

    execute {
        if (saveAsH264.value == true) enableSaveAsH264("reelsAsH264")

        // ---- the real names of the address fields ---------------------------------------------
        val names = reportedFieldNames(VIDEO_DATA_SOURCE, marker = "videoHdUri")
        val hdField = names["videoHdUri"]
        val sdField = names["videoUri"]
        val manifestField = names["abrManifestContent"]

        check(hdField != null && sdField != null && manifestField != null) {
            "Expected videoHdUri, videoUri and abrManifestContent on the source, found ${names.keys}"
        }

        // ---- the More sheet ---------------------------------------------------------------------
        val sheet = moreSheet()
        val builder = sheet.builder
        val parameters = builder.parameterTypes.map(CharSequence::toString)

        fun parameter(type: String, what: String): Int {
            val matches = parameters.indices.filter { parameters[it] == type }
            check(matches.size == 1) {
                "Expected 1 $what parameter of ${builder.definingClass}->${builder.name}, found ${matches.size}"
            }
            return matches.single()
        }

        val store = parameter(sheet.lookup.definingClass, "player state store")
        val origin = parameter(PLAYER_ORIGIN, "PlayerOrigin")
        val id = parameter(STRING, "video id")
        val list = parameter(LIST, "row list")

        // The scoped context: the one parameter of the builder whose class declares exactly one
        // field of type Context. Found by shape, not by an obfuscated name that changes release to
        // release.
        val scopedCandidates = parameters.indices.filter { index ->
            mutableClassDefByOrNull(parameters[index])?.fields
                ?.count { it.type.toString() == CONTEXT } == 1
        }

        check(scopedCandidates.size == 1) {
            "Expected 1 parameter of ${builder.definingClass}->${builder.name} with exactly one " +
                "Context field, found ${scopedCandidates.size}"
        }

        val scoped = scopedCandidates.single()
        val scopedType = parameters[scoped]
        val contextField = mutableClassDefBy(scopedType).fields.single { it.type.toString() == CONTEXT }

        implementClick(sheet, HANDLER)

        // ---- the helper -------------------------------------------------------------------------
        //
        // Its own method, with its own fresh registers, so nothing it does can collide with a
        // register the builder's own code still needs.
        val rowParameters = sheet.row.parameterTypes.map(CharSequence::toString)
        val helperParameters = listOf(sheet.lookup.definingClass, PLAYER_ORIGIN, STRING, scopedType, LIST)

        val lookupInvokeMnemonic = when (sheet.lookupInvoke) {
            Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE -> "invoke-virtual"
            Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE -> "invoke-interface"
            else -> throw IllegalStateException("The player state lookup is called with ${sheet.lookupInvoke.name}")
        }

        val helper = ImmutableMethod(
            builder.definingClass,
            HELPER,
            helperParameters.map { ImmutableMethodParameter(it, null, null) },
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            MutableMethodImplementation(64),
        ).toMutable().apply {
            addInstructions(
                0,
                buildRow(
                    sheet = sheet,
                    lookupInvoke = lookupInvokeMnemonic,
                    rowParameters = rowParameters,
                    scopedType = scopedType,
                    contextField = contextField.name,
                    hdField = hdField,
                    sdField = sdField,
                    manifestField = manifestField,
                ),
            )
        }

        mutableClassDefBy(builder.definingClass).methods.add(helper)

        // ---- the injection ----------------------------------------------------------------------
        //
        // First thing in the builder, before its own checks. Dalvik locals are unwritten before a
        // method's first instruction runs, so v0 to v4 are free here whatever the builder does
        // later with those same register numbers as locals of its own. The five parameters are not
        // consecutive in the builder's own parameter list, so each is copied down individually
        // rather than passed as one range.
        builder.addInstructions(
            0,
            """
                move-object/from16 v0, p$store
                move-object/from16 v1, p$origin
                move-object/from16 v2, p$id
                move-object/from16 v3, p$scoped
                move-object/from16 v4, p$list
                invoke-static/range { v0 .. v4 }, ${builder.definingClass}->$HELPER(${helperParameters.joinToString("")})V
            """,
        )
    }
}

/**
 * The body of the helper that builds and adds one row.
 *
 * `new-instance` and `const-string` take 8-bit registers, so the row's own constructor arguments
 * can sit high, from v30 up, clear of everything this method does before it.
 */
private fun buildRow(
    sheet: MoreSheet,
    lookupInvoke: String,
    rowParameters: List<String>,
    scopedType: String,
    contextField: String,
    hdField: String,
    sdField: String,
    manifestField: String,
): String {
    val iconEnumType = sheet.iconWrap.parameterTypes.single().toString()
    val signature = "${sheet.row.definingClass}-><init>" +
        rowParameters.joinToString("", "(", ")") + "V"

    // This method's own parameters sit at the top of a large register file (the row's constructor
    // needs a wide, clear block from v30 up), so any parameter past v15 cannot be named directly in
    // a non-range invoke — only `/range` forms and `move-object/from16` (whose source register is
    // 16-bit) can reach it. Every call below is written to respect that.
    return """
        if-eqz p1, :done
        $lookupInvoke/range { p0 .. p2 }, ${sheet.lookup.definingClass}->${sheet.lookup.name}($PLAYER_ORIGIN$STRING)${sheet.lookup.returnType}
        move-result-object v7
        if-eqz v7, :done

        move-object/from16 v1, p3
        iget-object v8, v1, $scopedType->$contextField:$CONTEXT

        const-string v9, "$hdField"
        const-string v10, "$sdField"
        const-string v11, "$manifestField"

        new-instance v6, $HANDLER
        invoke-direct/range { v6 .. v11 }, $HANDLER-><init>(Ljava/lang/Object;Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

        sget-object v12, ${sheet.downloadIcon.definingClass}->${sheet.downloadIcon.name}:$iconEnumType
        invoke-static { v12 }, ${sheet.iconWrap.definingClass}->${sheet.iconWrap.name}($iconEnumType)${sheet.iconWrap.returnType}
        move-result-object v13

        new-instance v30, ${sheet.row.definingClass}
${rowArguments(rowParameters, sheet.click.type, sheet.iconWrap.returnType.toString())}
        invoke-direct/range { v30 .. v${30 + rowParameters.size} }, $signature

        move-object/from16 v4, p4
        move-object/from16 v5, v30
        invoke-interface { v4, v5 }, $LIST->add($OBJECT)Z
        :done
        return-void
    """
}

/**
 * The moves that fill the row's own constructor arguments from v31 up: the handler (v6) wherever
 * the row takes the click interface, the wrapped icon (v13) wherever it takes the icon type, the
 * label as the first CharSequence (the title) and the row id as the last CharSequence if there is
 * more than one, the place Facebook's own rows carry theirs. Everything else is left null: this
 * patch does not know what a subtitle or a weight should be, and the row draws without them.
 */
private fun rowArguments(rowParameters: List<String>, click: String, icon: String): String {
    val title = rowParameters.indexOf(CHAR_SEQUENCE)
    val idAt = rowParameters.lastIndexOf(CHAR_SEQUENCE).takeIf { it != title }

    return rowParameters.mapIndexed { index, type ->
        val register = 31 + index
        when {
            type == click -> "move-object/from16 v$register, v6"
            type == icon -> "move-object/from16 v$register, v13"
            index == title -> "const-string v$register, \"$LABEL\""
            index == idAt -> "const-string v$register, \"$ROW_ID\""
            else -> "const/16 v$register, 0x0"
        }
    }.joinToString("\n")
}

private fun holds(method: Method, string: String) =
    method.implementation?.instructions?.any { it.stringReference() == string } == true

private fun Instruction.methodReference() =
    (this as? ReferenceInstruction)?.reference as? MethodReference

private fun Instruction.fieldReference() =
    (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.stringReference() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

private fun MutableMethod.instructions(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

private fun Method.instructions(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

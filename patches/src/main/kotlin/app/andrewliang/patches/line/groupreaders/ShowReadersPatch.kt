package app.andrewliang.patches.line.groupreaders

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION = "Lapp/andrewliang/extension/GroupReaders;"
private const val VIEW = "Landroid/view/View;"
private const val CONTEXT = "Landroid/content/Context;"
private const val STRING = "Ljava/lang/String;"
private const val OBJECT = "Ljava/lang/Object;"

/** An eye in the stroke style of LINE's own menu icons (`chat_ui_context_note`). */
private val showReadersResourcePatch = resourcePatch {
    execute {
        get("res/drawable/andrew_ic_readers.xml").writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="26dp" android:height="26dp"
                    android:viewportWidth="26" android:viewportHeight="26">
                    <path android:fillColor="#00000000" android:strokeColor="#ffffff" android:strokeWidth="1.35"
                        android:pathData="M3,13C5.8,8.3 9.2,6 13,6C16.8,6 20.2,8.3 23,13C20.2,17.7 16.8,20 13,20C9.2,20 5.8,17.7 3,13Z" />
                    <path android:fillColor="#00000000" android:strokeColor="#ffffff" android:strokeWidth="1.35"
                        android:pathData="M13,9.75A3.25,3.25 0,1 1,13 16.25A3.25,3.25 0,1 1,13 9.75Z" />
                </vector>
            """.trimIndent(),
        )
    }
}

@Suppress("unused")
val showReadersPatch = bytecodePatch(
    name = "[Chat] Show who read messages",
    description = "Adds \"Read by\" to the long-press menu of a message in groups. It lists the members " +
        "who read the message.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch, lineSettingsExtensionPatch, showReadersResourcePatch)

    // LINE keeps, for each group member, the range of messages that the member read. It counts that
    // range for its own "Read N" label. The extension reads the same table on a worker thread.
    // This patch only gives the extension the message of the menu and LINE's own read-range sync
    // for one chat.
    execute {
        val extension = mutableClassDefBy(EXTENSION)

        // The message of the menu: params -> message view data -> common view data.
        val build = ContextMenuBuildFingerprint.method
        val paramsType = build.parameterTypes.getOrNull(3)?.toString()
            ?: throw PatchException("Menu builder has no params argument")
        val viewData = printedField(toStringOf(paramsType), ", messageViewData=", null)
        val common = printedField(toStringOf(viewData.type), ", chatMessageCommonViewData=", null)
        val messageToString = MessageCommonToStringFingerprint.method
        if (common.type != messageToString.definingClass) {
            throw PatchException("Menu message data is ${common.type}, not ${messageToString.definingClass}")
        }
        val chatId = printedField(messageToString, "ChatMessageCommonViewData(chatId=", STRING)
        val serverId = printedField(messageToString, ", serverMessageId=", "J")
        val sender = printedField(messageToString, ", senderMid=", STRING)

        // In the builder: `getClass(grid)`, `sget-object <Set>`, then LINE colors the grid with
        // `iface.(View, Set, …)Z`. The hook goes before the `sget-object`, so the new cell is in the
        // grid when LINE colors it and gets the same colors.
        val code = build.implementation!!.instructions.toList()
        val colorCall = code.indexOfFirst { instruction ->
            instruction.opcode == Opcode.INVOKE_INTERFACE && instruction.methodRef().let {
                it.returnType == "Z" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == VIEW && it.parameterTypes[1] == "Ljava/util/Set;"
            }
        }
        if (colorCall < 1) throw PatchException("Menu color call not found")
        val setRead = code[colorCall - 1]
        if (setRead.opcode != Opcode.SGET_OBJECT || (setRead.fieldRef().type != "Ljava/util/Set;")) {
            throw PatchException("No Set read before the menu color call")
        }
        val grid = (code[colorCall] as FiveRegisterInstruction).registerD
        // The menu dialog: the next field read after the color call reads it. R8 reuses the
        // register of the params argument, so the hook reads the params from the dialog, which
        // keeps them in a field.
        val menuRead = code.drop(colorCall + 1).first { it.opcode == Opcode.IGET_OBJECT } as TwoRegisterInstruction
        val menuType = (menuRead as ReferenceInstruction).fieldRef().definingClass
        val menuClass = classDefBy(menuType)
        if (menuClass.interfaces.none { it == "Landroid/content/DialogInterface;" }) {
            throw PatchException("$menuType is not the menu dialog")
        }
        val params = menuClass.fields.singleOrNull { it.type == paramsType }
            ?: throw PatchException("$menuType has no single $paramsType field")
        val menu = menuRead.registerB
        if (grid > 15 || menu > 15) throw PatchException("Menu registers v$grid v$menu")

        // LINE's service locator `(Context, key) -> Object`. The builder calls it just before
        // the color call.
        val locator = code.subList(0, colorCall).last { instruction ->
            instruction.opcode == Opcode.INVOKE_STATIC && instruction.methodRef().let {
                it.returnType == OBJECT && it.parameterTypes.size == 2 && it.parameterTypes[0] == CONTEXT
            }
        }.methodRef()

        extension.addMethod(
            "menuBuilt",
            listOf(VIEW, OBJECT),
            "V",
            registers = 8,
            """
                move-object v0, p0
                move-object v1, p1
                check-cast p1, $menuType
                iget-object p1, p1, $menuType->${params.name}:$paramsType
                if-eqz p1, :none
                iget-object p1, p1, $viewData
                if-eqz p1, :none
                iget-object p1, p1, $common
                if-eqz p1, :none
                iget-object v2, p1, $chatId
                iget-wide v3, p1, $serverId
                iget-object v5, p1, $sender
                invoke-static/range { v0 .. v5 }, $EXTENSION->addCell($VIEW${OBJECT}${STRING}J$STRING)V
                :none
                return-void
            """,
        )
        build.addInstructions(
            colorCall - 1,
            "invoke-static { v$grid, v$menu }, $EXTENSION->menuBuilt($VIEW$OBJECT)V",
        )

        // LINE's read-range sync for one chat: the same call that its periodic sync makes for every
        // chat, with a one-chat set. It returns the error, or null.
        val syncMethod = ReadRangeSyncFingerprint.method
        val sync = readRangeSyncCall(syncMethod)!!
        val syncCode = syncMethod.implementation!!.instructions
        val database = syncCode.first {
            it.opcode == Opcode.IGET_OBJECT && it.fieldRef().type == "Landroid/database/sqlite/SQLiteDatabase;"
        }.fieldRef()
        val manager = database.definingClass
        val syncer = classDefBy(manager).fields.singleOrNull { it.type == sync.definingClass }
            ?: throw PatchException("$manager has no single ${sync.definingClass} field")
        val reason = sync.parameterTypes[2].toString()
        val managerHolder = holderOf { it.parameterTypes.isEmpty() && it.returnType == manager }
        extension.replaceStub(
            "syncReadRange",
            listOf(CONTEXT, STRING),
            OBJECT,
            registers = 7,
            """
                sget-object v0, ${managerHolder.key}
                invoke-static { p0, v0 }, $locator
                move-result-object v0
                check-cast v0, ${managerHolder.type}
                invoke-interface { v0 }, ${managerHolder.getter}
                move-result-object v0
                iget-object v1, v0, $manager->${syncer.name}:${syncer.type}
                iget-object v2, v0, $database
                invoke-static { p1 }, Ljava/util/Collections;->singleton(Ljava/lang/Object;)Ljava/util/Set;
                move-result-object v3
                sget-object v4, $reason->FULL_SYNC:$reason
                invoke-virtual { v1, v2, v3, v4 }, $sync
                move-result-object v0
                return-object v0
            """,
        )

        markLineSettingIncluded("showReaders")
    }
}

private fun Instruction.methodRef() = (this as ReferenceInstruction).reference as MethodReference

private fun Instruction.fieldRef() = (this as ReferenceInstruction).reference as FieldReference

private fun BytecodePatchContext.toStringOf(type: String): Method =
    classDefBy(type).methods.singleOrNull { it.name == "toString" && it.parameterTypes.isEmpty() }
        ?: throw PatchException("$type has no toString")

/**
 * The field that [toString] prints right after [marker], of type [wanted] (any object type if null).
 *
 * Kotlin data classes print `marker` then the field, either through `StringBuilder.append` (the
 * field is the argument of the next call) or through a shared helper that takes the markers and
 * the values in one call (the field is the argument right after the marker, or the only argument
 * of type [wanted]).
 */
private fun printedField(toString: Method, marker: String, wanted: String?): FieldReference {
    val strings = mutableMapOf<Int, String>()
    val fields = mutableMapOf<Int, FieldReference>()
    fun FieldReference?.fits() = this != null && (if (wanted == null) type.startsWith("L") else type == wanted)
    var markerWasLast = false
    for (instruction in toString.implementation!!.instructions) {
        val reference = (instruction as? ReferenceInstruction)?.reference
        when {
            reference is StringReference -> {
                val register = (instruction as OneRegisterInstruction).registerA
                strings[register] = reference.string
                fields.remove(register)
            }
            // An instance field read (iget); a static read (sget) has one register.
            reference is FieldReference && instruction is TwoRegisterInstruction -> {
                val register = instruction.registerA
                fields[register] = reference
                strings.remove(register)
            }
            reference is MethodReference -> {
                val registers = instruction.argumentRegisters()
                if (markerWasLast) {
                    val field = fields[registers.last()]
                    if (field.fits()) return field!!
                    markerWasLast = false
                }
                val at = registers.indexOfFirst { strings[it] == marker }
                if (at < 0) continue
                val next = registers.getOrNull(at + 1)?.let(fields::get)
                if (next.fits()) return next!!
                val only = registers.mapNotNull(fields::get).filter { it.fits() }.distinct()
                if (only.size == 1) return only.single()
                markerWasLast = at == registers.lastIndex
            }
        }
    }
    throw PatchException("${toString.definingClass} prints no $wanted field after \"$marker\"")
}

private fun Instruction.argumentRegisters(): List<Int> = when (this) {
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    is FiveRegisterInstruction ->
        listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    else -> emptyList()
}

/** A LINE service interface, the static key to look it up with, and its getter. */
private class Holder(val type: String, val key: String, val getter: String)

/**
 * The service interface with an abstract getter that [isGetter] accepts. LINE looks such an
 * interface up with the service locator and its one static key field.
 */
private fun BytecodePatchContext.holderOf(isGetter: (Method) -> Boolean): Holder {
    val found = mutableListOf<Holder>()
    classDefForEach { classDef: ClassDef ->
        if (classDef.accessFlags and AccessFlags.INTERFACE.value == 0) return@classDefForEach
        val getter = classDef.methods.singleOrNull { it.implementation == null && isGetter(it) } ?: return@classDefForEach
        val key = classDef.fields.singleOrNull { it.accessFlags and AccessFlags.STATIC.value != 0 } ?: return@classDefForEach
        found += Holder(
            classDef.type,
            "${classDef.type}->${key.name}:${key.type}",
            "${classDef.type}->${getter.name}()${getter.returnType}",
        )
    }
    return found.singleOrNull() ?: throw PatchException("Expected one service interface, found ${found.map { it.type }}")
}

/** Adds a public static method that runs [smali]. */
private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.addMethod(
    name: String,
    parameters: List<String>,
    returnType: String,
    registers: Int,
    smali: String,
) {
    val method = MutableMethod(
        ImmutableMethod(
            type,
            name,
            parameters.map { ImmutableMethodParameter(it, null, null) },
            returnType,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            MutableMethodImplementation(registers),
        ),
    )
    methods.add(method)
    method.addInstructions(0, smali)
}

/** Replaces the extension stub [name] with a method that runs [smali]. */
private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.replaceStub(
    name: String,
    parameters: List<String>,
    returnType: String,
    registers: Int,
    smali: String,
) {
    if (!methods.removeIf { it.name == name }) throw PatchException("No stub $name in $type")
    addMethod(name, parameters, returnType, registers, smali)
}

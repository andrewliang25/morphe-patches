package app.andrewliang.patches.line.settings

import app.andrewliang.patches.line.shared.LINE_SETTINGS
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.w3c.dom.Element

private const val CLICK = "Lapp/andrewliang/extension/LineSettingsClick;"

private const val ROW_KEY = "andrew-patch-settings"

private const val ROWS_METHOD = "andrewRows"
private const val ROWS_DESC = "(Ljava/util/List;)Ljava/util/List;"

/**
 * Adds the row title and the row icon. The extension finds their ids by name, because ids are
 * assigned after the bytecode patches.
 *
 * The icon is LINE's own header gear (`navi_top_setting`, 38dp, black) made to match the 22dp
 * Settings row icons: the patch copies its path into a 22dp vector, moves it so the gear fills the
 * box, and colors it with the color of the other row icons, which follows LINE's light and dark
 * themes.
 */
private val andrewPatchSettingResourcePatch = resourcePatch {
    execute {
        val gear = document("res/drawable/navi_top_setting.xml").use { document ->
            (document.getElementsByTagName("path").item(0) as Element).getAttribute("android:pathData")
        }
        if (gear.isEmpty()) throw PatchException("LINE's navi_top_setting has no path")
        get("res/drawable/andrew_ic_settings.xml").writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="22dp" android:height="22dp"
                    android:viewportWidth="22" android:viewportHeight="22">
                    <group android:translateX="-8" android:translateY="-8">
                        <path android:fillColor="@color/octonaryAltNeutralFill" android:pathData="$gear" />
                    </group>
                </vector>
            """.trimIndent(),
        )

        document("res/values/strings.xml").use { document ->
            val resources = document.getElementsByTagName("resources").item(0) as Element
            resources.appendChild(
                document.createElement("string").apply {
                    setAttribute("name", "andrew_patch_settings")
                    textContent = "Andrew\\'s Patch Setting"
                },
            )
        }
    }
}

@Suppress("unused")
val andrewPatchSettingPatch = bytecodePatch(
    name = "[General] Andrew's Patch Setting",
    description = "Adds \"Andrew's Patch Setting\" to LINE Settings, below \"Profile\". There you can " +
        "turn some patches on or off without patching again, and see the credits and licenses.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(andrewPatchSettingResourcePatch, lineSettingsExtensionPatch)

    // LINE builds its main Settings list once, in a static initializer, as a list of row objects.
    // The list getter only returns that static list. Thus the patch gives the getter a new method
    // that copies the list and puts one more row below "Profile".
    //
    // The new row is built like the "About LINE" row and takes that row's other values, such as
    // its subtitle, from the live "About LINE" row. Thus no row value has to be made up. Every
    // obfuscated name is read from the code that builds "About LINE".
    execute {
        val builder = SettingsRowsFingerprint.method
        val instructions = builder.implementation!!.instructions.toList()
        val aboutIndex = SettingsRowsFingerprint.instructionMatches[1].index

        // The key of "About LINE" is built at run time ("line-main-settings." + its name). Thus the
        // new method gets it the same way: the `AboutLine` constant, then the key accessor that
        // the builder calls on it.
        val aboutConstantIndex = SettingsRowsFingerprint.instructionMatches[0].index
        val aboutConstant = (instructions[aboutConstantIndex] as ReferenceInstruction).reference as FieldReference
        val keyAccessor = (aboutConstantIndex + 1 until aboutIndex).firstNotNullOf { index ->
            ((instructions[index] as? ReferenceInstruction)?.reference as? MethodReference)?.takeIf {
                instructions[index].opcode == Opcode.INVOKE_VIRTUAL && it.definingClass == aboutConstant.type &&
                    it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
            }
        }

        // The row goes below "Profile". Its key is built the same way, from the `Profile` constant
        // of the same enum.
        val profileConstant = "${aboutConstant.type}->Profile:${aboutConstant.type}"
        classDefBy(aboutConstant.type).fields.singleOrNull { it.name == "Profile" }
            ?: throw PatchException("No Profile constant in ${aboutConstant.type}")

        // The "About LINE" row constructor: the first <init> range call after the event that
        // takes the event type and ends with the default mask (`I`). The number of its arguments
        // changes between LINE versions, so each argument is found by its role.
        val event = (instructions[aboutIndex] as ReferenceInstruction).reference as FieldReference
        val ctorIndex = (aboutIndex until instructions.size).first { index ->
            val call = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            instructions[index].opcode == Opcode.INVOKE_DIRECT_RANGE && call?.name == "<init>" &&
                call.parameterTypes.any { it.toString() == event.type } &&
                call.parameterTypes.last().toString() == "I"
        }
        val rowCtor = (instructions[ctorIndex] as ReferenceInstruction).reference as MethodReference
        val rowClass = rowCtor.definingClass
        val types = rowCtor.parameterTypes.map { it.toString() }

        // The tap destination of "About LINE": a one-argument destination class around a
        // three-argument function. The destination argument is the one typed as its base class,
        // and the click function comes just before it.
        val destinationCtor = (aboutIndex until ctorIndex).firstNotNullOf { index ->
            val call = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            call?.takeIf {
                instructions[index].opcode == Opcode.INVOKE_DIRECT && it.name == "<init>" &&
                    it.parameterTypes.size == 1 &&
                    classDefBy(it.definingClass).superclass in types
            }
        }
        val function3 = destinationCtor.parameterTypes.single().toString()
        val destinationIndex = types.indexOf(classDefBy(destinationCtor.definingClass).superclass)
        val clickIndex = destinationIndex - 1
        val function1 = types[clickIndex]
        val eventIndex = types.indexOf(event.type)
        val maskIndex = types.lastIndex

        // The default mask of "About LINE": the literal in the last register of the call. The new
        // row passes a null event, which is the value the mask gives a defaulted event.
        val range = instructions[ctorIndex] as RegisterRangeInstruction
        val maskRegister = range.startRegister + range.registerCount - 1
        val mask = (ctorIndex - 1 downTo aboutIndex).firstNotNullOf { index ->
            val instruction = instructions[index]
            (instruction as? NarrowLiteralInstruction)?.narrowLiteral
                ?.takeIf { (instruction as OneRegisterInstruction).registerA == maskRegister }
        }

        // The field where the row keeps its key.
        val keyField = fieldOfArgument(rowCtor, 0)

        // The new row: v4 = the row, v5 .. are its arguments. Each argument that has no role of
        // its own (the subtitle, the providers, the analytics function) is copied from the field
        // where "About LINE" keeps it. v3 holds "About LINE".
        val argumentSmali = types.indices.joinToString("\n") { index ->
            val register = 5 + index
            when (index) {
                0 -> "const-string v$register, \"$ROW_KEY\""
                1 -> """
                    invoke-static {}, $LINE_SETTINGS->iconId()Ljava/lang/Integer;
                    move-result-object v$register
                """
                2 -> "" // v7 already holds the title id.
                eventIndex -> "const/4 v$register, 0x0"
                clickIndex -> """
                    new-instance v$register, $CLICK
                    invoke-direct {v$register}, $CLICK-><init>()V
                """
                destinationIndex -> """
                    new-instance v$register, ${destinationCtor.definingClass}
                    invoke-direct {v$register, v${5 + clickIndex}}, ${destinationCtor.definingClass}-><init>($function3)V
                """
                maskIndex -> "const v$register, $mask"
                else -> {
                    val field = fieldOfArgument(rowCtor, index)
                    if (register > 15) throw PatchException("Row argument $index needs register v$register")
                    when (field.type) {
                        "I", "Z" -> "iget v$register, v3, $field"
                        "J", "D" -> throw PatchException("Wide row argument $index is not supported")
                        types[index] -> "iget-object v$register, v3, $field"
                        // The field has another type than the argument: it holds the default
                        // that the constructor builds when the mask marks the argument unused.
                        // "About LINE" leaves such an argument unset, so pass null.
                        else -> "const/4 v$register, 0x0"
                    }
                }
            }
        }
        val lastRegister = 5 + types.lastIndex
        if (5 + destinationIndex > 15 || 5 + clickIndex > 15) {
            throw PatchException("The tap destination needs a register above v15")
        }

        // The click handler is an extension class. Add the two function interfaces of LINE and a
        // forwarding method for the three-argument interface, because R8 renames that method.
        val function3Method = classDefBy(function3).methods.single().name
        val clickClass = mutableClassDefBy(CLICK)
        clickClass.interfaces.apply {
            add(function1)
            add(function3)
        }
        if (clickClass.methods.none { it.name == function3Method && it.parameterTypes.size == 3 }) {
            val objectParam = ImmutableMethodParameter("Ljava/lang/Object;", null, null)
            val bridge = MutableMethod(
                ImmutableMethod(
                    CLICK,
                    function3Method,
                    listOf(objectParam, objectParam, objectParam),
                    "Ljava/lang/Object;",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(4),
                ),
            )
            bridge.addInstructions(
                0,
                """
                    invoke-virtual {p0, p1, p2, p3}, $CLICK->invoke(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p0
                    return-object p0
                """,
            )
            clickClass.methods.add(bridge)
        }

        val rowsClass = builder.definingClass
        val rows = MutableMethod(
            ImmutableMethod(
                rowsClass,
                ROWS_METHOD,
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(lastRegister + 2),
            ),
        )
        mutableClassDefBy(rowsClass).methods.add(rows)
        // v0 = rows, v1 = index, v2 = size, v3 = "About LINE", v5 = its key, v7 = the title id.
        // v4 .. lastRegister are the constructor call. Then a second pass finds "Profile"
        // (v3 = row, v5 = its key) and inserts the new row (v4) below it. Profile is a different row class, so that pass
        // compares on the base row class.
        rows.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                sget-object v5, $aboutConstant
                invoke-virtual {v5}, $keyAccessor
                move-result-object v5
                invoke-interface {v0}, Ljava/util/List;->size()I
                move-result v2
                const/4 v1, 0x0
                :loop
                if-ge v1, v2, :done
                invoke-interface {v0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
                move-result-object v3
                instance-of v4, v3, $rowClass
                if-eqz v4, :next
                check-cast v3, $rowClass
                iget-object v4, v3, $keyField
                invoke-virtual {v5, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v4
                if-nez v4, :found
                :next
                add-int/lit8 v1, v1, 0x1
                goto :loop
                :found
                invoke-static {}, $LINE_SETTINGS->titleId()I
                move-result v7
                if-eqz v7, :done
                new-instance v4, $rowClass
            """ + argumentSmali + """
                invoke-direct/range {v4 .. v$lastRegister}, $rowClass-><init>(${types.joinToString("")})V
                sget-object v5, $profileConstant
                invoke-virtual {v5}, $keyAccessor
                move-result-object v5
                const/4 v1, 0x0
                :profile
                if-ge v1, v2, :insert
                invoke-interface {v0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
                move-result-object v3
                instance-of v6, v3, ${keyField.definingClass}
                if-eqz v6, :nextProfile
                check-cast v3, ${keyField.definingClass}
                iget-object v6, v3, $keyField
                invoke-virtual {v5, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v6
                if-nez v6, :insert
                :nextProfile
                add-int/lit8 v1, v1, 0x1
                goto :profile
                :insert
                invoke-static {v0, v1, v4}, $LINE_SETTINGS->insertRow(Ljava/util/List;ILjava/lang/Object;)Ljava/util/List;
                move-result-object v0
                :done
                return-object v0
            """,
        )

        // The list getter: `sget-object p0, <rows>` then `return-object p0`. The call goes
        // before the return, and has no branch.
        val getter = mutableClassDefBy(rowsClass).methods.single { method ->
            method.returnType == "Ljava/util/List;" && method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.map { it.opcode } ==
                listOf(Opcode.SGET_OBJECT, Opcode.RETURN_OBJECT)
        }
        val listRegister = (getter.implementation!!.instructions.first() as OneRegisterInstruction).registerA
        getter.addInstructions(
            1,
            """
                invoke-static {v$listRegister}, $rowsClass->$ROWS_METHOD$ROWS_DESC
                move-result-object v$listRegister
            """,
        )

        markIncluded("rowIncluded")
    }
}

/**
 * The field where the constructor [ctor] stores its argument [argument] (0 = the first argument
 * after `this`). It follows the argument through register moves and through a call to another
 * constructor of the same object, because LINE's row constructors hand most of their arguments to
 * the base row class.
 */
private fun BytecodePatchContext.fieldOfArgument(ctor: MethodReference, argument: Int): FieldReference {
    val method = classDefBy(ctor.definingClass).methods.single {
        it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == ctor.parameterTypes.map(CharSequence::toString)
    }
    val implementation = method.implementation!!
    val widths = ctor.parameterTypes.map { if (it.toString() == "J" || it.toString() == "D") 2 else 1 }
    val thisRegister = implementation.registerCount - widths.sum() - 1
    val self = mutableSetOf(thisRegister)
    val value = mutableSetOf(thisRegister + 1 + widths.take(argument).sum())

    for (instruction in implementation.instructions) {
        when (instruction.opcode) {
            Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
            Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
            -> {
                val move = instruction as TwoRegisterInstruction
                listOf(self, value).forEach { set ->
                    if (move.registerB in set) set += move.registerA else set -= move.registerA
                }
            }

            Opcode.IPUT_OBJECT, Opcode.IPUT -> {
                val put = instruction as TwoRegisterInstruction
                if (put.registerA in value && put.registerB in self) {
                    return (instruction as ReferenceInstruction).reference as FieldReference
                }
            }

            Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE -> {
                val call = (instruction as ReferenceInstruction).reference as MethodReference
                val registers = when (instruction) {
                    is RegisterRangeInstruction ->
                        (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
                    is FiveRegisterInstruction -> listOf(
                        instruction.registerC, instruction.registerD, instruction.registerE,
                        instruction.registerF, instruction.registerG,
                    ).take(instruction.registerCount)
                    else -> emptyList()
                }
                if (call.name == "<init>" && registers.firstOrNull() in self) {
                    // Map the register position back to an argument index of the called
                    // constructor. Wide arguments take two registers.
                    var position = 1
                    call.parameterTypes.forEachIndexed { index, type ->
                        if (registers.getOrNull(position) in value) return fieldOfArgument(call, index)
                        position += if (type.toString() == "J" || type.toString() == "D") 2 else 1
                    }
                }
            }

            else -> {
                // Any other write to a tracked register ends the trail for that register.
                val written = (instruction as? OneRegisterInstruction)?.registerA
                if (written != null && instruction.opcode.setsRegister()) {
                    self -= written
                    value -= written
                }
            }
        }
    }
    throw PatchException("No field of ${ctor.definingClass} takes argument $argument of its constructor")
}

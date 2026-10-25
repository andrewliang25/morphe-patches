package app.andrewliang.patches.facebook.amoledtheme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The four resolvers, which answer the first of the four routes that a colour takes.
 *
 * Facebook has two colour systems and they share no code. Mig is the older one, and each of its
 * ~120 getters ends in one method per colour scheme. FDS is the newer one and carries most of the
 * app: `FDSColors` answers Litho, `LX/1tK` answers the view code.
 *
 * R8 keeps the owner class names but renames the methods on every release. Thus each fingerprint
 * uses the class, the return type, the parameters and the shape of the body, and never a name.
 * 584 renamed `FDSColors` too, so it's found by its shape ([fdsColors]) instead of its old kept
 * name.
 *
 * See `docs/facebook-theme-map.md`.
 */

internal const val DARK_COLOR_SCHEME = "Lcom/facebook/mig/scheme/schemes/DarkColorScheme;"
internal const val FDS_COLOR_SCHEME = "Lcom/facebook/mig/scheme/schemes/fds/FdsColorScheme;"

/**
 * `FDSColors`, the class answering Litho's colour tokens. Redex kept its name up to 580
 * (`Lcom/facebook/fds/core/theme/component/FDSColors;`) and renamed it on 584, so it's found by
 * its shape ([fdsColourSource]), which hasn't moved. One class has it, or the lookup fails.
 */
internal fun BytecodePatchContext.fdsColors(): String {
    val found = mutableListOf<String>()
    classDefForEach { classDef -> if (fdsColourSource(classDef) != null) found += classDef.type }
    return found.singleOrNull() ?: throw PatchException(
        "Expected one FDSColors (a singleton whose static Integer colour source hands a token to its own switch), found $found",
    )
}

/**
 * The static `Integer` colour source of [classDef] when the class is `FDSColors`, else null. The
 * class holds a singleton of its own type, and the source takes a Context, a token and a palette
 * and hands the token on to a static method of the same class taking the token, an int and a
 * boolean.
 */
internal fun fdsColourSource(classDef: ClassDef): Method? {
    if (classDef.fields.none { AccessFlags.STATIC.isSet(it.accessFlags) && it.type == classDef.type }) return null
    return classDef.methods.singleOrNull { method ->
        val parameters = method.parameterTypes.map { it.toString() }
        AccessFlags.STATIC.isSet(method.accessFlags) && method.returnType == "Ljava/lang/Integer;" &&
            parameters.size == 3 && parameters[0] == "Landroid/content/Context;" &&
            method.implementation?.instructions?.any { instruction ->
                val call = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_STATIC && call?.definingClass == classDef.type &&
                    call.returnType == "Ljava/lang/Integer;" &&
                    call.parameterTypes.map { it.toString() } == listOf(parameters[1], "I", "Z")
            } == true
    }
}

/**
 * The Mig dark scheme resolver. The return type and the interface call remove the three sibling
 * methods. The light scheme has the same shape, thus the class is part of the fingerprint.
 */
internal object DarkSchemeResolveFingerprint : Fingerprint(
    definingClass = DARK_COLOR_SCHEME,
    returnType = "I",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "I"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.RETURN),
    ),
)

/**
 * The step to the resolver of the view code. That resolver has a Redex name and no literal, thus
 * the patch matches this wrapper and reads the descriptor of its `invoke-static`. The wrapper is
 * the only method that reads the one `Context` field.
 */
internal object FdsSchemeResolveFingerprint : Fingerprint(
    definingClass = FDS_COLOR_SCHEME,
    returnType = "I",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(definingClass = FDS_COLOR_SCHEME, type = "Landroid/content/Context;"),
        methodCall(opcode = Opcode.INVOKE_STATIC, returnType = "I"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.RETURN),
    ),
)

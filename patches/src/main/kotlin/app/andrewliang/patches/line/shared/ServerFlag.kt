package app.andrewliang.patches.line.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The parse of one boolean server flag. LINE reads each flag as `const-string "<key>"`, then a
 * call to the accessor of the flag (for its default value), then the map lookup. R8 renames the
 * accessor but keeps the key, so the key finds the accessor.
 */
internal open class ServerFlagFingerprint(key: String) : Fingerprint(
    filters = listOf(
        string(key),
        methodCall(returnType = "Z", parameters = listOf(), location = MatchAfterImmediately()),
    ),
)

/**
 * Makes the accessor that [fingerprint] found return false. Every reader of the flag then sees
 * the same value that LINE gets when the server turns the flag off.
 */
internal fun BytecodePatchContext.forceServerFlagFalse(fingerprint: ServerFlagFingerprint) {
    val call = fingerprint.instructionMatches[1].instruction as ReferenceInstruction
    val accessor = call.reference as MethodReference

    mutableClassDefBy(accessor.definingClass).methods.single {
        it.name == accessor.name && it.returnType == "Z" && it.parameterTypes.isEmpty()
    }.addInstructions(
        0,
        """
            const/4 v0, 0x0
            return v0
        """,
    )
}

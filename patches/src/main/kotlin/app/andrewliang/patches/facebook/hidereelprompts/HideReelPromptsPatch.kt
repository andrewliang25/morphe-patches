package app.andrewliang.patches.facebook.hidereelprompts

import app.andrewliang.patches.facebook.shared.enumConstantField
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val INTEREST_PROMPT = "INTERESTED_OR_NOT_INTERESTED_BUMPER"

/**
 * The enum of the items that Facebook can show over a reel, such as a banner, a location or a
 * poll. Redex renames the enum and its fields, but the constant names stay as literals in its
 * `<clinit>`. Two of them are enough to make it unique.
 */
internal object ReelOverlayTypeFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf(INTEREST_PROMPT, "TUNE_YOUR_ALGORITHM"),
)

@Suppress("unused")
val hideReelPromptsPatch = bytecodePatch(
    name = "[Reels] Hide interest prompts",
    description = "Removes the \"Are you interested in this reel?\" prompt from Reels.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // The prompt comes from the server as an overlay item on the reel, or from a flag that the
    // client reads. One predicate combines both with two server settings and decides whether the
    // reel gets the prompt. The reel overlay asks it before it builds the prompt, and before it
    // reserves a slot for it. When it returns false, the reel looks the same as a reel with no
    // prompt.
    //
    // Nothing obfuscated is named. The predicate is the only method that reads the prompt's enum
    // constant, takes one argument and returns a boolean. The other readers take more arguments
    // or return a component.
    execute {
        val overlayType = ReelOverlayTypeFingerprint.method.definingClass
        val promptField = enumConstantField(overlayType, INTEREST_PROMPT)

        val matches = mutableListOf<Pair<String, String>>()
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                if (method.returnType != "Z" || method.parameterTypes.size != 1) return@forEach
                val reads = method.implementation?.instructions?.any { instruction ->
                    instruction.opcode == Opcode.SGET_OBJECT &&
                        ((instruction as ReferenceInstruction).reference as FieldReference).let {
                            it.definingClass == overlayType && it.name == promptField
                        }
                } ?: false
                if (reads) matches += classDef.type to method.name
            }
        }
        check(matches.size == 1) { "Expected 1 reel interest prompt predicate, found $matches" }

        val (owner, name) = matches.single()
        val shouldShow = mutableClassDefBy(owner).methods.single {
            it.name == name && it.returnType == "Z" && it.parameterTypes.size == 1
        }

        shouldShow.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}

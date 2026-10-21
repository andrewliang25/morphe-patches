package app.andrewliang.patches.APP.TOPIC

// Probe for TOPIC. Local only: never push, delete before the PR.
// Copy to patches/src/main/kotlin/app/andrewliang/patches/<app>/<topic>/ProbePatch.kt and replace
// APP, TOPIC, Topic and the fingerprints. Each site logs unconditionally, so a site that never
// runs shows as a missing line in logcat.

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val PROBE = "Lapp/andrewliang/extension/TopicProbe;"

// A method in a class that R8 keeps (manifest entry, framework override, navigation graph):
internal object ProbeEntryFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lcom/example/KeptClass;" && method.name == "onCreateView"
    },
)

// A method in an obfuscated class, found by a string literal that it holds:
internal object ProbeDecisionFingerprint : Fingerprint(
    filters = listOf(string("a literal in the method")),
)

@Suppress("unused")
val topicProbePatch = bytecodePatch(
    name = "[Probe] TOPIC",
    description = "Logs TOPIC. Do not ship.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LINE)
    extendWith("extensions/extension.mpe")

    execute {
        // Entry: log a parameter (p1 is the first parameter of an instance method).
        ProbeEntryFingerprint.method.addInstructions(
            0,
            "invoke-static { p1 }, $PROBE->entry(Ljava/lang/Object;)V",
        )

        // Each return: log the value just before the method returns it. Highest index first, so
        // the earlier indices stay valid. A `return-object` that is a branch target would leave the
        // log unreachable: check the disassembly with the verify-patch skill.
        ProbeDecisionFingerprint.method.apply {
            implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index to (it.value as OneRegisterInstruction).registerA }
                .reversed()
                .forEach { (index, register) ->
                    addInstructions(index, "invoke-static { v$register }, $PROBE->result(Ljava/lang/Object;)V")
                }
        }
    }
}

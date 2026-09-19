package app.andrewliang.patches.facebook.externalbrowser

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/andrewliang/extension/ExternalBrowser;"

private const val REDIRECT =
    "$EXTENSION_CLASS->redirect(Landroid/app/Activity;Landroid/content/Intent;)Z"

/**
 * Facebook ships **two** in-app browsers. The newer one opens a tapped link.
 *
 * `handleByBrowserLite` sets the component of the launch intent, and it names the `litev2`
 * activity. Nothing else can redirect an explicit component bind. Thus a hook on the original
 * browser alone leaves each ordinary link in the app. The first device round showed this. The hook
 * was correct in the build, and it never ran.
 *
 * Both names are kept names, and `AndroidManifest.xml` declares them. Each has a subclass that
 * comes through `super.onCreate`. Thus these two entries cover four activities.
 */
private val IN_APP_BROWSERS = listOf(
    // The newer browser. Ordinary links go to this one.
    "Lcom/facebook/browser/litev2/lite/BrowserLiteDIActivity;",
    // The original. Some surfaces still reach it, thus it keeps its hook.
    "Lcom/facebook/browser/lite/BrowserLiteActivity;",
)

@Suppress("unused")
val forceExternalBrowserPatch = bytecodePatch(
    name = "[General] Open links in external browser",
    description = "A web link that you tap opens in your default browser, and not in the " +
        "in-app browser of Facebook. The pages of Facebook still open in the app. Thus login " +
        "and checkout still work.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)
    extendWith("extensions/extension.mpe")

    // Each hook goes in after the superclass call. At that point the activity is a valid Activity
    // for the extension, and the browser is not built yet. The extension returns false when the
    // link must stay in the app. The injected branch then continues into the original code.
    execute {
        val hooked = IN_APP_BROWSERS.sumOf { descriptor ->
            val classDef = mutableClassDefByOrNull(descriptor) ?: return@sumOf 0

            // onCreate: the URL is the data of the launch intent. Read it from the activity.
            val onCreate = classDef.methods.single {
                it.name == "onCreate" && it.parameterTypes == listOf("Landroid/os/Bundle;")
            }
            onCreate.hookRedirect(
                loadIntent = """
                    invoke-virtual { p0 }, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                """,
            )

            // onNewIntent: the new URL comes in as the parameter. getIntent() still returns the
            // intent that started the browser, which holds the previous link.
            val onNewIntent = classDef.methods.single {
                it.name == "onNewIntent" && it.parameterTypes == listOf("Landroid/content/Intent;")
            }
            onNewIntent.hookRedirect(loadIntent = null)

            2
        }

        check(hooked > 0) {
            "No in-app browser activity. The com.facebook.browser packages have new names."
        }
    }
}

/**
 * Put the redirect after the superclass call of this method.
 *
 * [loadIntent] is the smali that leaves the intent in `v0`. It must hold its own
 * `move-result-object`, because the call that gets the intent is of no use without one. It is null
 * when the intent is already the parameter of the method.
 *
 * The redirect must go after the superclass call. Before it, the superclass call does not run, and
 * Android answers with `SuperNotCalledException`.
 */
private fun MutableMethod.hookRedirect(loadIntent: String?) {
    val superIndex = instructions().indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
    check(superIndex >= 0) { "$definingClass->$name makes no super call to inject after" }

    val injectIndex = superIndex + 1
    val intent = if (loadIntent != null) "v0" else "p1"
    val call = """
        ${loadIntent ?: ""}
        invoke-static { p0, $intent }, $REDIRECT
        move-result v0
    """

    val traceClose = traceCloseIndex()

    if (traceClose != null) {
        // The method opens a trace section in its prologue. A direct return leaves that section
        // open. Thus the branch jumps to the instruction that loads the marker of the close call.
        addInstructionsWithLabels(
            injectIndex,
            "$call\nif-nez v0, :handled",
            ExternalLabel("handled", getInstruction(traceClose)),
        )
    } else {
        // There is no trace section to balance, thus the redirect returns. The label binds to the
        // real instruction that comes after, and never to one inside the injected block.
        addInstructionsWithLabels(
            injectIndex,
            "$call\nif-eqz v0, :keepInApp\nreturn-void",
            ExternalLabel("keepInApp", getInstruction(injectIndex)),
        )
    }
}

/**
 * Where the trace section of the method closes, or null when the method opens none.
 *
 * Facebook wraps these methods in a section. A static call that returns the marker of the section
 * opens it. A static call on the same class that takes the marker back closes it.
 *
 * The index is the instruction that feeds the close call. A jump to that instruction keeps the
 * section balanced and keeps the value of the marker.
 */
private fun MutableMethod.traceCloseIndex(): Int? {
    val instructions = instructions()

    val tracer = instructions.firstNotNullOfOrNull { instruction ->
        instruction.methodReferenceOrNull()
            ?.takeIf { instruction.opcode == Opcode.INVOKE_STATIC && it.returnType == "I" }
            ?.definingClass
    } ?: return null

    val closeIndex = instructions.indexOfLast { instruction ->
        instruction.opcode == Opcode.INVOKE_STATIC &&
            instruction.methodReferenceOrNull()
                ?.let { it.definingClass == tracer && it.returnType == "V" } == true
    }

    return if (closeIndex > 0) closeIndex - 1 else null
}

private fun MutableMethod.instructions(): List<Instruction> =
    implementation?.instructions?.toList()
        ?: throw IllegalStateException("$definingClass->$name has no body to patch")

private fun Instruction.methodReferenceOrNull() =
    (this as? ReferenceInstruction)?.reference as? MethodReference

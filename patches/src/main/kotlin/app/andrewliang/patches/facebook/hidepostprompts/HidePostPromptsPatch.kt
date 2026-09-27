package app.andrewliang.patches.facebook.hidepostprompts

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The constructor of `NTFeedStoryBumperComponent`, the component that draws a story bumper. It
 * names itself in a string literal. Another method also holds the literal, so the fingerprint pins
 * the constructor.
 */
internal object StoryBumperComponentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(),
    strings = listOf("NTFeedStoryBumperComponent"),
)

@Suppress("unused")
val hidePostPromptsPatch = bytecodePatch(
    name = "[Feed] Hide post prompts",
    description = "Removes the prompts Facebook adds inside a post, such as \"Are you " +
        "interested in this post?\".",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // A bumper is a strip that the server attaches to a story and that Facebook draws between the
    // post and its like bar. Each one is a server-built template, and "Are you interested in this
    // post?" is one kind. All the kinds in the app are engagement prompts ("show less", follow,
    // chat and post suggestions), so the patch removes all of them, not one kind.
    //
    // One static predicate on the component answers "does this story have a bumper?". The bumper
    // plugin and every row that makes space for a bumper ask it. When it returns false, Facebook
    // does what it does for the many stories that have no bumper.
    execute {
        val component = mutableClassDefBy(StoryBumperComponentFingerprint.method.definingClass)

        // The predicate is the only static method on the component that takes one argument and
        // returns a boolean.
        val hasBumper = component.methods.single {
            AccessFlags.STATIC.isSet(it.accessFlags) &&
                it.returnType == "Z" &&
                it.parameterTypes.size == 1
        }

        hasBumper.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}

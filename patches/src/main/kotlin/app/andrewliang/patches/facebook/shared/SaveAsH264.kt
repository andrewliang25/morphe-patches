package app.andrewliang.patches.facebook.shared

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchBuilder
import app.morphe.patcher.patch.booleanOption

private const val MEDIA_DOWNLOAD = "Lapp/andrewliang/extension/MediaDownload;"

/** The option "Save as H.264". The story patch and the reel patch each declare their own. */
internal fun PatchBuilder<*>.saveAsH264Option() = booleanOption(
    key = "saveAsH264",
    default = false,
    title = "Save as H.264",
    description = "Saves videos as H.264 with AAC sound, so that apps such as WhatsApp can send " +
        "them. The device converts a video in another format. This makes the save slower " +
        "and the file larger.",
)

/**
 * Make the extension flag [flag] of `MediaDownload` return true.
 *
 * The flag is a `()Z` method whose body is `const/4 v0, 0x0` then `return v0`.
 */
internal fun BytecodePatchContext.enableSaveAsH264(flag: String) {
    mutableClassDefBy(MEDIA_DOWNLOAD).methods.single { it.name == flag }
        .replaceInstruction(0, "const/4 v0, 0x1")
}

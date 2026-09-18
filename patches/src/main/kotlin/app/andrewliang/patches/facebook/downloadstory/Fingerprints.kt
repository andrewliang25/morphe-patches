package app.andrewliang.patches.facebook.downloadstory

import app.morphe.patcher.Fingerprint

/** Kept name. The story viewer's "More" menu, where the save item is added. */
internal const val STORY_VIEWER_MORE_MENU =
    "Lcom/facebook/stories/viewer/ui/buckets/regular/topbar/menu/StoryViewerMoreButtonCallback;"

/**
 * The action behind the save item, found by the analytics event it reports.
 *
 * The event name appears in three methods, but the other two are a string table returning a
 * `String` and the orchestrator further down the chain, which takes eight parameters. A one-argument
 * `void` is only this one.
 *
 * The patch does not edit this method. It uses it to learn the action's class, which is the thing
 * the menu builder creates, and that is what identifies the builder without naming any obfuscated
 * type.
 */
internal object SaveStoryActionFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("save_story_attempted"),
)

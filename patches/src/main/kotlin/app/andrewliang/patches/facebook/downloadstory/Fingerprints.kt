package app.andrewliang.patches.facebook.downloadstory

import app.morphe.patcher.Fingerprint

/** Kept name. The "More" menu of the story viewer, where the save item is added. */
internal const val STORY_VIEWER_MORE_MENU =
    "Lcom/facebook/stories/viewer/ui/buckets/regular/topbar/menu/StoryViewerMoreButtonCallback;"

/**
 * Kept name. One story, and the object that the save action carries.
 *
 * Its `getMedia` is also a kept name, so the extension reaches the address of the story through
 * two names that Redex leaves alone and no name that it rewrites.
 */
internal const val STORY_CARD = "Lcom/facebook/stories/model/StoryCard;"

/**
 * The action behind the save item. The analytics event that it reports is what finds it.
 *
 * The event name is in three methods. One of the other two is a string table, which returns a
 * `String`. The second is the orchestrator further down the chain, which takes eight parameters.
 * Only this method is a `void` with one parameter.
 *
 * The patch does not edit this method. It reads the class of the action from it. That class is what
 * the menu builder creates, and thus what identifies the builder without a name that Redex moves.
 */
internal object SaveStoryActionFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("save_story_attempted"),
)

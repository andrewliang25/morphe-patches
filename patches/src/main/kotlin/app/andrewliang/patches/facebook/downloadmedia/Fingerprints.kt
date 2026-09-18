package app.andrewliang.patches.facebook.downloadmedia

import app.morphe.patcher.Fingerprint

/** The tag Facebook puts on the download menu item. It is what identifies the item in both menus. */
internal const val DOWNLOAD_VIDEO_TAG = "DOWNLOAD_VIDEO"

/**
 * The two builders of the video menu, which is also the menu behind the three dots on a reel.
 *
 * Facebook has one menu for every video surface, so there is no reels-specific download code to
 * find: the item tagged [DOWNLOAD_VIDEO_TAG] is the "Download reel" button as well. The second
 * builder extends the first and overrides this method with a longer version used by the player.
 *
 * The tag appears in six methods across the APK, but only these two return `void`, so the return
 * type is what selects them.
 */
internal object VideoMenuDownloadItemFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(DOWNLOAD_VIDEO_TAG),
)

package app.andrewliang.patches.facebook.hidesponsoredreels

import app.morphe.patcher.Fingerprint

/**
 * `VideoHomeDataControllerImpl.maybeInsertAds` — splices ads into the Reels and Watch item list.
 * Anchored on the QPL trace literal it opens with, which is unique in the APK. That marker is the
 * first instruction, so returning ahead of it leaves no unbalanced trace section.
 */
internal object VideoHomeInsertAdsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("VideoHomeDataControllerImpl.maybeInsertAds"),
)

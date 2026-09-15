package app.andrewliang.patches.facebook.hidesponsoredstories

import app.morphe.patcher.Fingerprint

internal const val IMMUTABLE_LIST = "Lcom/google/common/collect/ImmutableList;"

/**
 * The Stories tray interleaver: takes the organic tray and returns it with ad ("inorganic") buckets
 * spliced in. Every name on the class is obfuscated, so it is anchored on the
 * `"handling_inorganic_clash"` literal, which appears in exactly one method in the APK.
 */
internal object StoriesTrayAdInserterFingerprint : Fingerprint(
    returnType = IMMUTABLE_LIST,
    strings = listOf("handling_inorganic_clash"),
)

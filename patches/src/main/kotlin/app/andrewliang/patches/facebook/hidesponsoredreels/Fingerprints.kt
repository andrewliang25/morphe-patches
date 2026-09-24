package app.andrewliang.patches.facebook.hidesponsoredreels

import app.andrewliang.patches.facebook.shared.redexOriginalName
import app.morphe.patcher.Fingerprint

/**
 * Reels and Watch get their ads from two families of insertion. One runs when a page of videos
 * loads; the other three fire on their own while you are already scrolling, each fetching a single
 * ad and splicing it into the live item collection. Only the first is a chokepoint, so all four are
 * neutered.
 *
 * Every one of these methods returns `void` and does nothing but insert an ad and tell the
 * controller its data changed, so returning immediately is safe. Each also opens a trace section
 * after the point of injection, never before, so no section is left unbalanced.
 */

/**
 * `VideoHomeDataControllerImpl.maybeInsertAds` — splices a page of ads into the Reels and Watch item
 * list. It is the only caller of the batch insert, and the controller is the only implementation of
 * its interface. Anchored on the trace literal it opens with, which is unique in the APK.
 */
internal object VideoHomeInsertAdsFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("VideoHomeDataControllerImpl.maybeInsertAds"),
)

/**
 * `VideoHomeDataControllerAdsUtil.maybeInsertFbShortsRealtimeIntentItem` — inserts an ad fetched in
 * response to what you just did. Two separate callbacks reach it, and neither goes through
 * [VideoHomeInsertAdsFingerprint].
 */
internal object RealtimeIntentAdInsertFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("VideoHomeDataControllerAdsUtil.maybeInsertFbShortsRealtimeIntentItem"),
)

/**
 * The task that inserts an SFD ad. It carries the name of the utility that posts it, which is unique
 * in the APK.
 */
internal object SfdAdInsertFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("VideoHomeDataControllerSfdAdsUtil"),
)

/**
 * The task that renders a POE ad into the item collection. This one holds no string literal, so it
 * is recognised by the name Redex left on the task class itself -- see [redexOriginalName] for why
 * that is sound here and unsound for locating an enclosing class.
 *
 * Confirmed to be an ad rather than an injected organic unit: it logs what it inserted under the
 * `SPONSORED` story category.
 */
internal object PoeAdRenderFingerprint : Fingerprint(
    name = "run",
    returnType = "V",
    custom = { _, classDef ->
        redexOriginalName(classDef) == "VideoHomeDataControllerPoeAdsUtil\$renderPoeItemToUiBuffer\$1"
    },
)

/**
 * The ad-break fetch that the Reels ad states share. It sends either a banner query or a video ad
 * query, and it logs one of these literals right after each query returns its future. The call
 * before each literal is the helper that every request of that kind goes through -- see
 * `HideSponsoredReelsPatch`. Both literals are also in the classic in-stream fetch, which calls the
 * same video helper, so a match on either method finds the same thing.
 */
internal object AdBreakFetchFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(BANNER_FETCH_LOG, VIDEO_FETCH_LOG),
)

internal const val BANNER_FETCH_LOG = "Kicking off banner ads fetch"
internal const val VIDEO_FETCH_LOG = "Kicking off video ad fetch"

/**
 * The idle state of the Reels ad state machine builds its own video ad query by name and runs it on
 * the generic GraphQL executor. The query name is otherwise only in a string table, which returns a
 * `String`, so `void` picks the fetch.
 */
internal object ReelsVideoAdQueryFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(REELS_VIDEO_AD_QUERY),
)

internal const val REELS_VIDEO_AD_QUERY = "FBFetchReelsVideoAdsQuery"

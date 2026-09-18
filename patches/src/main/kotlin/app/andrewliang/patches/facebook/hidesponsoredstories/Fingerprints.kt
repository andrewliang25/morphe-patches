package app.andrewliang.patches.facebook.hidesponsoredstories

import app.morphe.patcher.Fingerprint

internal const val IMMUTABLE_LIST = "Lcom/google/common/collect/ImmutableList;"

/**
 * Story buckets do not pass through one inserter. `StoryviewerBucketDataController.processBucketData`
 * holds an `ImmutableList` of bucket data sources and calls the same interleave method on each,
 * feeding every result into the next. The chain is assembled per session behind a launch-config
 * predicate and MobileConfig gates, so which sources are present varies by account.
 *
 * Every source below takes the organic list and returns it with its own buckets spliced in, and all
 * four share one signature: `(FbUserSession, <flags>, ImmutableList) -> ImmutableList`. Returning the
 * input unchanged is the result Facebook itself produces when a source contributes nothing.
 *
 * Each is anchored on a string literal that is unique in the APK. The class and method names are
 * Redex names that change every release, so none is named here.
 */

/**
 * The clash resolver, which decides the order when two ad ("inorganic") buckets land together.
 * Anchored on `"handling_inorganic_clash"`.
 */
internal object InorganicClashResolverFingerprint : Fingerprint(
    returnType = IMMUTABLE_LIST,
    strings = listOf("handling_inorganic_clash"),
)

/**
 * `AdBucketDataSourceUtil` — the main ad placement engine, and by far the largest of the four. It
 * keeps its own queues of inserted and pending ads alongside a model of the organic stream, then
 * decides where each ad lands.
 *
 * `"uninsertedMainAdsQueue"` also appears in the placement-rule helper it calls, so the return type
 * is what selects the interleave method: the helper's two methods return `void` and `boolean`.
 */
internal object AdBucketDataSourceFingerprint : Fingerprint(
    returnType = IMMUTABLE_LIST,
    strings = listOf("uninsertedMainAdsQueue"),
)

/**
 * `AdPaginatingBucketStaticInsertionDataSource` — drains a queue of pending ad buckets as the viewer
 * paginates. A factory picks this or [AdBucketDataSourceFingerprint] by flag, so both ship in the
 * APK and both need neutering.
 */
internal object AdPaginatingBucketInserterFingerprint : Fingerprint(
    returnType = IMMUTABLE_LIST,
    strings = listOf("AdPaginatingBucketStaticInsertionDataSource.getBuckets"),
)

/**
 * `StoryViewerMidCardDataSource` — mid-card ads fetched while the viewer is already open, which is
 * why they appear mid-session and are gone after the app restarts.
 */
internal object StoryViewerMidCardFingerprint : Fingerprint(
    returnType = IMMUTABLE_LIST,
    strings = listOf("StoryViewerMidCardDataSource.getBuckets"),
)

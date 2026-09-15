package app.andrewliang.patches.facebook.blockadprefetch

import app.andrewliang.patches.facebook.shared.neuterVoidMethods
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.patch.bytecodePatch

/** Background ad fetchers. Each keeps its real `com.facebook` name; each void method enqueues. */
private val AD_PREFETCH_SCHEDULERS = listOf(
    // News feed ads channel — the periodic WorkManager prefetch and its emerging-surface twin.
    "Lcom/facebook/feed/push/adschannelbackgroundprefetch/FeedAdsChannelBackgroundPrefetchInitializerAppJob;",
    "Lcom/facebook/feed/push/adschannelemergingsurfaceprefetch/FeedAdsChannelEmergingSurfacePrefetchInitializerAppJob;",
    // Reels and Watch.
    "Lcom/facebook/video/videohome/prefetching/ads/background/ReelsAdsBackgroundPrefetchAppJob;",
    // Stories viewer ads.
    "Lcom/facebook/stories/features/ads/prefetch/StoryViewerAdsPrefetchAppInitializationController;",
    "Lcom/facebook/stories/features/ads/prefetch/StoryViewerAdsPrefetchController;",
    // Revalidation of the cached ad payloads.
    "Lcom/facebook/addelivery/deliveryvalidation/cachedadsvalidator/NewsFeedAdCacheSyncInitializerAppJob;",
    // The on-device ad-ranking ML model: downloads the model and keeps it warm.
    "Lcom/facebook/feed/ads/mlranker/MlRankerAppJob;",
)

@Suppress("unused")
val blockAdPrefetchPatch = bytecodePatch(
    name = "[Ad] Block background ad prefetch",
    description = "Stops Facebook downloading ads, ad images and the on-device ad-ranking model " +
        "in the background. Saves mobile data, battery and storage whether or not you also hide " +
        "the ads themselves.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Block the schedulers, not the workers: two of the three workers no longer override doWork,
    // and androidx.work.Worker's own is renamed and returns an obfuscated Result with no safe
    // value to synthesise.
    //
    // WorkManager persists its schedule, so a device that already ran an unpatched build keeps
    // work enqueued before patching until its app data is cleared.
    execute {
        val neutered = AD_PREFETCH_SCHEDULERS.sumOf { neuterVoidMethods(it) }

        check(neutered > 0) {
            "No ad prefetch schedulers found; the com.facebook.feed.push / videohome.prefetching " +
                "packages were renamed or removed"
        }
    }
}

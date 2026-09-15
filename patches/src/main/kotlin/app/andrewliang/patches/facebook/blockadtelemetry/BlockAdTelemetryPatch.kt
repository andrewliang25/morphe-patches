package app.andrewliang.patches.facebook.blockadtelemetry

import app.andrewliang.patches.facebook.shared.neuterVoidMethods
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.patch.bytecodePatch

/** Ad measurement that runs whether or not an ad is shown. All keep their real names. */
private val AD_TELEMETRY = listOf(
    // Watches for you taking a screenshot of an ad. The controller's void methods add and remove
    // the detector's listener, so neutering them means it is never registered.
    "Lcom/facebook/ads/screenshot/AdsScreenshotController;",
    "Lcom/facebook/ads/AdsScreenshotDetector;",
    // Ad attribution for apps you install: the tracker's scheduler and the service that does the
    // reporting (doHandleIntent keeps its name).
    "Lcom/facebook/feed/platformads/AppInstallTrackerScheduler;",
    "Lcom/facebook/feed/platformads/AppInstallService;",
)

@Suppress("unused")
val blockAdTelemetryPatch = bytecodePatch(
    name = "[Ad] Block ad telemetry",
    description = "Stops Facebook watching for screenshots of ads and reporting which apps you " +
        "install for ad attribution.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Deliberately not covered:
    //
    // - AdVisualQualityEngine / OcrPreprocessor (on-device OCR of ad creative). Their only entry
    //   points are suspend functions returning Object, so an early return hands null to a
    //   continuation that does not expect one. They only run on a rendered ad anyway.
    // - PigeonFeedUnitSponsoredImpressionLogger. Its one clean entry point marks an impression as
    //   *already logged*; neutering it invites repeat logging rather than none.
    execute {
        val neutered = AD_TELEMETRY.sumOf { neuterVoidMethods(it) }

        check(neutered > 0) {
            "No ad telemetry classes found; com.facebook.ads.screenshot / " +
                "com.facebook.feed.platformads were renamed or removed"
        }
    }
}

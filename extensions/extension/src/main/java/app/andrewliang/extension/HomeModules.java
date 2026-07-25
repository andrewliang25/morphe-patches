package app.andrewliang.extension;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper for hiding LINE Home tab modules.
 *
 * LINE's Home tab is a server-driven list of typed modules (m52.a0, each with a stable
 * getType() string). The patch iterates that list in injected smali and drops modules whose
 * type this helper flags — the extension only makes the String decision (it cannot reference
 * the obfuscated LINE module classes).
 *
 * DIAGNOSTIC BUILD: every module type encountered is logged to logcat under tag
 * "MorpheHomeModules", so the real type strings of the visible Home sections can be captured
 * on device (the type -> visible-section mapping can't be determined statically). Also logs a
 * one-time marker when the filter runs, so we can tell "filter ran but nothing matched" apart
 * from "filter never ran (wrong injection point)". Capture with:
 *   adb logcat -s MorpheHomeModules
 * Once the real types are known, set the blocklist to them and remove the logging.
 */
public final class HomeModules {

    private HomeModules() {}

    private static final String TAG = "MorpheHomeModules";

    private static final Set<String> HIDDEN = new HashSet<>();

    static {
        // Confirmed on device:
        HIDDEN.add("HomeContentsRecommendation"); // recommended stickers (confirmed gone)
        HIDDEN.add("HomePerformanceAd");          // a performance ad module
        // Testing this round: 即時夯話題 is most likely FLEX. NOTE there are 2 FLEX modules in
        // the feed, so this hides BOTH — confirm on device whether it removes 即時夯話題 (and
        // possibly the bottom ad) without taking any wanted content.
        HIDDEN.add("FLEX");
    }

    /** DIAGNOSTIC: called once at filter entry so "ran but empty/unmatched" is distinguishable
     *  from "never ran". Returns the list unchanged. */
    public static java.util.List onEnter(java.util.List modules) {
        Log.i(TAG, "filterHomeModules ENTER size=" + (modules == null ? "null" : modules.size()));
        return modules;
    }

    /** @return true if a Home module of this type should be hidden. */
    public static boolean shouldHide(String type) {
        boolean hide = type != null && HIDDEN.contains(type);
        Log.i(TAG, "module type=" + type + " hide=" + hide);
        return hide;
    }
}

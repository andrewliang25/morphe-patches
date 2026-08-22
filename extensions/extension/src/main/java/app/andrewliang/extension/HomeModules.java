package app.andrewliang.extension;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper for the "Hide Home modules" LINE patch.
 *
 * LINE's Home tab renders a server-driven list of typed modules (m52.a0, each with a stable
 * getType() string), held as the module list of the Compose state x72.h$a. The patch iterates
 * that list in injected smali and drops modules whose type this helper flags — the extension
 * only makes the String decision (it cannot reference the obfuscated LINE module classes).
 *
 * Blocklist confirmed on device (LINE 26.11.0):
 *   HomeContentsRecommendation = recommended stickers/content
 *   HomePerformanceAd          = performance ad modules in the feed
 *   FLEX                       = 即時夯話題 (real-time hot topics) and the bottom promo/ad block
 *
 * AdModel is in the blocklist on static evidence only. The GcsAdModuleViewData and GcsAdMeta
 * server models back it, thus it is an ad module. But it did not appear in the device capture,
 * so it is not confirmed. If the server never sends AdModel, the entry does no harm.
 *
 * A separate patch hides the content feed below the friends list. See HomeFeed.
 */
public final class HomeModules {

    private HomeModules() {}

    private static final Set<String> HIDDEN = new HashSet<>();

    static {
        HIDDEN.add("HomeContentsRecommendation");
        HIDDEN.add("HomePerformanceAd");
        HIDDEN.add("FLEX");
        // Not confirmed on device. See the class comment.
        HIDDEN.add("AdModel");
    }

    /** @return true if a Home module of this type should be hidden. */
    public static boolean shouldHide(String type) {
        return type != null && HIDDEN.contains(type);
    }
}

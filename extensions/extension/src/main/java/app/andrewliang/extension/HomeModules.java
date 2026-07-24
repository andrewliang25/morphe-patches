package app.andrewliang.extension;

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
 * EXPERIMENTAL blocklist for the test-build loop: hides the strong candidate types for the
 * bottom-of-Home ad, 即時夯話題 (real-time hot topics), and recommended stickers. Narrow this
 * to the confirmed types once device testing maps each section to its type.
 */
public final class HomeModules {

    private HomeModules() {}

    private static final Set<String> HIDDEN = new HashSet<>();

    static {
        // Ad modules (bottom-of-Home ad candidates).
        HIDDEN.add("HomePerformanceAd");
        HIDDEN.add("AdModel");
        // Content-recommendation candidates (即時夯話題 / recommended stickers).
        HIDDEN.add("HomeContentsRecommendation");
        HIDDEN.add("HomeFeedMatomeCarousel");
        HIDDEN.add("HomeFeedMatomeSingle");
    }

    /** @return true if a Home module of this type should be hidden. */
    public static boolean shouldHide(String type) {
        return type != null && HIDDEN.contains(type);
    }
}

package app.andrewliang.extension;

/**
 * Helper for the "Hide Home content feed" LINE patch.
 *
 * LINE's Home tab renders a server-driven list of typed modules (m52.a0, each with a stable
 * getType() string), held as the module list of the Compose state x72.h$a. The patch iterates
 * that list in injected smali and drops modules this helper flags — the extension only makes
 * the String decision (it cannot reference the obfuscated LINE module classes).
 *
 * Everything below the friends list — the infinite-scrolling card stack — is one server feed
 * whose module types all start with "HomeFeed" (network models GcsHomeFeed*). On LINE 26.11.0
 * that is 14 types:
 *   HomeFeedPost                    = an official-account / LINE NEWS post card
 *   HomeFeedLiveSingle              = the OA_LIVE variant of the above
 *   HomeFeedMatomeSingle/-Carousel  = AI-digest ("matome") news cards
 *   HomeFeedUnitBigVisual, -Grid, -Ranking, -ShortFormGrid, -Single, -SingleAndGrid
 *                                   = the content-unit card layouts, each wrapping posts
 *   HomeFeedDefaultPageError, HomeFeedDefaultPageLoading, HomeFeedError, HomeFeedSeedPostError
 *                                   = that feed's error / spinner placeholders
 *
 * Matching on the prefix rather than the 14 literals is deliberate: the server rotates between
 * card variants (and between regions — a Taiwan account renders none of these, a Japanese one
 * renders them), so a literal list would reopen the hole on the next rotation. The error and
 * loading placeholders are included on purpose, so no orphan spinner or error shell is left
 * behind where the cards were.
 *
 * The Home *modules* above the feed (recommended stickers/content, hot topics, ads) are a
 * different patch — see HomeModules.
 */
public final class HomeFeed {

    private HomeFeed() {}

    /** Every module type in LINE's server-driven Home content feed starts with this. */
    private static final String FEED_PREFIX = "HomeFeed";

    /** @return true if a Home module of this type belongs to the content feed. */
    public static boolean shouldHide(String type) {
        return type != null && type.startsWith(FEED_PREFIX);
    }
}

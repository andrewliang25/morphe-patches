package app.andrewliang.extension;

/**
 * Helper for the "Hide Home content feed" LINE patch.
 *
 * The LINE Home tab shows one server-driven list of typed modules. Each module is an m52.a0
 * with a stable getType() string. The list is the module list of the Compose state x72.h$a.
 * The patch iterates that list in injected smali and drops the modules that this helper flags.
 * The extension makes the String decision only, because it cannot reference the obfuscated
 * LINE module classes.
 *
 * The card stack below the friends list is one server feed, and it scrolls without end. The
 * type of every module in this feed starts with "HomeFeed" (network models GcsHomeFeed*). On
 * LINE 26.11.0 there are 14 types:
 *   HomeFeedPost                    = an official account / LINE NEWS post card
 *   HomeFeedLiveSingle              = the OA_LIVE variant of HomeFeedPost
 *   HomeFeedMatomeSingle/-Carousel  = AI-digest ("matome") news cards
 *   HomeFeedUnitBigVisual, -Grid, -Ranking, -ShortFormGrid, -Single, -SingleAndGrid
 *                                   = the content-unit card layouts, each one holds posts
 *   HomeFeedDefaultPageError, HomeFeedDefaultPageLoading, HomeFeedError, HomeFeedSeedPostError
 *                                   = the error and spinner placeholders of the same feed
 *
 * This helper tests the prefix and not the 14 literal strings. The server rotates between card
 * variants, and it sends different variants to different regions. A Taiwan account gets none
 * of these modules, but a Japanese account gets them. Thus a literal list does not cover the
 * next rotation. The prefix also matches the error and loading types, so no empty spinner or
 * error card stays on the tab.
 *
 * The Home modules above the feed are a different patch. See HomeModules for the recommended
 * stickers, the hot topics and the ads.
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

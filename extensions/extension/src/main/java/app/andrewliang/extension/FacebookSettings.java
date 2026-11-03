package app.andrewliang.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime switches of "[General] Andrew's Patch Setting" for Facebook.
 *
 * <p>Each patch with a switch calls its method here before it changes Facebook's behaviour, and
 * does nothing when the method returns false. Every switch is on by default, so a build behaves as
 * its patches were chosen until the user turns a switch off.
 *
 * <p>The {@code …Included()} methods return false here. At patch time, each patch rewrites its own
 * method to return true, and the settings patch does the same to {@link #rowIncluded()}. The screen
 * shows only the switches of the patches in the build. A stored value counts only while the
 * settings row is in the build: without the row, the user cannot turn a switch on again.
 */
public final class FacebookSettings {

    private FacebookSettings() {}

    static final String PREFERENCES = "andrew_facebook_settings";

    static final String HIDE_SPONSORED_POSTS = "hide_sponsored_posts";
    static final String HIDE_PAGE_SUGGESTIONS = "hide_page_suggestions";
    static final String HIDE_POST_PROMPTS = "hide_post_prompts";
    static final String BLOCK_FEED_AUTO_REFRESH = "block_feed_auto_refresh";
    static final String HIDE_SPONSORED_REELS = "hide_sponsored_reels";
    static final String HIDE_REEL_PROMPTS = "hide_reel_prompts";
    static final String DOWNLOAD_REEL = "download_reel";
    static final String REELS_PIP = "reels_pip";
    static final String HIDE_SPONSORED_STORIES = "hide_sponsored_stories";
    static final String DOWNLOAD_STORY = "download_story";
    static final String ANONYMOUS_STORY_VIEWS = "anonymous_story_views";
    static final String DISABLE_STORY_AUTO_ADVANCE = "disable_story_auto_advance";
    static final String HIDE_AFFILIATE_LINKS = "hide_affiliate_links";
    static final String EXTERNAL_BROWSER = "external_browser";
    static final String AMOLED_THEME = "amoled_theme";

    // Some switches are read on hot paths (each feed item, each seen field), so the values are read
    // from storage once and then kept here. Only the settings screen writes them.
    private static final Map<String, Boolean> VALUES = new ConcurrentHashMap<>();

    public static boolean hideSponsoredPosts() {
        return isOn(HIDE_SPONSORED_POSTS);
    }

    public static boolean hidePageSuggestions() {
        return isOn(HIDE_PAGE_SUGGESTIONS);
    }

    public static boolean hidePostPrompts() {
        return isOn(HIDE_POST_PROMPTS);
    }

    public static boolean blockFeedAutoRefresh() {
        return isOn(BLOCK_FEED_AUTO_REFRESH);
    }

    public static boolean hideSponsoredReels() {
        return isOn(HIDE_SPONSORED_REELS);
    }

    public static boolean hideReelPrompts() {
        return isOn(HIDE_REEL_PROMPTS);
    }

    public static boolean downloadReel() {
        return isOn(DOWNLOAD_REEL);
    }

    public static boolean reelsPip() {
        return isOn(REELS_PIP);
    }

    public static boolean hideSponsoredStories() {
        return isOn(HIDE_SPONSORED_STORIES);
    }

    public static boolean downloadStory() {
        return isOn(DOWNLOAD_STORY);
    }

    public static boolean anonymousStoryViews() {
        return isOn(ANONYMOUS_STORY_VIEWS);
    }

    public static boolean disableStoryAutoAdvance() {
        return isOn(DISABLE_STORY_AUTO_ADVANCE);
    }

    public static boolean hideAffiliateLinks() {
        return isOn(HIDE_AFFILIATE_LINKS);
    }

    public static boolean externalBrowser() {
        return isOn(EXTERNAL_BROWSER);
    }

    public static boolean amoledTheme() {
        return isOn(AMOLED_THEME);
    }

    // The patches that force a boolean true call these: they give true while the switch is on, and
    // the value Facebook computed while it is off.

    public static boolean reelsPip(boolean stock) {
        return stock || reelsPip();
    }

    public static boolean downloadStory(boolean stock) {
        return stock || downloadStory();
    }

    // The patches rewrite these methods to return true. Keep each one a plain "return false".

    static boolean rowIncluded() {
        return false;
    }

    static boolean hideSponsoredPostsIncluded() {
        return false;
    }

    static boolean hidePageSuggestionsIncluded() {
        return false;
    }

    static boolean hidePostPromptsIncluded() {
        return false;
    }

    static boolean blockFeedAutoRefreshIncluded() {
        return false;
    }

    static boolean hideSponsoredReelsIncluded() {
        return false;
    }

    static boolean hideReelPromptsIncluded() {
        return false;
    }

    static boolean downloadReelIncluded() {
        return false;
    }

    static boolean reelsPipIncluded() {
        return false;
    }

    static boolean hideSponsoredStoriesIncluded() {
        return false;
    }

    static boolean downloadStoryIncluded() {
        return false;
    }

    static boolean anonymousStoryViewsIncluded() {
        return false;
    }

    static boolean disableStoryAutoAdvanceIncluded() {
        return false;
    }

    static boolean hideAffiliateLinksIncluded() {
        return false;
    }

    static boolean externalBrowserIncluded() {
        return false;
    }

    static boolean amoledThemeIncluded() {
        return false;
    }

    static boolean isOn(String key) {
        if (!rowIncluded()) return true;
        Boolean value = VALUES.get(key);
        if (value != null) return value;
        SharedPreferences preferences = preferences();
        // Before the Application exists there is no storage. Answer on without keeping it, so a
        // switch that is off is read again once storage is there. AMOLED reads colours this early.
        if (preferences == null) return true;
        boolean stored = preferences.getBoolean(key, true);
        VALUES.put(key, stored);
        return stored;
    }

    static void set(String key, boolean on) {
        VALUES.put(key, on);
        SharedPreferences preferences = preferences();
        // Written at once, because a user can close Facebook right after a change.
        if (preferences != null) preferences.edit().putBoolean(key, on).commit();
    }

    /**
     * Adds the "Andrew's Patch Setting" row to the top of the Menu drawer and returns {@code root}.
     * Called by the settings patch with the view that the drawer fragment returns: a
     * {@link FrameLayout} that holds the drawer's list. The row stays out of the list, so the list
     * moves down by the height of the row and scrolls below it.
     */
    public static View addMenuRow(View root) {
        if (!(root instanceof FrameLayout)) return root;
        FrameLayout frame = (FrameLayout) root;
        Context context = frame.getContext();

        int background = backgroundOf(frame);
        View row = FacebookSettingsScreen.menuRow(context, background);
        int height = FacebookSettingsScreen.MENU_ROW_HEIGHT_DP;
        int heightPx = FacebookSettingsScreen.dp(context, height);

        for (int i = 0; i < frame.getChildCount(); i++) {
            ViewGroup.LayoutParams params = frame.getChildAt(i).getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).topMargin += heightPx;
            }
        }
        frame.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        return root;
    }

    /** The colour that the drawer list paints, or 0 when the list has no plain colour yet. */
    static int backgroundOf(ViewGroup frame) {
        for (int i = 0; i < frame.getChildCount(); i++) {
            Drawable drawable = frame.getChildAt(i).getBackground();
            if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
        }
        return 0;
    }

    private static SharedPreferences preferences() {
        Context context = LineSettings.application();
        return context == null ? null : context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}

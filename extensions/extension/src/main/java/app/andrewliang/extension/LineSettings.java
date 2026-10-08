package app.andrewliang.extension;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime switches of "[General] Andrew's Patch Setting" for LINE.
 *
 * <p>Each patch with a switch calls its method here before it changes LINE's behaviour, and does
 * nothing when the method returns false. Every switch is on by default, so a build behaves as its
 * patches were chosen until the user turns a switch off.
 *
 * <p>The {@code …Included()} methods return false here. At patch time, each patch rewrites its own
 * method to return true, and the settings patch does the same to {@link #rowIncluded()}. The screen
 * shows only the switches of the patches in the build. A stored value counts only while the
 * settings row is in the build: without the row, the user cannot turn a switch on again.
 */
public final class LineSettings {

    private LineSettings() {}

    static final String PREFERENCES = "andrew_line_settings";

    static final String KEEP_CHATS_UNREAD = "keep_chats_unread";
    static final String KEEP_UNSENT_MESSAGES = "keep_unsent_messages";
    static final String EXTERNAL_BROWSER = "external_browser";
    static final String DISABLE_VOOM = "disable_voom";
    static final String HIDE_TODAY_TAB = "hide_today_tab";
    static final String HIDE_SHOPPING_TAB = "hide_shopping_tab";
    static final String HIDE_VOOM_TAB = "hide_voom_tab";
    static final String HIDE_WALLET_TAB = "hide_wallet_tab";

    /** The resource name of the row title. The settings patch adds it to strings.xml. */
    private static final String TITLE_RESOURCE = "andrew_patch_settings";

    /** A gear like the other Settings row icons. The settings patch adds it. */
    private static final String ICON_RESOURCE = "andrew_ic_settings";

    // keepunsent reads its switch on the database path of each unsent message, so the values are
    // read from storage once and then kept here. Only the settings screen writes them.
    private static final Map<String, Boolean> VALUES = new ConcurrentHashMap<>();

    public static boolean keepChatsUnread() {
        return isOn(KEEP_CHATS_UNREAD);
    }

    public static boolean keepUnsentMessages() {
        return isOn(KEEP_UNSENT_MESSAGES);
    }

    public static boolean externalBrowser() {
        return isOn(EXTERNAL_BROWSER);
    }

    public static boolean disableVoom() {
        return isOn(DISABLE_VOOM);
    }

    public static boolean hideTodayTab() {
        return isOn(HIDE_TODAY_TAB);
    }

    public static boolean hideShoppingTab() {
        return isOn(HIDE_SHOPPING_TAB);
    }

    public static boolean hideVoomTab() {
        return isOn(HIDE_VOOM_TAB);
    }

    public static boolean hideWalletTab() {
        return isOn(HIDE_WALLET_TAB);
    }

    // The patches rewrite these methods to return true. Keep each one a plain "return false".

    static boolean rowIncluded() {
        return false;
    }

    static boolean keepChatsUnreadIncluded() {
        return false;
    }

    static boolean keepUnsentMessagesIncluded() {
        return false;
    }

    static boolean externalBrowserIncluded() {
        return false;
    }

    static boolean disableVoomIncluded() {
        return false;
    }

    static boolean hideTodayTabIncluded() {
        return false;
    }

    static boolean hideShoppingTabIncluded() {
        return false;
    }

    static boolean hideVoomTabIncluded() {
        return false;
    }

    static boolean hideWalletTabIncluded() {
        return false;
    }

    static boolean isOn(String key) {
        if (!rowIncluded()) return true;
        Boolean value = VALUES.get(key);
        if (value != null) return value;
        SharedPreferences preferences = preferences();
        boolean stored = preferences == null || preferences.getBoolean(key, true);
        VALUES.put(key, stored);
        return stored;
    }

    static void set(String key, boolean on) {
        VALUES.put(key, on);
        SharedPreferences preferences = preferences();
        // Written at once, because a user can close LINE right after a change.
        if (preferences != null) preferences.edit().putBoolean(key, on).commit();
    }

    /** The string id of the row title, or 0 if the resource is missing. Called by the patch. */
    public static int titleId() {
        return identifier(TITLE_RESOURCE, "string");
    }

    /** The icon of the row. Called by the patch. */
    public static Integer iconId() {
        return identifier(ICON_RESOURCE, "drawable");
    }

    /**
     * Gives the rows of LINE's Settings list with {@code row} below the row at {@code index}, or
     * first if {@code index} is not in the list. Called by the patch, which finds the "Profile" row
     * and builds {@code row} from the "About LINE" row.
     */
    public static List<Object> insertRow(List<Object> rows, int index, Object row) {
        ArrayList<Object> result = new ArrayList<>(rows);
        result.add(index >= 0 && index < rows.size() ? index + 1 : 0, row);
        return result;
    }

    private static int identifier(String name, String type) {
        Context context = application();
        return context == null ? 0 : context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    private static SharedPreferences preferences() {
        Context context = application();
        return context == null ? null : context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static Application application() {
        try {
            return (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}

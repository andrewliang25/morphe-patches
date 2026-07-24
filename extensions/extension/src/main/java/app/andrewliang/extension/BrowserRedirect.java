package app.andrewliang.extension;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.util.Locale;

/**
 * Helper for the "Open links in external browser" LINE patch.
 *
 * Injected at the tail of LINE's in-app-browser decision point
 * (com.linecorp.browser.OpenUriActivity), which has already excluded LIFF / line:// / deep
 * links before this runs.
 */
public final class BrowserRedirect {

    private BrowserRedirect() {}

    /**
     * If {@code uri} is an http/https web link, open it in the external default browser and
     * finish the in-app browser activity.
     *
     * @return {@code true} if the link was handled externally; {@code false} for non-web
     *         schemes or on any failure, so LINE's own in-app handling proceeds unchanged.
     */
    public static boolean openExternalIfWeb(Activity activity, Uri uri) {
        if (activity == null || uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            activity.finish();
            return true;
        } catch (Exception e) {
            // No external browser available, or launch failed -> let LINE handle it in-app.
            return false;
        }
    }
}

package app.andrewliang.extension;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.net.URLEncoder;

/**
 * Helper for the "Redirect LINE Pay" patch.
 *
 * LINE's messenger cannot run its own Pay flow on a re-signed/patched build (the bundled VKey
 * V-Guard integrity check fails), so instead of merely closing the Pay screen we forward the
 * payment to the user's separately-installed, unpatched <b>standalone LINE Pay app</b>, whose
 * integrity check passes. The standalone app is opened by an {@code ACTION_VIEW} on:
 *
 * <pre>https://web-tw-pay.line.me/R/iab?url=&lt;urlencoded inner payment url&gt;</pre>
 *
 * <p>The inner url is the standalone web-payment page. A merchant "LINE Pay" checkout link is a
 * deep link of the form {@code http(s)://line.me/R/pay/payment/<reserveId>} (or the scheme form
 * {@code line://pay/payment/<reserveId>}); the last path segment IS the {@code transactionReserveId}
 * (verified: it decodes identically to the reserve id in the known-good web-pay url). We rebuild:
 *
 * <pre>https://web-pay.line.me/web/payment/waitPreLogin?transactionReserveId=&lt;reserveId&gt;&amp;locale=zh-TW_LP</pre>
 *
 * <p>{@code web-tw-pay.line.me} is not registered by the messenger, so the redirect resolves to the
 * standalone app. Best-effort: every exception is swallowed and the patch always calls
 * {@code finish()} afterwards, so a failed hand-off is never worse than the old "just close"
 * behavior. It also logs the incoming intent (tag {@code AndrewLinePay}) so the exact URL shape can
 * be confirmed on device via {@code adb logcat -s AndrewLinePay}.
 */
public final class LinePayRedirect {

    private LinePayRedirect() {}

    private static final String TAG = "AndrewLinePay";
    private static final String WRAP_PREFIX = "https://web-tw-pay.line.me/R/iab?url=";
    private static final String WEBPAY_PREFIX = "https://web-pay.line.me/web/payment/waitPreLogin?transactionReserveId=";
    private static final String LOCALE = "zh-TW_LP";
    private static final String PAYMENT_MARKER = "pay/payment/";
    private static final String KEY_LIFF_URI = "linepay.intent.extra.URI";

    /**
     * Best-effort: forward the Pay activity's incoming intent to the standalone LINE Pay app.
     * The caller must still {@code finish()} the in-app Pay screen after this returns.
     *
     * @param activity the Pay activity (PayLaunchActivity / PayLiffActivity) being intercepted.
     */
    public static void redirect(Activity activity) {
        try {
            Intent intent = activity == null ? null : activity.getIntent();
            String dataString = intent == null ? null : intent.getDataString();
            String extraUri = extraUri(intent);

            String inner = firstNonNull(buildInnerUrl(extraUri), buildInnerUrl(dataString));
            if (inner == null) {
                // Not a recognizable payment deep link (e.g. line://pay/main). Caller finishes.
                Log.i(TAG, "Pay intent on " + name(activity) + ": no reserve id, skipping redirect.");
                return;
            }

            // Token-free breadcrumb: enough to confirm the hook fired, without logging the
            // single-use payment reserve id.
            Log.i(TAG, "Redirecting " + name(activity) + " to the standalone LINE Pay app.");
            String wrapped = WRAP_PREFIX + URLEncoder.encode(inner, "UTF-8");
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(wrapped));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(view);
        } catch (Throwable t) {
            Log.w(TAG, "LINE Pay redirect failed; falling back to close.", t);
        }
    }

    /**
     * Turn an incoming pay deep link into the standalone web-payment url, or null if it isn't a
     * recognizable payment link. Handles:
     *   - an already-resolved web-pay page (returned as-is)
     *   - {@code .../pay/payment/<reserveId>} deep links (http(s)://line.me/R/... or line://pay/...)
     */
    private static String buildInnerUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        // Already the resolved web page (e.g. if a future route hands us the real url) -> use as-is.
        if ((url.startsWith("http://") || url.startsWith("https://")) && url.contains("web-pay.line.me")) {
            return url;
        }
        String reserveId = segmentAfter(url, PAYMENT_MARKER);
        if (reserveId == null || reserveId.isEmpty()) return null;
        return WEBPAY_PREFIX + reserveId + "&locale=" + LOCALE;
    }

    /** Substring after {@code marker}, trimmed at the first path/query delimiter (/ ? #). */
    private static String segmentAfter(String url, String marker) {
        int i = url.indexOf(marker);
        if (i < 0) return null;
        String rest = url.substring(i + marker.length());
        for (int k = 0; k < rest.length(); k++) {
            char c = rest.charAt(k);
            if (c == '/' || c == '?' || c == '#') return rest.substring(0, k);
        }
        return rest;
    }

    private static String extraUri(Intent intent) {
        if (intent == null) return null;
        try {
            Uri u = intent.getParcelableExtra(KEY_LIFF_URI);
            return u == null ? null : u.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static String name(Activity activity) {
        return activity == null ? "null" : activity.getClass().getName();
    }
}

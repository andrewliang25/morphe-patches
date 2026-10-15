package app.andrewliang.extension;

import android.util.Log;

/**
 * Probe for TOPIC. Local only: never push, delete before the PR.
 * Copy to extensions/extension/src/main/java/app/andrewliang/extension/<Topic>Probe.java.
 */
public final class TopicProbe {

    private TopicProbe() {}

    /** One tag per probe, so `adb logcat -s AndrewTopic` shows only this probe. */
    private static final String TAG = "AndrewTopic";

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getName() + " " + value;
    }

    public static void entry(Object value) {
        Log.d(TAG, "entry: " + describe(value));
    }

    public static void result(Object value) {
        Log.d(TAG, "result: " + describe(value));
    }

    /** Logs who called the hooked method. Use it once, on the site whose caller is unknown. */
    public static void caller(Object value) {
        Log.d(TAG, "caller of: " + describe(value), new Throwable("caller"));
    }
}

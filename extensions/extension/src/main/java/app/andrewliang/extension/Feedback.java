package app.andrewliang.extension;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Tells the user what happened.
 *
 * <p>A download takes seconds and happens away from the screen, so without a word from it a tap
 * looks like it did nothing and the user taps again.
 *
 * <p>The text is English and is written here rather than taken from the app. Facebook keeps the
 * words for its own menus in a pack that it downloads, not in the resources of the APK, so there
 * is nothing to borrow. Translating a few of the languages by hand and leaving the rest would read
 * worse than one language used consistently, and anyone who installed this patch has already read
 * its English name and description.
 */
final class Feedback {

    private Feedback() {}

    private static final String TAG = MediaDownload.TAG;

    static void show(Context applicationContext, String text, boolean longToast) {
        if (applicationContext == null || text == null) return;

        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                // This is the one place where this patch runs on the thread that draws the app,
                // so it carries its own guard. An exception thrown from here would reach the
                // looper and take Facebook down with it.
                try {
                    Toast.makeText(
                        applicationContext,
                        text,
                        longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT
                    ).show();
                } catch (Throwable t) {
                    Log.w(TAG, "could not show a message", t);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "could not reach the main thread", t);
        }
    }
}

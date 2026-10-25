package app.andrewliang.extension;

import android.content.Context;
import android.util.Log;

/**
 * The handler behind the Download row this patch adds to a reel's More sheet.
 *
 * <p>Unlike the sidebar's button factory, the More sheet's row takes a single click callback with
 * no slot multiplexing, so this needs none of {@link ReelDownload}'s slot filtering. The click
 * interface itself is not known at compile time — it is an obfuscated type the patch discovers at
 * patch time by shape, so the patch adds that interface to this class with dexlib2 and bridges its
 * one method to {@link #run()} rather than this class implementing it directly.
 */
public final class ReelMenuDownload implements Runnable {

    private static final String TAG = "AndrewFbSave";

    /** The player state object the More sheet's row lookup answered. Its source holds the addresses. */
    private final Object playerParams;

    private final Context context;

    private final String hdField;
    private final String sdField;
    private final String manifestField;

    public ReelMenuDownload(
        Object playerParams,
        Context context,
        String hdField,
        String sdField,
        String manifestField
    ) {
        this.playerParams = playerParams;
        this.context = context;
        this.hdField = hdField;
        this.sdField = sdField;
        this.manifestField = manifestField;
    }

    @Override
    public void run() {
        try {
            Log.i(TAG, "reel menu download tapped");

            Object source = ReelDownload.sourceOf(playerParams);

            if (source == null) {
                Log.w(TAG, "the player of this reel holds no source");
                return;
            }

            MediaDownload.saveVideo(context, source, hdField, sdField, manifestField);
        } catch (Throwable t) {
            // Nothing can leave this method. It runs on the thread that draws, inside the app's
            // own click dispatch, so a throw here ends the app rather than the download.
            Log.w(TAG, "the reel menu handler failed", t);
        }
    }
}

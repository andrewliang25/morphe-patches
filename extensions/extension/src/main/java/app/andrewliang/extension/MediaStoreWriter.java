package app.andrewliang.extension;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a fetched file into the gallery of the device.
 *
 * <p>The target of these patches declares {@code minSdk 30}, so scoped storage is the only storage
 * there is and MediaStore is the only way in. Nothing here needs a permission: from API 29 an app
 * may insert media that it creates without asking for one.
 *
 * <p>The entry is created with {@code IS_PENDING} set and is only published once the last byte has
 * arrived. So a failed fetch never leaves a playable looking file of the wrong length in the
 * gallery, and a fetch that the system kills half way leaves a pending row that the platform
 * clears by itself after about a week.
 */
final class MediaStoreWriter implements Downloader.Sink {

    private static final String TAG = MediaDownload.TAG;

    /** Where the files go. One folder, so that the two halves of the feature land together. */
    private static final String FOLDER = "Facebook";

    private final Context context;
    private final boolean video;

    private Uri item;
    private OutputStream stream;
    private String location;

    MediaStoreWriter(Context applicationContext, boolean video) {
        this.context = applicationContext;
        this.video = video;
    }

    /** {@code Movies/Facebook} or {@code Pictures/Facebook}, for the message to the user. */
    String savedLocation() {
        return location;
    }

    @Override
    public OutputStream open(String mimeFromServer) throws IOException {
        String mime = mime(mimeFromServer);
        String directory = video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
        location = directory + "/" + FOLDER;

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name(mime));
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        // No leading slash. MediaStore checks this against its own directory names.
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, location);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = video
            ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        ContentResolver resolver = context.getContentResolver();
        item = resolver.insert(collection, values);
        if (item == null) throw new IOException("the gallery refused a new entry");

        stream = resolver.openOutputStream(item, "w");
        if (stream == null) throw new IOException("the gallery gave no way to write");

        return stream;
    }

    @Override
    public void commit() throws IOException {
        close();

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(item, values, null, null);
    }

    @Override
    public void abandon() {
        close();

        if (item == null) return;

        try {
            context.getContentResolver().delete(item, null, null);
        } catch (Throwable t) {
            Log.w(TAG, "could not remove the unfinished entry", t);
        } finally {
            item = null;
        }
    }

    private void close() {
        if (stream == null) return;

        try {
            stream.close();
        } catch (Throwable ignored) {
            // The bytes are already written or already lost.
        } finally {
            stream = null;
        }
    }

    /**
     * The type to record, taken from the server.
     *
     * <p>The type decides the name, and never the other way round. Facebook itself gets this
     * wrong: its own save writes AVIF bytes into a file called {@code .jpg}, which leaves the
     * gallery unable to draw a thumbnail for it. Copying that would be copying the fault.
     */
    private String mime(String fromServer) {
        if (fromServer != null && EXTENSIONS.containsKey(fromServer)) {
            boolean isVideo = fromServer.startsWith("video/");
            // A video request that answers with a picture, or the reverse, is an error page or a
            // thumbnail. Saving it would look like success.
            if (isVideo == video) return fromServer;
        }

        return video ? "video/mp4" : "image/jpeg";
    }

    private String name(String mime) {
        String suffix = EXTENSIONS.get(mime);
        if (suffix == null) suffix = video ? ".mp4" : ".jpg";

        // Facebook's own naming, so that files from this patch and from Facebook sit together.
        String prefix = video ? "FB_VID_" : "FB_IMG_";

        // The locale has to be fixed. Under a Thai or an Arabic locale the default calendar
        // writes Buddhist years or Eastern Arabic digits into the file name.
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        return prefix + stamp + suffix;
    }

    /**
     * The types worth recording, and the name that each one takes.
     *
     * <p>{@code MimeTypeMap} is not used. It answers nothing for {@code image/avif} on many
     * devices and its table is different from one ROM to the next.
     */
    private static final Map<String, String> EXTENSIONS = new HashMap<>();

    static {
        EXTENSIONS.put("video/mp4", ".mp4");
        EXTENSIONS.put("video/x-m4v", ".m4v");
        EXTENSIONS.put("video/quicktime", ".mov");
        EXTENSIONS.put("video/webm", ".webm");
        EXTENSIONS.put("video/3gpp", ".3gp");
        EXTENSIONS.put("image/jpeg", ".jpg");
        EXTENSIONS.put("image/png", ".png");
        EXTENSIONS.put("image/webp", ".webp");
        EXTENSIONS.put("image/heic", ".heic");
        EXTENSIONS.put("image/heif", ".heif");
        EXTENSIONS.put("image/avif", ".avif");
        EXTENSIONS.put("image/gif", ".gif");
    }
}

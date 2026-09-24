package app.andrewliang.extension;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Saves the picture or the video that the app is showing, without asking the app to do it.
 *
 * <p>Facebook ships a save feature and refuses to run it for most content. The refusal is not
 * about the address: the story or the reel is on the screen, so the app holds an address that it
 * can fetch. This takes that address and fetches it.
 *
 * <p>For a story that is the point of the whole thing. Facebook's own save runs a check for
 * licensed music first. On a story that has any, it shows a warning and then saves nothing,
 * whatever the user answers (issue #110). The patch replaces the body of that handler, so the
 * check never runs and the video arrives complete, with its sound.
 *
 * <h2>What the patch hands over</h2>
 *
 * <p>The object that holds the media, and nothing else. Each entry point is called with the object
 * of the item that the user tapped, so the file saved is always the item on the screen. There is
 * deliberately <b>no static holding the last address seen</b>. Facebook prepares the next reels
 * while the current one plays, and a measurement counted six of them built in about fifteen
 * seconds of scrolling. Anything remembered rather than passed in saves the wrong video, and
 * still reports success.
 *
 * <p>Capture the log with {@code adb logcat -s AndrewFbSave}.
 */
public final class MediaDownload {

    private MediaDownload() {}

    static final String TAG = "AndrewFbSave";

    /** Scoped storage arrived in API 29, and with it the only way this can write anything. */
    private static final int MIN_SDK = 29;

    /** A guard against a rapid tap, not a work queue. */
    private static final int MAX_IN_FLIGHT = 3;

    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();

    /**
     * Save the media of the story that is open.
     *
     * <p>[host] is the story card. Its address is read by value, because the fields that hold it
     * are renamed on every release of the app while the addresses in them keep their shape.
     *
     * @return whether a download started. {@code false} lets the caller fall back to the app.
     */
    public static boolean saveStory(Context context, Object host) {
        try {
            List<String> urls = collectStoryUrls(host);

            // The card holds one video address, and it is 360p. The player of the same video can
            // hold better, so its recorded source is tried first.
            PlayerSources.Source source = PlayerSources.find(host);
            if (source != null) {
                addIfUsable(urls, source.hdUrl);
                if (beginDash(context, source, urls)) return true;
            }

            return begin(context, urls);
        } catch (Throwable t) {
            // Throwable and not Exception. A renamed field surfaces as NoSuchFieldError, and a
            // reflective call on a changed class surfaces as a LinkageError. Neither is an
            // Exception, and either one that reaches Facebook's click handler ends the app.
            Log.w(TAG, "the story save could not start", t);
            return false;
        }
    }

    /**
     * Save the video that the player is streaming.
     *
     * <p>[hdField] and [sdField] are the real names of the two fields of the source that hold a
     * single file address. The patch reads those names out of the app while patching, so this
     * file names no field of its own and neither does the patch.
     *
     * <p>Asking by name matters here in a way that it does not for a story. The source carries a
     * third address of the same type, and it holds the subtitles. So "the first address on the
     * object" is a real way to save the wrong thing.
     *
     * @return whether a download started. {@code false} lets the caller fall back to the app.
     */
    public static boolean saveVideo(Context context, Object host, String hdField, String sdField) {
        try {
            return begin(context, collectVideoUrls(host, hdField, sdField));
        } catch (Throwable t) {
            Log.w(TAG, "the video save could not start", t);
            return false;
        }
    }

    // ---------------------------------------------------------------- internals

    /** The named fields first, then whatever else the object can reach. */
    private static List<String> collectVideoUrls(Object host, String hdField, String sdField) {
        List<String> urls = new ArrayList<>();

        addIfUsable(urls, RenditionPicker.fieldValue(host, hdField));
        addIfUsable(urls, RenditionPicker.fieldValue(host, sdField));

        // Either the object is not the source itself, or the release moved the fields. Ranking
        // by value still answers, and it is the reason this is not only a pair of reads.
        if (urls.isEmpty()) urls.addAll(RenditionPicker.harvest(host, 1));

        return urls;
    }

    /**
     * Every address the story card can reach.
     *
     * <p>{@code getMedia} is one of the few names on this class that Facebook keeps, so it is
     * worth trying before the walk. When it answers, the walk starts from the media rather than
     * from the card, which keeps it away from everything else the card holds.
     */
    private static List<String> collectStoryUrls(Object host) {
        Object media = call(host, "getMedia");
        Object from = media != null ? media : host;

        return RenditionPicker.harvest(from, media != null ? 1 : 2);
    }

    private static void addIfUsable(List<String> urls, String url) {
        if (url != null && RenditionPicker.isHttpUrl(url)) urls.add(url);
    }

    private static Object call(Object host, String method) {
        if (host == null) return null;

        try {
            return host.getClass().getMethod(method).invoke(host);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Pick the best address of those found and fetch it.
     *
     * <p>This ranks the item both ways and keeps the better answer. It does not read the type of
     * the item from the app. The app records that type in an enum whose constants move between
     * releases, and one constant mistaken for another saves the wrong file without a word.
     */
    private static boolean begin(Context context, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            Log.w(TAG, "nothing to save: the item carried no address");
            return false;
        }

        String video = RenditionPicker.bestOf(urls, true);
        String image = RenditionPicker.bestOf(urls, false);

        boolean isVideo = video != null;
        String chosen = isVideo ? video : image;

        if (chosen == null) {
            Log.w(TAG, "nothing to save: none of the " + urls.size() + " addresses was a file");
            return false;
        }

        Context safe = ready(context);
        if (safe == null) return false;

        // Every candidate, so a saved file that is smaller than expected can be told apart from
        // a ranking that chose badly. Names and sizes only: a whole address is a signed, working
        // handle to the user's content, and the log can be read by anything else on the device.
        StringBuilder all = new StringBuilder();
        for (String url : urls) {
            if (all.length() > 0) all.append(", ");
            all.append(describe(url));
        }

        Log.i(TAG, "saving " + (isVideo ? "video" : "image")
            + " " + describe(chosen)
            + " from " + urls.size() + " candidate(s): " + all);

        start(safe, isVideo, writer -> Downloader.fetch(chosen, writer));
        return true;
    }

    /**
     * Save the best video track and audio track of the player's DASH manifest, when that beats
     * every single-file address the item holds.
     *
     * <p>The manifest lists renditions that no single-file address offers: a story whose card and
     * player both hold only 360p files lists tracks up to 1080p. The two tracks are joined into one
     * file on the device. If that fails, the best single-file address is saved instead, so the
     * user still gets a file.
     *
     * @return whether a download started. {@code false} lets the caller save a single file.
     */
    private static boolean beginDash(Context context, PlayerSources.Source source, List<String> urls) {
        List<DashManifest.Track> tracks = DashManifest.parse(source.manifest);
        DashManifest.Track video = DashManifest.bestVideo(tracks, DashSave.canWriteAv1());

        if (video == null) {
            if (source.manifest != null) {
                Log.i(TAG, "the manifest of video " + source.videoId + " has no track to save: "
                    + tracks);
            }
            return false;
        }

        String fallback = RenditionPicker.bestOf(urls, true);
        int fallbackQuality = fallback == null ? 0 : RenditionPicker.qualityOf(fallback);

        if (video.shortSide() <= fallbackQuality) return false;

        Context safe = ready(context);
        if (safe == null) return false;

        DashManifest.Track audio = DashManifest.bestAudio(tracks);

        Log.i(TAG, "saving video " + source.videoId + " from its DASH manifest: " + video
            + (audio == null ? ", no sound track" : " + " + audio)
            + ", instead of " + (fallback == null ? "nothing" : describe(fallback)));

        start(safe, true, writer -> {
            Downloader.Status status = DashSave.save(safe, video, audio, writer);
            if (status == Downloader.Status.OK || fallback == null) return status;

            Log.w(TAG, "the DASH save ended with " + status + ", saving " + describe(fallback));
            return Downloader.fetch(fallback, writer);
        });
        return true;
    }

    /**
     * The context to save with, or {@code null} when a save cannot start now.
     *
     * <p>Never the Activity. A download outlives the screen that started it, and holding the
     * Activity across it is a leak, and Facebook's own tooling reports it.
     */
    private static Context ready(Context context) {
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            // The module is shared with the LINE patches and declares an older floor than this
            // needs, so the check belongs here rather than in the manifest.
            Log.w(TAG, "saving needs Android 10 or newer");
            return null;
        }

        if (context == null) return null;

        if (IN_FLIGHT.get() >= MAX_IN_FLIGHT) {
            Log.w(TAG, "too many saves at once");
            return null;
        }

        Context application = context.getApplicationContext();
        return application != null ? application : context;
    }

    /** One save, run on the worker thread. It writes through [writer] and reports how it went. */
    private interface Job {
        Downloader.Status run(MediaStoreWriter writer);
    }

    private static void start(Context application, boolean video, Job job) {
        IN_FLIGHT.incrementAndGet();
        Feedback.show(application, "Saving...", false);

        Thread worker = new Thread(() -> {
            MediaStoreWriter writer = new MediaStoreWriter(application, video);

            try {
                Downloader.Status status = job.run(writer);
                Log.i(TAG, "save finished: " + status);
                Feedback.show(application, message(status, writer.savedLocation()), status != Downloader.Status.OK);
            } catch (Throwable t) {
                // Nothing can leave this thread. Facebook installs its own handler for uncaught
                // exceptions and reports them as its own crashes.
                Log.w(TAG, "the save failed", t);
                Feedback.show(application, "Download failed", true);
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        }, "andrew-fb-save");

        // A thread that ends when the copy ends leaves nothing behind in a process that is not
        // ours. A pool parks a thread there for as long as Facebook runs.
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private static String message(Downloader.Status status, String location) {
        switch (status) {
            case OK:
                return "Saved to " + (location == null ? "the gallery" : location);
            case EXPIRED:
                return "Link expired - reopen the item and try again";
            default:
                return "Download failed";
        }
    }

    /**
     * Enough of an address to recognise it in a log, and no more.
     *
     * <p>A whole address is a signed, working handle to the content of the user, and the log can
     * be read by anything else on the device.
     */
    private static String describe(String url) {
        int query = url.indexOf('?');
        String withoutQuery = query < 0 ? url : url.substring(0, query);

        int slash = withoutQuery.lastIndexOf('/');
        String file = slash < 0 ? withoutQuery : withoutQuery.substring(slash + 1);

        return file + " (" + RenditionPicker.qualityOf(url) + "p)";
    }
}

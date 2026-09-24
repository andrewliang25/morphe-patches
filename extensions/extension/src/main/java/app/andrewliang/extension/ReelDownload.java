package app.andrewliang.extension;

import android.content.Context;
import android.util.Log;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * The handler behind a download button added to the reel sidebar.
 *
 * <p>The sidebar builds each of its buttons through one factory of the app, and that factory takes
 * its handlers as {@code Function1}. The reference bundle had no extension of its own, so it had to
 * borrow one of the app's multiplexed lambdas and pick unused case numbers inside it. The module
 * here already carries the Kotlin standard library, so this implements {@code Function1} directly
 * and is passed straight in. Nothing of the app is modified to hold it.
 *
 * <p>The object carries the player of the item that the button belongs to, so the file saved is
 * always the reel on the screen. The app prepares the reels that come next, so a handler that
 * reads a shared place instead saves the wrong one.
 *
 * <h2>Slots</h2>
 *
 * <p>The factory takes several handlers and only one of them is the tap. Which one is not written
 * anywhere, and the render of the button cannot settle it either, so a run answered it. Every slot
 * got one of these with its own number, and the events they received named them. The touch
 * slot fires twice per press with a {@code MotionEvent}. A visibility slot fires on its own. One
 * slot fires once per press with an event that carries nothing but the {@code View}. That last one
 * is the tap, and it is the only slot that saves. The others are kept rather than left null. The
 * factory is not documented to accept null, and a silent handler costs nothing.
 */
public final class ReelDownload implements Function1<Object, Unit> {

    private static final String TAG = "AndrewFbSave";

    /** The player params of this item. Its source holds the addresses. */
    private final Object playerParams;

    private final Context context;

    /** Which handler of the factory this one was passed as. Diagnostic. */
    private final int slot;

    /** Whether this slot saves, or only reports that it ran. */
    private final boolean saves;

    /**
     * The real names of the two address fields of the source.
     *
     * <p>The patch reads them out of the app while patching and hands them over. The app renames
     * its fields on every release. The names that it reports for them do not change.
     */
    private final String hdField;
    private final String sdField;

    /** The real name of the field that holds the DASH manifest, read the same way. */
    private final String manifestField;

    public ReelDownload(
        Object playerParams,
        Context context,
        String hdField,
        String sdField,
        String manifestField,
        int slot,
        boolean saves
    ) {
        this.playerParams = playerParams;
        this.context = context;
        this.hdField = hdField;
        this.sdField = sdField;
        this.manifestField = manifestField;
        this.slot = slot;
        this.saves = saves;
    }

    @Override
    public Unit invoke(Object argument) {
        try {
            // Only the tap slot does anything. The others get the same object, so that no
            // parameter of the factory is null, and they return without a word. They fire on every
            // touch and every visibility change, which buries the log that this feature needs.
            if (!saves) return Unit.INSTANCE;

            Log.i(TAG, "reel download tapped"
                + (argument == null ? "" : ", event " + argument.getClass().getName()));

            save();
        } catch (Throwable t) {
            // Nothing can leave this method. It runs on the thread that draws, inside the app's
            // own click dispatch, so a throw here ends the app rather than the download.
            Log.w(TAG, "the reel handler failed", t);
        }

        return Unit.INSTANCE;
    }

    private void save() {
        Object source = sourceOf(playerParams);

        if (source == null) {
            Log.w(TAG, "the player of this reel holds no source");
            return;
        }

        MediaDownload.saveVideo(context, source, hdField, sdField, manifestField);
    }

    /**
     * The source of the player, found by type rather than by name.
     *
     * <p>The params object holds exactly one field of the source type, and that type is a name that
     * Redex keeps, so no letter has to be written down here.
     */
    private static Object sourceOf(Object params) {
        if (params == null) return null;

        // The sidebar holds a rich params object, which holds the plain one, which holds the
        // source. Walking by type keeps all three of those names out of this file.
        Object current = params;

        for (int depth = 0; depth < 3 && current != null; depth++) {
            Object next = null;

            for (java.lang.reflect.Field field : current.getClass().getDeclaredFields()) {
                try {
                    String type = field.getType().getName();

                    if (type.equals("com.facebook.video.engine.api.VideoDataSource")) {
                        field.setAccessible(true);
                        return field.get(current);
                    }

                    if (type.equals("com.facebook.video.engine.api.VideoPlayerParams")) {
                        field.setAccessible(true);
                        next = field.get(current);
                    }
                } catch (Throwable ignored) {
                    // One unreadable field must not end the walk.
                }
            }

            current = next;
        }

        return null;
    }

}

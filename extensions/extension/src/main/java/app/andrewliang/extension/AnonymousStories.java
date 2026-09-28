package app.andrewliang.extension;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Helper for the Facebook "[Stories] View stories anonymously" patch.
 *
 * <p>Facebook marks a story as seen on the device only when the server answers the seen report.
 * The patch stops that report, so this class keeps the seen state on the device instead:
 *
 * <ol>
 *   <li>{@link #markCardSeen} runs when the viewer shows a story card. When you saw each card of
 *       the stories of one person (a bucket), the bucket is kept with its time stamp.
 *   <li>{@link #isSeen} replaces each read of a seen field. It returns the value from the server,
 *       or {@code true} for a kept card or bucket. A bucket stays seen only while its time stamp
 *       does not change, so a new story shows as new again.
 * </ol>
 *
 * <p>A story is live for 24 hours, so an entry goes after 48 hours.
 */
public final class AnonymousStories {

    private AnonymousStories() {}

    private static final String TAG = "AndrewFbStories";

    private static final String PREFERENCES = "andrew_anonymous_stories";

    private static final long KEEP_MS = 48L * 60 * 60 * 1000;

    /** Facebook reads a tree field by the {@code hashCode} of its name. */
    private static final int ID_FIELD = "id".hashCode();

    private static final int BUCKET_FIELD = "is_bucket_seen_by_viewer".hashCode();

    /** The time stamp of a bucket in the story tray. It changes when the person posts a story. */
    private static final int BUCKET_TIME_FIELD = 767170141;

    private static final String CARD = "c:";

    private static final String BUCKET = "b:";

    private static final String TIME = "t:";

    private static final String STORY_CARD = "com.facebook.stories.model.StoryCard";

    /** Kept entries: "c:" + card id, or "b:" + bucket id, to the time that it was saved. */
    private static final Map<String, Long> SAVED = new HashMap<>();

    /** "b:" + bucket id to the tray time stamp of that bucket when it was saved. */
    private static final Map<String, Long> BUCKET_TIMES = new HashMap<>();

    /** The last tray time stamp read for each bucket id, in this process. */
    private static final Map<String, Long> TRAY_TIMES = new HashMap<>();

    private static boolean loaded;

    private static Method getBooleanValue;

    private static Method getString;

    private static Method getTimeValue;

    /** Called when the story viewer shows {@code card} of {@code bucket}. */
    public static void markCardSeen(Object bucket, Object card) {
        try {
            String cardId = id(card);
            String bucketId = id(bucket);
            if (cardId == null || bucketId == null) return;

            long now = System.currentTimeMillis();
            synchronized (SAVED) {
                load();
                SAVED.put(CARD + cardId, now);
                if (allCardsSeen(bucket)) {
                    Long trayTime = TRAY_TIMES.get(bucketId);
                    SAVED.put(BUCKET + bucketId, now);
                    BUCKET_TIMES.put(BUCKET + bucketId, trayTime == null ? 0L : trayTime);
                }
                save(now);
            }
        } catch (Throwable t) {
            Log.e(TAG, "markCardSeen failed", t);
        }
    }

    /**
     * Replaces {@code tree.getBooleanValue(hash)} for a seen field. If a step fails, the value from
     * the server stands, so the patch never shows a seen story as new.
     */
    public static boolean isSeen(Object tree, int hash) {
        boolean server = false;
        try {
            if (getBooleanValue == null) {
                getBooleanValue = find(tree.getClass(), "getBooleanValue", int.class);
                getString = find(tree.getClass(), "getString", int.class);
                getTimeValue = find(tree.getClass(), "getTimeValue", int.class);
            }
            server = (Boolean) getBooleanValue.invoke(tree, hash);
            Object id = getString.invoke(tree, ID_FIELD);
            if (id == null) return server;

            synchronized (SAVED) {
                load();
                if (hash != BUCKET_FIELD) return server || SAVED.containsKey(CARD + id);

                long time = (Long) getTimeValue.invoke(tree, BUCKET_TIME_FIELD);
                TRAY_TIMES.put(id.toString(), time);
                if (server) return true;

                Long savedTime = BUCKET_TIMES.get(BUCKET + id);
                if (savedTime == null) return false;
                if (savedTime == 0L) {
                    // Saved before the tray read this bucket: this read shows the stories you saw.
                    BUCKET_TIMES.put(BUCKET + id, time);
                    save(System.currentTimeMillis());
                    return true;
                }
                return time <= savedTime;
            }
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                Log.e(TAG, "isSeen failed", t);
            }
            return server;
        }
    }

    private static boolean failureLogged;

    /**
     * True when each card of the bucket is saved. The bucket model lists its cards in the one
     * no-argument getter that returns a list of story cards. Its name changes with each release,
     * so it is found by what it returns.
     */
    private static boolean allCardsSeen(Object bucket) throws Exception {
        for (Class<?> c = bucket.getClass(); c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0) continue;
                if (!List.class.isAssignableFrom(method.getReturnType())) continue;
                method.setAccessible(true);
                Object value = method.invoke(bucket);
                if (!(value instanceof List) || ((List<?>) value).isEmpty()) continue;
                if (!isStoryCard(((List<?>) value).get(0))) continue;

                for (Object card : (List<?>) value) {
                    String cardId = id(card);
                    if (cardId == null) return false;
                    if (!SAVED.containsKey(CARD + cardId) && !isCardSeen(card)) return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * The seen state of a story card, from the card itself. It is true for a card that the server
     * already marked as seen, so the viewer did not show it again. The patch replaces this body
     * with a call to the seen getter of the story card, whose name changes with each release.
     */
    public static boolean isCardSeen(Object card) {
        return false;
    }

    private static boolean isStoryCard(Object value) {
        if (value == null) return false;
        for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(STORY_CARD)) return true;
        }
        return false;
    }

    /** The result of the kept {@code getId()} of a story bucket or card. */
    private static String id(Object model) throws Exception {
        Object id = find(model.getClass(), "getId").invoke(model);
        return id == null ? null : id.toString();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        SharedPreferences preferences = preferences();
        if (preferences == null) return;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!(entry.getValue() instanceof Long)) continue;
            if (key.startsWith(TIME)) {
                BUCKET_TIMES.put(key.substring(TIME.length()), (Long) entry.getValue());
            } else {
                SAVED.put(key, (Long) entry.getValue());
            }
        }
    }

    private static void save(long now) {
        Iterator<Map.Entry<String, Long>> iterator = SAVED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > KEEP_MS) {
                BUCKET_TIMES.remove(entry.getKey());
                iterator.remove();
            }
        }
        SharedPreferences preferences = preferences();
        if (preferences == null) return;
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, Long> entry : SAVED.entrySet()) {
            editor.putLong(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : BUCKET_TIMES.entrySet()) {
            editor.putLong(TIME + entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private static SharedPreferences preferences() {
        try {
            Application application = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            return application == null
                    ? null
                    : application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }
}

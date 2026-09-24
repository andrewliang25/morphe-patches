package app.andrewliang.extension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The source of each player that the app builds, kept by video id.
 *
 * <p>A story card holds one video address, and it is 360p. The player of the same story holds a
 * DASH manifest that lists every rendition, up to 1080p. The save action holds the card and cannot
 * reach the player. The card does carry the video id of the player, so the patch records every
 * source here by that id, and the save looks it up.
 *
 * <p>The key is the reason this is safe. The app builds the players of the next items early, so a
 * record of "the last source built" saves the wrong video. A record keyed by video id answers for
 * the item that was tapped, or does not answer at all.
 */
public final class PlayerSources {

    private PlayerSources() {}

    /** The fields of one source that a save can use. */
    static final class Source {
        final String videoId;
        final String hdUrl;
        final String manifest;

        Source(String videoId, String hdUrl, String manifest) {
            this.videoId = videoId;
            this.hdUrl = hdUrl;
            this.manifest = manifest;
        }
    }

    private static final String VIDEO_DATA_SOURCE = "com.facebook.video.engine.api.VideoDataSource";

    /**
     * How many sources to keep. A manifest is about 20 KB of text, so this holds about 1 MB. It is
     * much more than the players the app prepares ahead of the one on the screen.
     */
    private static final int MAX_SOURCES = 48;

    /** A video id: a long run of digits. */
    private static final Pattern ID = Pattern.compile("\\d{8,20}");

    /** The fence of the walk over a card, the same as the walk that finds its addresses. */
    private static final int MAX_NODES = 512;
    private static final int MAX_DEPTH = 3;

    private static final LinkedHashMap<String, Source> SOURCES =
        new LinkedHashMap<String, Source>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Source> eldest) {
                return size() > MAX_SOURCES;
            }
        };

    /**
     * Record the source of one player. The patch calls this at the end of every constructor of the
     * player params, so it runs often and on any thread, and must never throw.
     *
     * <p>The three names are the real names of the fields, which the patch reads from the app.
     */
    public static void remember(Object params, String idField, String hdField, String manifestField) {
        try {
            String videoId = RenditionPicker.fieldValue(params, idField);
            if (videoId == null || videoId.isEmpty()) return;

            Object source = fieldOfType(params, VIDEO_DATA_SOURCE);
            if (source == null) return;

            String hd = RenditionPicker.fieldValue(source, hdField);
            String manifest = RenditionPicker.fieldValue(source, manifestField);
            if (hd == null && manifest == null) return;

            synchronized (SOURCES) {
                SOURCES.put(videoId, new Source(videoId, hd, manifest));
            }
        } catch (Throwable ignored) {
            // This runs inside a constructor of the app. Nothing can leave it.
        }
    }

    /**
     * The source of the video that [card] shows, or {@code null}.
     *
     * <p>The card is walked for every string that has the shape of an id, and the first one that
     * names a recorded player wins. Only players are recorded, so an id that matches is the id of
     * a video, and the card shows one video.
     */
    static Source find(Object card) {
        if (card == null) return null;

        List<String> ids = idsIn(card);

        synchronized (SOURCES) {
            for (String id : ids) {
                Source source = SOURCES.get(id);
                if (source != null) return source;
            }
        }

        return null;
    }

    // ---------------------------------------------------------------- internals

    private static List<String> idsIn(Object host) {
        List<String> found = new ArrayList<>();

        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        Deque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[] { host, 0 });
        seen.put(host, Boolean.TRUE);

        int nodes = 0;
        while (!queue.isEmpty() && nodes < MAX_NODES) {
            Object[] entry = queue.poll();
            Object node = entry[0];
            int depth = (Integer) entry[1];
            nodes++;

            for (Class<?> type = node.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                Field[] fields;
                try {
                    fields = type.getDeclaredFields();
                } catch (Throwable t) {
                    continue;
                }

                for (Field field : fields) {
                    if (Modifier.isStatic(field.getModifiers())) continue;

                    Object value;
                    try {
                        field.setAccessible(true);
                        value = field.get(node);
                    } catch (Throwable t) {
                        continue;
                    }
                    if (value == null) continue;

                    if (value instanceof CharSequence) {
                        addIds(found, value.toString());
                        continue;
                    }

                    if (depth >= MAX_DEPTH) continue;

                    String name = value.getClass().getName();
                    if (!name.startsWith("com.facebook.") && !name.startsWith("X.")) continue;
                    if (seen.put(value, Boolean.TRUE) != null) continue;

                    queue.add(new Object[] { value, depth + 1 });
                }
            }
        }

        return found;
    }

    /**
     * The ids in a short string. An address is skipped, because its path and its query carry long
     * runs of digits that are not ids.
     */
    private static void addIds(List<String> ids, String text) {
        if (text.length() > 40) return;

        Matcher matcher = ID.matcher(text);
        while (matcher.find()) {
            String id = matcher.group();
            if (!ids.contains(id)) ids.add(id);
        }
    }

    private static Object fieldOfType(Object host, String typeName) {
        for (Class<?> type = host.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.getType().getName().equals(typeName)) continue;
                try {
                    field.setAccessible(true);
                    return field.get(host);
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        return null;
    }
}

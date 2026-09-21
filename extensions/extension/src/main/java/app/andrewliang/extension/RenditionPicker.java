package app.andrewliang.extension;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the best media address that an object holds, and ranks addresses against each other.
 *
 * <p>Two things shape this class.
 *
 * <p>First, <b>no Android type appears anywhere in it</b>. Every method takes and answers a
 * {@code String}, an {@code int} or a plain object, and a {@code Uri} becomes a {@code String}
 * through {@code String.valueOf}. Thus the whole file compiles and runs with {@code javac} alone,
 * and {@code work/Renditions.java} checks the ranking on a fixed set of addresses with no device
 * and no Android jar. The ranking is the part that is easy to get wrong and impossible to see on a
 * device, so it is the part that gets a test.
 *
 * <p>Second, <b>Facebook renames the fields of its classes on every release</b> while the values
 * inside them keep their shape. So this class reads values and never names. It collects every
 * address that an object can reach and then asks which one looks best, rather than asking for the
 * field that held the best one last time.
 *
 * <p>The caller that knows a field name by other means should read that field directly with
 * {@link #fieldValue} and only fall back to {@link #harvest}. The video path does exactly that: the
 * patch reads the real field names out of the app itself, so it can ask for the high quality
 * address by name. The story path has no such source and ranks by value.
 */
final class RenditionPicker {

    private RenditionPicker() {}

    /** A progressive file: something that a single GET returns whole. */
    static final int TIER_PROGRESSIVE = 2;

    /** Plausible, but with nothing to say it is a file rather than a manifest. A last resort. */
    static final int TIER_PLAUSIBLE = 1;

    /** Rejected. */
    static final int TIER_NONE = 0;

    /** Stop the walk before it can wander into the app. */
    private static final int MAX_NODES = 512;

    /** Longer than any real address, and a guard against walking into a serialised blob. */
    private static final int MAX_URL = 4096;

    // ---------------------------------------------------------------- ranking (pure)

    /** Whether this text is an address that {@code HttpURLConnection} can fetch. */
    static boolean isHttpUrl(String text) {
        if (text == null) return false;
        if (text.length() < 11 || text.length() > MAX_URL) return false;

        String lower = text.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;

        // A real address holds no space. Inline XML that begins with a URL would.
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) return false;
        }

        return true;
    }

    /**
     * Whether this text describes where the pieces of a video are, rather than being the video.
     *
     * <p>A manifest is the one answer that looks right and is useless: fetching it returns a few
     * kilobytes of XML named {@code .mp4} by the time anyone notices.
     */
    static boolean isManifest(String text) {
        if (text == null) return true;

        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("<mpd") || lower.contains("<?xml")) return true;

        String path = pathOf(lower);
        return path.endsWith(".mpd")
            || path.endsWith(".m3u8")
            || path.endsWith(".m3u")
            || path.endsWith(".ism")
            || path.endsWith(".ismc");
    }

    /** How much this address looks like a video file. See the {@code TIER_} constants. */
    static int videoTier(String url) {
        if (!isHttpUrl(url) || isManifest(url)) return TIER_NONE;

        String lower = url.toLowerCase(Locale.US);
        String path = pathOf(lower);

        if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov")) {
            return TIER_PROGRESSIVE;
        }

        // Facebook marks the single file variants of a video with this word. The measured
        // addresses of a reel carry it even when the path has no suffix.
        if (lower.contains("progressive")) return TIER_PROGRESSIVE;

        return isFacebookHost(lower) ? TIER_PLAUSIBLE : TIER_NONE;
    }

    /** How much this address looks like a full size picture rather than a thumbnail. */
    static int imageTier(String url) {
        if (!isHttpUrl(url) || isManifest(url)) return TIER_NONE;

        String lower = url.toLowerCase(Locale.US);

        // Facebook asks for a thumbnail by putting the size into the address. A saved thumbnail
        // looks like a success and is worthless, so these lose outright rather than rank low.
        if (lower.contains("stp=dst-jpg_s") || lower.matches(".*[/_]([sp])\\d{2,3}x\\d{2,3}.*")) {
            return TIER_NONE;
        }

        String path = pathOf(lower);
        if (path.endsWith(".jpg")
            || path.endsWith(".jpeg")
            || path.endsWith(".png")
            || path.endsWith(".webp")
            || path.endsWith(".heic")
            || path.endsWith(".avif")) {
            return TIER_PROGRESSIVE;
        }

        return isFacebookHost(lower) ? TIER_PLAUSIBLE : TIER_NONE;
    }

    /**
     * The short side of this address in pixels, or {@code 0} when it says nothing.
     *
     * <p>Three markers, in a fixed order, so that the answer never depends on which one is looked
     * for first. {@code 720p} already means the short side, so a {@code WxH} pair reduces to the
     * same unit and the two never need separate scales.
     *
     * <p>Facebook does not always put the marker in plain sight. It packs the name of the variant
     * into the {@code efg} parameter as base64, and the marker is inside. Reading that parameter is
     * the difference between a real ranking and every address tying at zero.
     */
    static int qualityOf(String url) {
        if (url == null) return 0;

        int direct = qualityOfText(url);
        if (direct > 0) return direct;

        String efg = decodeEfg(url);
        return efg == null ? 0 : qualityOfText(efg);
    }

    /**
     * Which of two addresses to prefer. Lower sorts better.
     *
     * <p>The last key is the address itself, and it has to be. The walk reads fields in whatever
     * order the runtime reports them, which the JVM and ART both leave unspecified, so a tie
     * broken by the order they were found is a different answer on a different device. Comparing
     * the text makes the answer the same everywhere.
     */
    static int compare(String a, String b, boolean video) {
        int tierA = video ? videoTier(a) : imageTier(a);
        int tierB = video ? videoTier(b) : imageTier(b);
        if (tierA != tierB) return tierB - tierA;

        int qualityA = qualityOf(a);
        int qualityB = qualityOf(b);
        if (qualityA != qualityB) return qualityB - qualityA;

        return a.compareTo(b);
    }

    /** The best of these addresses, or {@code null} when none of them qualifies. */
    static String bestOf(Collection<String> urls, boolean video) {
        if (urls == null || urls.isEmpty()) return null;

        // The same address can arrive through two fields. Fold it first, so that a duplicate
        // cannot change the answer.
        Set<String> unique = new LinkedHashSet<>(urls);

        String best = null;
        for (String url : unique) {
            if ((video ? videoTier(url) : imageTier(url)) == TIER_NONE) continue;
            if (best == null || compare(url, best, video) < 0) best = url;
        }

        return best;
    }

    // ---------------------------------------------------------------- reading an object

    /**
     * The value of one named field of [host] as text, or {@code null}.
     *
     * <p>For the caller that already knows the real name of the field. Walks the superclasses,
     * because the field is not always declared where the object says it is.
     */
    static String fieldValue(Object host, String fieldName) {
        if (host == null || fieldName == null) return null;

        for (Class<?> type = host.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(host);
                return value == null ? null : String.valueOf(value);
            } catch (NoSuchFieldException ignored) {
                // Declared further up, or renamed. Keep walking.
            } catch (Throwable t) {
                return null;
            }
        }

        return null;
    }

    /**
     * Every address that [host] can reach within [maxDepth] steps.
     *
     * <p>This is a walk with a fence around it, and the fence is the point. An object of Facebook
     * holds a view, a view holds a context and a context holds the whole application, so a walk
     * that follows every field reaches the entire app and does it on the thread that draws. The
     * budget, the depth and the check on the name of the class are what keep it to the media.
     */
    static List<String> harvest(Object host, int maxDepth) {
        List<String> found = new ArrayList<>();
        if (host == null) return found;

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
                    // A static field is a constant or a cache, never the media of this item.
                    if (Modifier.isStatic(field.getModifiers())) continue;

                    Object value;
                    try {
                        field.setAccessible(true);
                        value = field.get(node);
                    } catch (Throwable t) {
                        // One unreadable field must not end the walk.
                        continue;
                    }

                    if (value == null) continue;
                    collect(value, depth, maxDepth, found, seen, queue);
                }
            }
        }

        return found;
    }

    // ---------------------------------------------------------------- internals

    private static void collect(
        Object value,
        int depth,
        int maxDepth,
        List<String> found,
        IdentityHashMap<Object, Boolean> seen,
        Deque<Object[]> queue
    ) {
        if (isText(value)) {
            String text = String.valueOf(value);
            if (isHttpUrl(text)) found.add(text);
            return;
        }

        // One level into a list or an array, because a field can hold several variants of the
        // same video rather than one.
        if (value instanceof Iterable) {
            for (Object element : (Iterable<?>) value) {
                if (element != null && isText(element)) {
                    String text = String.valueOf(element);
                    if (isHttpUrl(text)) found.add(text);
                }
            }
            return;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                if (element != null && isText(element)) {
                    String text = String.valueOf(element);
                    if (isHttpUrl(text)) found.add(text);
                }
            }
            return;
        }

        if (depth >= maxDepth) return;
        if (!isAppObject(value)) return;
        if (seen.put(value, Boolean.TRUE) != null) return;

        queue.add(new Object[] { value, depth + 1 });
    }

    /** Text, or something whose text is the address. An Android {@code Uri} is the second kind. */
    private static boolean isText(Object value) {
        return value instanceof CharSequence
            || value.getClass().getName().startsWith("android.net.Uri");
    }

    /**
     * Whether this is an object of the app rather than of the platform.
     *
     * <p>Facebook puts its own classes in its own packages and its renamed ones in {@code X}.
     * Everything else is a view, a framework object or a container, and the walk stops there.
     */
    private static boolean isAppObject(Object value) {
        String name = value.getClass().getName();
        return name.startsWith("com.facebook.") || name.startsWith("X.");
    }

    private static boolean isFacebookHost(String lowerUrl) {
        String host = hostOf(lowerUrl);
        return host.endsWith(".fbcdn.net") || host.startsWith("scontent");
    }

    private static String hostOf(String lowerUrl) {
        int start = lowerUrl.indexOf("://");
        if (start < 0) return "";
        start += 3;

        int end = lowerUrl.length();
        for (int i = start; i < lowerUrl.length(); i++) {
            char c = lowerUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }

        return lowerUrl.substring(start, end);
    }

    private static String pathOf(String lowerUrl) {
        int cut = lowerUrl.indexOf('?');
        String withoutQuery = cut < 0 ? lowerUrl : lowerUrl.substring(0, cut);

        cut = withoutQuery.indexOf('#');
        return cut < 0 ? withoutQuery : withoutQuery.substring(0, cut);
    }

    /** The first of the three markers that this text carries, in a fixed order. */
    private static int qualityOfText(String text) {
        int marked = matchNumberBefore(text, 'p');
        if (marked > 0) return marked;

        int pair = matchDimensionPair(text);
        if (pair > 0) return pair;

        String lower = text.toLowerCase(Locale.US);
        if (containsWord(lower, "hd")) return 720;
        if (containsWord(lower, "sd")) return 480;

        return 0;
    }

    /**
     * The number in a marker such as {@code _720p}, or {@code 0}.
     *
     * <p>Written by hand rather than with a regular expression, because this runs on every address
     * of every save and a pattern here is not worth the cost of compiling one.
     */
    private static int matchNumberBefore(String text, char suffix) {
        int best = 0;

        for (int i = 0; i < text.length(); i++) {
            if (Character.toLowerCase(text.charAt(i)) != suffix) continue;

            // A digit after the marker means this is part of a longer number, not a marker.
            if (i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) continue;

            int end = i;
            int start = i;
            while (start > 0 && Character.isDigit(text.charAt(start - 1))) start--;

            // Three or four digits is a resolution. Fewer is a version, more is an id.
            int digits = end - start;
            if (digits < 3 || digits > 4) continue;

            try {
                best = Math.max(best, Integer.parseInt(text.substring(start, end)));
            } catch (NumberFormatException ignored) {
                // Cannot happen: every character was checked as a digit.
            }
        }

        return best;
    }

    /** The short side of a {@code 1280x720} pair, or {@code 0}. */
    private static int matchDimensionPair(String text) {
        int best = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c != 'x') continue;

            int leftEnd = i;
            int leftStart = i;
            while (leftStart > 0 && Character.isDigit(text.charAt(leftStart - 1))) leftStart--;

            int rightStart = i + 1;
            int rightEnd = rightStart;
            while (rightEnd < text.length() && Character.isDigit(text.charAt(rightEnd))) rightEnd++;

            int leftDigits = leftEnd - leftStart;
            int rightDigits = rightEnd - rightStart;
            if (leftDigits < 2 || leftDigits > 5 || rightDigits < 2 || rightDigits > 5) continue;

            try {
                int width = Integer.parseInt(text.substring(leftStart, leftEnd));
                int height = Integer.parseInt(text.substring(rightStart, rightEnd));
                best = Math.max(best, Math.min(width, height));
            } catch (NumberFormatException ignored) {
                // Cannot happen: every character was checked as a digit.
            }
        }

        return best;
    }

    private static boolean containsWord(String lowerText, String word) {
        int from = 0;

        while (true) {
            int at = lowerText.indexOf(word, from);
            if (at < 0) return false;

            boolean startsCleanly = at == 0 || !Character.isLetterOrDigit(lowerText.charAt(at - 1));
            int after = at + word.length();
            boolean endsCleanly = after >= lowerText.length()
                || !Character.isLetterOrDigit(lowerText.charAt(after));

            if (startsCleanly && endsCleanly) return true;
            from = at + 1;
        }
    }

    /**
     * The {@code efg} parameter of this address, decoded, or {@code null}.
     *
     * <p>Facebook puts the name of the variant in here, so this is where the marker is when the
     * address itself shows none.
     */
    private static String decodeEfg(String url) {
        int at = url.indexOf("efg=");
        if (at < 0) return null;

        int start = at + 4;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '&' || c == '#') {
                end = i;
                break;
            }
        }

        String raw = url.substring(start, end);
        if (raw.isEmpty()) return null;

        try {
            // The parameter is base64 for a URL, and Facebook drops the padding.
            StringBuilder padded = new StringBuilder(raw);
            while (padded.length() % 4 != 0) padded.append('=');

            byte[] decoded = java.util.Base64.getUrlDecoder().decode(padded.toString());
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // Not base64, or not for a URL. The address simply says nothing about its size.
            return null;
        }
    }
}

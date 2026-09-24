package app.andrewliang.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tracks of a DASH manifest, and the best video and audio track among them.
 *
 * <p>Holds no Android type, like {@link RenditionPicker}, so it can be checked with {@code javac}
 * alone against a manifest taken from a device.
 *
 * <p>Facebook writes each track as one {@code Representation} with one {@code BaseURL}: the address
 * of a whole MP4 file that holds that track and nothing else. So a track downloads with one plain
 * fetch, and the video and the audio are two separate files. A track that has no {@code BaseURL},
 * for example one split into segments, is left out, because one fetch cannot get it.
 *
 * <p>This is a small reader for that one shape, not a general XML parser. It reads attributes of
 * elements that hold no nested elements of the same name, which is all a manifest of this kind has.
 */
final class DashManifest {

    private DashManifest() {}

    /** One track of the manifest. */
    static final class Track {
        final String mime;
        final String codecs;
        final int width;
        final int height;
        final long bandwidth;
        final String url;

        Track(String mime, String codecs, int width, int height, long bandwidth, String url) {
            this.mime = mime;
            this.codecs = codecs;
            this.width = width;
            this.height = height;
            this.bandwidth = bandwidth;
            this.url = url;
        }

        boolean isVideo() {
            return mime.startsWith("video/");
        }

        boolean isAudio() {
            return mime.startsWith("audio/");
        }

        /** The quality in the same unit as {@code 720p}: the short side, in pixels. */
        int shortSide() {
            return Math.min(width, height);
        }

        @Override
        public String toString() {
            return mime + " " + codecs + " " + width + "x" + height + " " + (bandwidth / 1000) + "kbps";
        }
    }

    private static final Pattern ADAPTATION_SET =
        Pattern.compile("<AdaptationSet\\b([^>]*)>(.*?)</AdaptationSet>", Pattern.DOTALL);

    private static final Pattern REPRESENTATION =
        Pattern.compile("<Representation\\b([^>]*?)(?:/>|>(.*?)</Representation>)", Pattern.DOTALL);

    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:]+)\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern BASE_URL = Pattern.compile("<BaseURL[^>]*>(.*?)</BaseURL>", Pattern.DOTALL);

    /** Every track with a single file address. Never throws, and never returns {@code null}. */
    static List<Track> parse(String manifest) {
        List<Track> tracks = new ArrayList<>();
        if (manifest == null) return tracks;

        try {
            Matcher sets = ADAPTATION_SET.matcher(manifest);
            while (sets.find()) {
                String setAttributes = sets.group(1);
                Matcher representations = REPRESENTATION.matcher(sets.group(2));

                while (representations.find()) {
                    Track track = track(setAttributes, representations.group(1), representations.group(2));
                    if (track != null) tracks.add(track);
                }
            }
        } catch (Throwable ignored) {
            // A manifest of an unexpected shape answers with what was read so far.
        }

        return tracks;
    }

    /**
     * The best video track that {@code MediaMuxer} can write into an MP4, or {@code null}.
     *
     * <p>H.264 and H.265 always, and AV1 only when [allowAv1]. A story often lists AV1 tracks and
     * nothing else, but the muxer writes AV1 only from Android 14, and a device with no AV1
     * decoder cannot play the file. VP9 is never chosen. At the same size H.264 wins, because
     * every player can play it.
     */
    static Track bestVideo(List<Track> tracks, boolean allowAv1) {
        Track best = null;

        for (Track track : tracks) {
            if (!track.isVideo()) continue;

            int family = videoFamily(track.codecs, allowAv1);
            if (family == 0) continue;

            if (best == null) {
                best = track;
                continue;
            }

            if (track.shortSide() != best.shortSide()) {
                if (track.shortSide() > best.shortSide()) best = track;
                continue;
            }

            int bestFamily = videoFamily(best.codecs, allowAv1);
            if (family != bestFamily) {
                if (family > bestFamily) best = track;
                continue;
            }

            if (track.bandwidth > best.bandwidth) best = track;
        }

        return best;
    }

    /**
     * The AAC track with the highest bitrate, or {@code null}. Every AAC profile counts, including
     * xHE-AAC ({@code mp4a.40.42}), which is what a story lists.
     */
    static Track bestAudio(List<Track> tracks) {
        Track best = null;

        for (Track track : tracks) {
            if (!track.isAudio()) continue;
            if (!track.codecs.startsWith("mp4a")) continue;
            if (best == null || track.bandwidth > best.bandwidth) best = track;
        }

        return best;
    }

    // ---------------------------------------------------------------- internals

    /** Higher is preferred: 3 for H.264, 2 for H.265, 1 for AV1, 0 for a codec that is left out. */
    private static int videoFamily(String codecs, boolean allowAv1) {
        if (codecs.startsWith("avc1") || codecs.startsWith("avc3")) return 3;
        if (codecs.startsWith("hvc1") || codecs.startsWith("hev1")) return 2;
        if (allowAv1 && codecs.startsWith("av01")) return 1;
        return 0;
    }

    private static Track track(String setAttributes, String attributes, String body) {
        if (body == null) return null;

        Matcher base = BASE_URL.matcher(body);
        if (!base.find()) return null;

        String url = unescape(base.group(1).trim());
        if (!url.toLowerCase(Locale.US).startsWith("https://")) return null;

        // A track states what it holds either on itself or on its set.
        String mime = attribute(attributes, "mimeType");
        if (mime == null) mime = attribute(setAttributes, "mimeType");
        if (mime == null) {
            String content = attribute(setAttributes, "contentType");
            if (content != null) mime = content + "/mp4";
        }
        if (mime == null) return null;

        String codecs = attribute(attributes, "codecs");
        if (codecs == null) codecs = attribute(setAttributes, "codecs");

        return new Track(
            mime.toLowerCase(Locale.US),
            codecs == null ? "" : codecs.toLowerCase(Locale.US),
            number(attribute(attributes, "width")),
            number(attribute(attributes, "height")),
            number(attribute(attributes, "bandwidth")),
            url
        );
    }

    private static String attribute(String attributes, String name) {
        if (attributes == null) return null;

        Matcher matcher = ATTRIBUTE.matcher(attributes);
        while (matcher.find()) {
            if (matcher.group(1).equals(name)) return unescape(matcher.group(2));
        }
        return null;
    }

    private static int number(String text) {
        if (text == null) return 0;
        try {
            long value = Long.parseLong(text.trim());
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The five entities of XML. {@code &amp;} is last, so {@code &amp;lt;} stays {@code &lt;}. */
    private static String unescape(String text) {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&");
    }
}

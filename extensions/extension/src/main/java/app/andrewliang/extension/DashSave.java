package app.andrewliang.extension;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Saves one DASH video track and one audio track as one MP4 file.
 *
 * <p>A DASH manifest keeps the picture and the sound in two files. One plain fetch gets each file.
 * Then {@code MediaMuxer} copies the samples of both into one file. It does not decode or encode
 * them again. So the file has the quality that the player streams, and the join takes less than a
 * second.
 *
 * <p>The two tracks and the result go into the cache of the app first, because the muxer must seek
 * in its files. Only the finished file goes into the gallery, through the same
 * {@link Downloader.Sink} as all other saves. So an error at any step leaves nothing in the gallery.
 */
final class DashSave {

    private DashSave() {}

    private static final String TAG = MediaDownload.TAG;

    private static final String CACHE_FOLDER = "andrew-save";

    /** A file older than this is from a save that the system stopped. */
    private static final long STALE_MS = 60L * 60L * 1000L;

    private static final int DEFAULT_SAMPLE_BUFFER = 2 * 1024 * 1024;

    private static volatile Boolean canWriteAv1;

    /**
     * Whether this device can save an AV1 track. The muxer writes AV1 into an MP4 from Android 14.
     * The device must also have an AV1 decoder, or it cannot play the file.
     */
    static boolean canWriteAv1() {
        if (canWriteAv1 == null) canWriteAv1 = hasAv1Decoder();
        return canWriteAv1;
    }

    private static boolean hasAv1Decoder() {
        if (Build.VERSION.SDK_INT < 34) return false;

        try {
            for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (codec.isEncoder()) continue;
                for (String type : codec.getSupportedTypes()) {
                    if ("video/av01".equalsIgnoreCase(type)) return true;
                }
            }
        } catch (Throwable ignored) {
            // No codec list, so no decoder.
        }

        return false;
    }

    /**
     * Download [video] and [audio], join them, and write the result to [sink]. This blocks and
     * never throws. [audio] is {@code null} for a video with no sound.
     */
    static Downloader.Status save(
        Context application,
        DashManifest.Track video,
        DashManifest.Track audio,
        Downloader.Sink sink
    ) {
        File folder = new File(application.getCacheDir(), CACHE_FOLDER);
        File videoFile = null;
        File audioFile = null;
        File joined = null;

        try {
            if (!folder.isDirectory() && !folder.mkdirs()) return Downloader.Status.WRITE_ERROR;
            removeStale(folder);

            videoFile = File.createTempFile("video", ".mp4", folder);
            Downloader.Status status = Downloader.fetch(video.url, new FileSink(videoFile));
            if (status != Downloader.Status.OK) return status;

            if (audio != null) {
                audioFile = File.createTempFile("audio", ".mp4", folder);
                status = Downloader.fetch(audio.url, new FileSink(audioFile));
                if (status != Downloader.Status.OK) return status;
            }

            joined = File.createTempFile("joined", ".mp4", folder);
            join(videoFile, audioFile, joined);

            return publish(joined, sink);
        } catch (Throwable t) {
            Log.w(TAG, "the DASH save failed", t);
            return Downloader.Status.WRITE_ERROR;
        } finally {
            delete(videoFile);
            delete(audioFile);
            delete(joined);
        }
    }

    // ---------------------------------------------------------------- internals

    /**
     * Copy the samples of both files into [out], in order of time.
     *
     * <p>Each step writes the sample that comes first in time, from either track. A file with all
     * of the video before all of the sound also plays. But a player must then seek across the whole
     * file to start, and some players refuse that.
     */
    private static void join(File video, File audio, File out) throws IOException {
        MediaExtractor videoIn = new MediaExtractor();
        MediaExtractor audioIn = audio == null ? null : new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;

        try {
            videoIn.setDataSource(video.getPath());
            if (audioIn != null) audioIn.setDataSource(audio.getPath());

            muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int bufferSize = DEFAULT_SAMPLE_BUFFER;

            MediaFormat videoFormat = selectTrack(videoIn, "video/");
            if (videoFormat == null) throw new IOException("the video file holds no video track");
            int videoTrack = muxer.addTrack(videoFormat);
            bufferSize = Math.max(bufferSize, maxInputSize(videoFormat));

            int audioTrack = -1;
            if (audioIn != null) {
                MediaFormat audioFormat = selectTrack(audioIn, "audio/");
                if (audioFormat == null) throw new IOException("the audio file holds no audio track");
                audioTrack = muxer.addTrack(audioFormat);
                bufferSize = Math.max(bufferSize, maxInputSize(audioFormat));
            }

            muxer.start();
            started = true;

            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            boolean videoDone = false;
            boolean audioDone = audioIn == null;

            while (!videoDone || !audioDone) {
                long videoTime = videoDone ? Long.MAX_VALUE : videoIn.getSampleTime();
                long audioTime = audioDone ? Long.MAX_VALUE : audioIn.getSampleTime();

                if (!videoDone && videoTime < 0) {
                    videoDone = true;
                    continue;
                }
                if (!audioDone && audioTime < 0) {
                    audioDone = true;
                    continue;
                }

                boolean takeVideo = videoTime <= audioTime;
                MediaExtractor from = takeVideo ? videoIn : audioIn;
                int track = takeVideo ? videoTrack : audioTrack;

                buffer.clear();
                int size = from.readSampleData(buffer, 0);
                if (size < 0) {
                    if (takeVideo) videoDone = true; else audioDone = true;
                    continue;
                }

                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = from.getSampleTime();
                info.flags = (from.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? MediaCodec.BUFFER_FLAG_KEY_FRAME
                    : 0;

                muxer.writeSampleData(track, buffer, info);
                from.advance();
            }
        } finally {
            if (muxer != null) {
                try {
                    if (started) muxer.stop();
                } finally {
                    muxer.release();
                }
            }
            videoIn.release();
            if (audioIn != null) audioIn.release();
        }
    }

    /** Select the first track of [kind] and return its format, or {@code null}. */
    private static MediaFormat selectTrack(MediaExtractor extractor, String kind) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(kind)) {
                extractor.selectTrack(i);
                return format;
            }
        }
        return null;
    }

    private static int maxInputSize(MediaFormat format) {
        try {
            return format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                ? format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Copy the finished file into [sink]. If an error occurs, remove the entry again. */
    private static Downloader.Status publish(File file, Downloader.Sink sink) {
        boolean opened = false;

        try (InputStream in = new FileInputStream(file)) {
            OutputStream out = sink.open("video/mp4");
            opened = true;

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            out.flush();

            sink.commit();
            return Downloader.Status.OK;
        } catch (Throwable t) {
            Log.w(TAG, "could not publish the joined file", t);
            if (opened) {
                try {
                    sink.abandon();
                } catch (Throwable ignored) {
                    // Cleaning up must never replace the real failure.
                }
            }
            return Downloader.Status.WRITE_ERROR;
        }
    }

    private static void removeStale(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;

        long now = System.currentTimeMillis();
        for (File file : files) {
            if (now - file.lastModified() > STALE_MS) delete(file);
        }
    }

    private static void delete(File file) {
        if (file == null) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable ignored) {
            // The next save removes a file that stays, or the system clears the cache.
        }
    }

    /** A {@link Downloader.Sink} that writes one file in the cache. */
    private static final class FileSink implements Downloader.Sink {
        private final File file;
        private OutputStream stream;

        FileSink(File file) {
            this.file = file;
        }

        @Override
        public OutputStream open(String mimeFromServer) throws IOException {
            stream = new FileOutputStream(file);
            return stream;
        }

        @Override
        public void commit() throws IOException {
            if (stream != null) stream.close();
            stream = null;
        }

        @Override
        public void abandon() {
            try {
                if (stream != null) stream.close();
            } catch (Throwable ignored) {
                // The file is removed next.
            }
            stream = null;
            delete(file);
        }
    }
}

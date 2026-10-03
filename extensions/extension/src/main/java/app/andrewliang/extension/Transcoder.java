package app.andrewliang.extension;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Encodes one track again, into a codec that every player and every app accepts.
 *
 * <p>Each method reads one track from one MP4 file and writes one MP4 file that holds only the new
 * track. {@link DashSave} then joins the video and the sound as it does for a track it copies.
 *
 * <p>The video goes from the decoder straight into the input surface of the H.264 encoder. The
 * size does not change, so no drawing step is necessary between the two.
 */
final class Transcoder {

    private Transcoder() {}

    private static final long TIMEOUT_US = 10_000L;

    /** If no step makes progress for this long, the codec has stopped and the save fails. */
    private static final long STALL_MS = 15_000L;

    private static final int DEFAULT_FRAME_RATE = 30;

    /** Bits for each pixel of each frame. This gives about 6 Mbit/s at 1080p and 30 fps. */
    private static final double BITS_PER_PIXEL = 0.1;

    private static final int MIN_VIDEO_BIT_RATE = 2_000_000;
    private static final int MAX_VIDEO_BIT_RATE = 16_000_000;

    private static final int AUDIO_BIT_RATE_PER_CHANNEL = 96_000;

    /**
     * Encode the video track of [in] as H.264 into [out].
     *
     * <p>[sourceBitRate] is the bit rate of the source track, or 0. H.264 needs about twice the bit
     * rate of AV1 or VP9 for the same picture, so the target is never less than twice the source.
     */
    static void toAvc(File in, File out, long sourceBitRate) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        Surface surface = null;
        Output output = null;

        try {
            extractor.setDataSource(in.getPath());
            MediaFormat source = DashSave.selectTrack(extractor, "video/");
            if (source == null) throw new IOException("the file holds no video track");

            int width = source.getInteger(MediaFormat.KEY_WIDTH);
            int height = source.getInteger(MediaFormat.KEY_HEIGHT);
            int frameRate = intOf(source, MediaFormat.KEY_FRAME_RATE, DEFAULT_FRAME_RATE);

            long bitRate = (long) (width * (long) height * frameRate * BITS_PER_PIXEL);
            bitRate = Math.max(bitRate, 2L * sourceBitRate);
            bitRate = Math.max(MIN_VIDEO_BIT_RATE, Math.min(MAX_VIDEO_BIT_RATE, bitRate));

            MediaFormat target = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            target.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            target.setInteger(MediaFormat.KEY_BIT_RATE, (int) bitRate);
            target.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            target.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(target, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();

            decoder = MediaCodec.createDecoderByType(source.getString(MediaFormat.KEY_MIME));
            decoder.configure(source, surface, null, 0);
            decoder.start();

            output = new Output(out, intOf(source, MediaFormat.KEY_ROTATION, 0));

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean decoderDone = false;
            long lastProgress = System.currentTimeMillis();

            while (!output.done) {
                boolean progress = false;

                if (!inputDone) {
                    int index = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (index >= 0) {
                        inputDone = feed(extractor, decoder, index);
                        progress = true;
                    }
                }

                if (!decoderDone) {
                    int index = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (index >= 0) {
                        boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        // The frame goes to the encoder with the time of this buffer.
                        decoder.releaseOutputBuffer(index, info.size > 0);
                        if (end) {
                            encoder.signalEndOfInputStream();
                            decoderDone = true;
                        }
                        progress = true;
                    }
                }

                if (output.drain(encoder, info, decoderDone ? TIMEOUT_US : 0)) progress = true;

                lastProgress = checkStall(progress, lastProgress);
            }
        } finally {
            release(decoder);
            release(encoder);
            if (surface != null) surface.release();
            if (output != null) output.close();
            extractor.release();
        }
    }

    /** Encode the audio track of [in] as AAC-LC into [out]. */
    static void toAacLc(File in, File out) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        Output output = null;

        try {
            extractor.setDataSource(in.getPath());
            MediaFormat source = DashSave.selectTrack(extractor, "audio/");
            if (source == null) throw new IOException("the file holds no audio track");

            decoder = MediaCodec.createDecoderByType(source.getString(MediaFormat.KEY_MIME));
            decoder.configure(source, null, null, 0);
            decoder.start();

            output = new Output(out, 0);

            MediaCodec.BufferInfo decoded = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encoded = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean decoderDone = false;
            int sampleRate = 0;
            int channels = 0;
            long framesQueued = 0;
            long startUs = -1;
            boolean endQueued = false;
            long lastProgress = System.currentTimeMillis();

            while (!output.done) {
                boolean progress = false;

                if (!inputDone) {
                    int index = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (index >= 0) {
                        inputDone = feed(extractor, decoder, index);
                        progress = true;
                    }
                }

                if (!decoderDone) {
                    int index = decoder.dequeueOutputBuffer(decoded, TIMEOUT_US);

                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && encoder == null) {
                        // The decoder knows the real rate and channels only after the first frame.
                        MediaFormat pcm = decoder.getOutputFormat();
                        int encoding = intOf(pcm, MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                        if (encoding != AudioFormat.ENCODING_PCM_16BIT) {
                            throw new IOException("the decoder gives PCM encoding " + encoding);
                        }

                        sampleRate = pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels = pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        encoder = aacEncoder(sampleRate, channels);
                        progress = true;
                    } else if (index >= 0) {
                        if (encoder == null) throw new IOException("the decoder gave samples before a format");

                        if (startUs < 0) startUs = Math.max(0L, decoded.presentationTimeUs);

                        ByteBuffer pcm = decoder.getOutputBuffer(index);
                        pcm.position(decoded.offset);
                        pcm.limit(decoded.offset + decoded.size);

                        // An input buffer of the encoder can be smaller than one decoded frame.
                        while (pcm.hasRemaining()) {
                            int slot = encoder.dequeueInputBuffer(TIMEOUT_US);
                            if (slot < 0) {
                                output.drain(encoder, encoded, 0);
                                lastProgress = checkStall(false, lastProgress);
                                continue;
                            }

                            ByteBuffer target = encoder.getInputBuffer(slot);
                            target.clear();
                            int bytes = Math.min(pcm.remaining(), target.remaining());
                            bytes -= bytes % (2 * channels);
                            if (bytes == 0) throw new IOException("the encoder input buffer is too small");

                            ByteBuffer slice = pcm.duplicate();
                            slice.limit(pcm.position() + bytes);
                            target.put(slice);
                            pcm.position(pcm.position() + bytes);

                            long timeUs = startUs + framesQueued * 1_000_000L / sampleRate;
                            framesQueued += bytes / (2 * channels);
                            encoder.queueInputBuffer(slot, 0, bytes, timeUs, 0);
                            lastProgress = System.currentTimeMillis();
                        }

                        decoder.releaseOutputBuffer(index, false);

                        if ((decoded.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) decoderDone = true;
                        progress = true;
                    }
                }

                if (decoderDone && encoder != null && !endQueued) {
                    int index = encoder.dequeueInputBuffer(TIMEOUT_US);
                    if (index >= 0) {
                        long timeUs = startUs + framesQueued * 1_000_000L / sampleRate;
                        encoder.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        endQueued = true;
                        progress = true;
                    }
                }

                if (decoderDone && encoder == null) throw new IOException("the decoder gave no audio");

                if (encoder != null && output.drain(encoder, encoded, decoderDone ? TIMEOUT_US : 0)) {
                    progress = true;
                }

                lastProgress = checkStall(progress, lastProgress);
            }
        } finally {
            release(decoder);
            release(encoder);
            if (output != null) output.close();
            extractor.release();
        }
    }

    // ---------------------------------------------------------------- internals

    private static MediaCodec aacEncoder(int sampleRate, int channels) throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE_PER_CHANNEL * channels);

        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        return encoder;
    }

    /** Give the next sample of [extractor] to [decoder]. Returns whether that was the end. */
    private static boolean feed(MediaExtractor extractor, MediaCodec decoder, int index) {
        ByteBuffer buffer = decoder.getInputBuffer(index);
        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);

        if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return true;
        }

        decoder.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
        extractor.advance();
        return false;
    }

    private static long checkStall(boolean progress, long lastProgress) throws IOException {
        long now = System.currentTimeMillis();
        if (progress) return now;
        if (now - lastProgress > STALL_MS) throw new IOException("the codec stopped");
        return lastProgress;
    }

    private static int intOf(MediaFormat format, String key, int fallback) {
        if (!format.containsKey(key)) return fallback;
        try {
            return format.getInteger(key);
        } catch (ClassCastException e) {
            try {
                return Math.round(format.getFloat(key));
            } catch (Throwable t) {
                return fallback;
            }
        }
    }

    private static void release(MediaCodec codec) {
        if (codec == null) return;
        try {
            codec.stop();
        } catch (Throwable ignored) {
            // A codec that failed cannot stop. It can still release.
        }
        codec.release();
    }

    /** The muxer of one encoded track. It starts when the encoder gives its format. */
    private static final class Output {
        private final MediaMuxer muxer;
        private int track = -1;
        private boolean started;

        boolean done;

        Output(File file, int rotation) throws IOException {
            muxer = new MediaMuxer(file.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (rotation != 0) muxer.setOrientationHint(rotation);
        }

        /** Write what [encoder] has ready. Returns whether anything came out. */
        boolean drain(MediaCodec encoder, MediaCodec.BufferInfo info, long timeoutUs) throws IOException {
            boolean any = false;

            while (!done) {
                int index = encoder.dequeueOutputBuffer(info, timeoutUs);

                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return any;

                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (started) throw new IOException("the encoder changed its format twice");
                    track = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    started = true;
                    any = true;
                    continue;
                }

                if (index < 0) continue;

                ByteBuffer data = encoder.getOutputBuffer(index);
                // The format already carries the codec configuration.
                boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;

                if (!config && info.size > 0 && data != null) {
                    if (!started) throw new IOException("the encoder gave data before a format");
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    muxer.writeSampleData(track, data, info);
                }

                encoder.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true;
                any = true;
                timeoutUs = 0;
            }

            return any;
        }

        void close() {
            try {
                if (started) muxer.stop();
            } catch (Throwable ignored) {
                // A muxer that failed cannot stop. It can still release.
            } finally {
                muxer.release();
            }
        }
    }
}

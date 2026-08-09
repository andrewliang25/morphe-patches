package app.andrewliang.extension;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helper for the "Send original photos without the quality cliff" patch.
 *
 * <p>With the "Original" toggle on, stock LINE clears the per-item {@code isOriginal} flag when a
 * photo is {@code >= 20 MB} or {@code >= 100 MP} ({@code t73.k0.b0}). The photo then falls all the
 * way onto the ordinary standard path and is resampled to the tier's pixel budget — 4.19 MP on the
 * High setting, 1.64 MP on the default Normal one. Just under the threshold a photo is copied byte
 * for byte; just over it loses 6-25x its pixels. There is no tier in between, which is the whole
 * defect.
 *
 * <p>The patch stops those two bail-outs, so every such photo now stays on the *original* path and
 * reaches this class. Here it is re-encoded at native resolution — or bounded to
 * {@link #MAX_PIXELS} when it really is enormous — instead of being resampled to a few megapixels.
 *
 * <p>{@link #writeBounded} deliberately re-derives the same {@code >= 20 MB || >= 100 MP} test the
 * patch removed, and returns {@code null} for anything else. That keeps it a strict no-op for the
 * cases that already work: notably a large-but-not-huge JPEG (say 50 MP at 10 MB) is copied byte
 * for byte today and must keep being copied byte for byte.
 */
public final class OriginalPhoto {

    private OriginalPhoto() {}

    private static final String TAG = "AndrewOriginalPhoto";

    /** {@code 20971520} — the source-size gate the patch neutralises in {@code t73.k0.b0}. */
    private static final long SIZE_THRESHOLD = 20L * 1024 * 1024;

    /** {@code 100000000} — the source-pixel gate the patch neutralises in {@code t73.k0.b0}. */
    private static final long PIXEL_THRESHOLD = 100_000_000L;

    /**
     * Output ceiling. A 24 MP decode is ~96 MB as {@code ARGB_8888}, and baking in rotation
     * briefly doubles that; LINE sets {@code android:largeHeap="true"}, which accommodates it on
     * a roomy device. Photos at or below this keep their native resolution.
     */
    private static final long MAX_PIXELS = 24_000_000L;

    /**
     * How many times to retry with the ceiling halved when a decode fails. 24 MP needs ~192 MB
     * across the decode and the rotation copy, which a mid-range device can refuse even with
     * {@code largeHeap}; 12 MP and then 6 MP need ~96 MB and ~48 MB. Giving up entirely would cost
     * far more than the halving does — see {@link #writeBounded}.
     */
    private static final int ATTEMPTS = 3;

    /** Matches the quality the patch pins LINE's own original-path re-encode to. */
    private static final int QUALITY = 80;

    /**
     * Arbitrary base for the {@code inDensity} / {@code inTargetDensity} ratio. Large enough that
     * rounding the target density costs well under a pixel of accuracy.
     */
    private static final int DENSITY_BASE = 10_000;

    /**
     * Re-encodes an oversized photo into {@code destination} at native resolution, or bounded to
     * {@link #MAX_PIXELS} if it exceeds that.
     *
     * <p>Failure returns {@code null}, not {@code FALSE}. {@code FALSE} propagates as "the original
     * could not be written", and {@code c1.q} then deletes the temp file, so {@code d98.m1} finds
     * no {@code IMAGE_ORIGINAL} file and silently ships the {@code c1.p} standard variant instead —
     * the 1.64 MP result this patch exists to prevent. Returning {@code null} instead hands the
     * photo back to LINE's stock raw copy, which keeps full resolution; the only cost is a large
     * file on the wire, against an OBS ceiling that has never actually been observed. LINE's copy
     * opens {@code FileOutputStream(File)} with no append, so it truncates whatever a failed
     * attempt left behind.
     *
     * @param rotation the rotation in degrees LINE's caller supplied for this send, or
     *                 {@code null} to fall back to the source's EXIF orientation — the same
     *                 precedence LINE's own encoders on this path use.
     * @return {@code TRUE} when {@code destination} was written; {@code null} when the caller must
     *         fall through to LINE's stock handling, either because this photo is not one of the
     *         oversized cases or because re-encoding it did not succeed. Never {@code FALSE}.
     */
    public static Boolean writeBounded(
            Context context, Uri source, Integer rotation, File destination) {
        if (context == null || source == null || destination == null) {
            // Logged unconditionally: see the entry log below for why.
            Log.i(TAG, "Reached with a null argument; deferring to LINE.");
            return null;
        }

        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decode(context, source, bounds);

            long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
            long length = sourceLength(context, source);

            // Unconditional, before any decision. Every other outcome here is a fall-through that
            // looks identical from outside the app, so without this line "nothing was logged" is
            // ambiguous between "the hook never ran" (wrong pipeline, or a stale install) and "the
            // hook ran and measured the photo as small". Those need opposite fixes.
            Log.i(TAG, "Reached: " + bounds.outWidth + "x" + bounds.outHeight + " (" + pixels
                    + " px), " + length + " bytes, rotation=" + rotation + ", uri=" + source);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.i(TAG, "Bounds unreadable; deferring to LINE.");
                return null;
            }

            if (pixels < PIXEL_THRESHOLD && length < SIZE_THRESHOLD) {
                Log.i(TAG, "Under both thresholds; deferring to LINE's raw copy.");
                return null;
            }

            // Halve the ceiling and retry rather than give up: the likeliest failure is the
            // allocation, and 12 MP still beats the 1.64/4.19 MP a fall-through-to-standard costs.
            long ceiling = MAX_PIXELS;
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                try {
                    if (reencode(context, source, rotation, destination, pixels, ceiling)) {
                        Log.i(TAG, "Wrote original: " + bounds.outWidth + "x" + bounds.outHeight
                                + " source, ceiling " + ceiling + " px, attempt " + attempt + ".");
                        return Boolean.TRUE;
                    }
                    Log.w(TAG, "Re-encode attempt " + attempt + " failed at ceiling " + ceiling
                            + " px.");
                } catch (OutOfMemoryError e) {
                    Log.w(TAG, "Out of memory at ceiling " + ceiling + " px (attempt " + attempt
                            + ").");
                }
                ceiling /= 2L;
            }

            Log.w(TAG, "Giving up; letting LINE copy the source at full resolution instead.");
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "Could not re-encode the original photo.", t);
            return null;
        }
    }

    private static boolean reencode(Context context, Uri source, Integer rotation, File destination,
            long pixels, long ceiling) {
        Bitmap bitmap = decode(context, source, options(pixels, ceiling));
        if (bitmap == null) return false;

        try {
            // Bake the orientation into the pixels rather than writing an EXIF tag. LINE's own
            // encoders (c1.p and the stock original-path re-encode) both rotate with a Matrix and
            // emit a JPEG with no EXIF, so everything downstream — OBS's derived /preview and
            // standard variants, and the recipient's renderer — assumes orientation is already
            // applied. Costs one extra bitmap; a sideways photo would be far worse.
            bitmap = rotate(bitmap, degrees(context, source, rotation));

            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                return bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
            } catch (Throwable t) {
                Log.w(TAG, "Could not write the re-encoded photo.", t);
                return false;
            }
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Decode options that land the result on {@code ceiling} pixels.
     *
     * <p>{@code inSampleSize} alone is power-of-two, so on its own it overshoots badly — a 108 MP
     * source would quantise to 6.75 MP, barely better than the 4.19 MP this patch exists to avoid.
     * Instead the sample size is taken as far as it can go while staying *above* the ceiling, and
     * the decoder's own density scaler covers the remainder. That also keeps the final bitmap the
     * only large allocation, however big the source is.
     */
    private static BitmapFactory.Options options(long pixels, long ceiling) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        if (pixels <= ceiling) return options;

        int sample = 1;
        while (pixels / ((long) sample * 2L * sample * 2L) >= ceiling) {
            sample *= 2;
        }
        options.inSampleSize = sample;

        long sampled = pixels / ((long) sample * (long) sample);
        double scale = Math.sqrt((double) ceiling / (double) sampled);
        if (scale < 1.0d) {
            options.inScaled = true;
            options.inDensity = DENSITY_BASE;
            options.inTargetDensity = Math.max(1, (int) Math.round(DENSITY_BASE * scale));
        }
        return options;
    }

    /** Lets {@link OutOfMemoryError} escape so the caller can retry at a smaller ceiling. */
    private static Bitmap decode(Context context, Uri source, BitmapFactory.Options options) {
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, options);
        } catch (OutOfMemoryError e) {
            throw e;
        } catch (Throwable t) {
            Log.w(TAG, "Could not read the source photo.", t);
            return null;
        }
    }

    /**
     * Also lets {@link OutOfMemoryError} escape. Retrying smaller and staying upright beats
     * succeeding at full size and shipping the photo sideways.
     */
    private static Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.setRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    /**
     * The rotation to bake in, normalised to {@code [0, 360)}.
     *
     * <p>LINE hands the same {@code Integer} rotation to both encoders {@code c1.f} runs for a
     * send: {@code c1.p}, which writes the standard variant the thumbnail and OBS's derived
     * {@code /preview} come from, and the original-path re-encode this class replaces. Both prefer
     * that value and only read the file's EXIF ({@code c1.d}) when it is {@code null}. Matching
     * that precedence is what keeps the original's orientation agreeing with the standard variant
     * — re-deriving from EXIF unconditionally would send them sideways relative to each other
     * whenever the caller supplied a rotation the file itself does not carry.
     */
    private static int degrees(Context context, Uri source, Integer rotation) {
        int value = (rotation != null ? rotation.intValue() : exifDegrees(context, source)) % 360;
        return value < 0 ? value + 360 : value;
    }

    private static int exifDegrees(Context context, Uri source) {
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) return 0;
            switch (new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    /** @return the source's length in bytes, or {@code -1} if it cannot be determined. */
    private static long sourceLength(Context context, Uri source) {
        String scheme = source.getScheme();
        if (scheme == null || ContentResolver.SCHEME_FILE.equals(scheme)) {
            String path = source.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.exists()) return file.length();
            }
            return -1L;
        }

        try (AssetFileDescriptor descriptor =
                     context.getContentResolver().openAssetFileDescriptor(source, "r")) {
            if (descriptor == null) return -1L;
            long length = descriptor.getLength();
            return length == AssetFileDescriptor.UNKNOWN_LENGTH ? -1L : length;
        } catch (Throwable t) {
            return -1L;
        }
    }
}

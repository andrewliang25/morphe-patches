package app.andrewliang.extension;

import android.os.Build;

/**
 * Returns the signer hash of the genuine LINE app for the "[Fix] Restore biometric login" patch.
 *
 * <p>LINE's two WebAuthn stacks put the app origin {@code android:apk-key-hash-sha256:<hash>} into
 * the {@code clientDataJSON} that the FIDO server reads. The hash is the SHA-256 of the signer from
 * {@code SigningInfo.getApkContentsSigners()}, in Base64 with no padding. On a re-signed build that
 * is the certificate of the user, so the FIDO server refuses the request.
 *
 * <p>LINE uses APK Signature Scheme v3.1 key rotation. Android 13 (SDK 33) and later read the
 * rotated signer, and earlier versions read the original signer.
 */
public final class LineSignature {

    private LineSignature() {}

    /** SHA-256 of the rotated signer "CN=LINE Android", for SDK 33 and later. */
    private static final String ROTATED_SIGNER_SHA256 = "5ugDbgEjTpGfvpz56l0hBDXPwFQjKGQapwoBj1L2PPg";

    /** SHA-256 of the original signer, for SDK 32 and earlier. */
    private static final String ORIGINAL_SIGNER_SHA256 = "5oL+C81gkH3+1RXguKTeA6ocKB0RGgeDOYZgK2CYr9I";

    /** Returns the signer hash that the genuine app sends on this Android version. */
    public static String apkKeyHash() {
        return Build.VERSION.SDK_INT >= 33 ? ROTATED_SIGNER_SHA256 : ORIGINAL_SIGNER_SHA256;
    }
}

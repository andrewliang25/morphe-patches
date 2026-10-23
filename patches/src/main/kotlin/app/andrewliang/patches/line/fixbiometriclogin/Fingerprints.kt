package app.andrewliang.patches.line.fixbiometriclogin

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The `clientDataJSON` builder of the LINE FIDO2 authenticator (`com.linecorp.ltsm.fido2.v.b`).
 *
 * It writes the challenge, the origin `android:apk-key-hash-sha256:<hash>`, the package name and
 * the type into a JSON object. `Fido2Authenticator` hashes that JSON and signs it in
 * `nativeMakeCredential`, so the server reads the origin written here.
 *
 * The method also encodes the challenge with `Base64` before the digest. [instructionMatches] are,
 * in order: the `MessageDigest.digest` of the signer, the origin prefix, then the
 * `Base64.encodeToString` of the digest.
 */
internal object Fido2ClientDataFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "[B"),
    filters = listOf(
        methodCall(definingClass = "Ljava/security/MessageDigest;", name = "digest"),
        string("android:apk-key-hash-sha256:"),
        methodCall(definingClass = "Landroid/util/Base64;", name = "encodeToString"),
    ),
)

/**
 * The facet id of the second WebAuthn stack (`Fido2Util.Companion.getPackageFacetID(Context)`).
 *
 * `PublicKeyCredential` puts this result into the `clientDataJSON` origin. The class and method
 * names stay un-obfuscated in LINE. [instructionMatches] are, in order: the
 * `MessageDigest.digest` of the signer, then the `Base64.encodeToString` of the digest.
 */
internal object WebAuthnFacetIdFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/webauthn/util/Fido2Util\$Companion;",
    name = "getPackageFacetID",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        methodCall(definingClass = "Ljava/security/MessageDigest;", name = "digest"),
        methodCall(definingClass = "Landroid/util/Base64;", name = "encodeToString"),
    ),
)

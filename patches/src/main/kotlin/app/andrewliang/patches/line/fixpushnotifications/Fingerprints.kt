package app.andrewliang.patches.line.fixpushnotifications

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

/**
 * The Firebase Installations (FIS) request builder — obfuscated `ct.c.c(URL, String)`.
 *
 * This is the in-app method that assembles the HTTPS request LINE's bundled Firebase
 * Installations SDK sends to `firebaseinstallations.googleapis.com` to mint an installation /
 * FCM token. It self-reports the app's signing-certificate SHA-1 in the `X-Android-Cert`
 * header (computed from `PackageManager.GET_SIGNATURES` via `rl.a.a` → SHA-1 → uppercase hex
 * in `rl.h.b`). Google's API-key "Android apps" restriction validates that
 * `X-Android-Package` + `X-Android-Cert` pair against LINE's official cert without proving key
 * possession, so a re-signed build is rejected (`FisError: BAD CONFIG`) and never gets a push
 * token — hence no notifications when the app is fully closed.
 *
 * Anchored on the non-obfuscated `X-Android-Cert` header string plus the immediately following
 * `URLConnection.addRequestProperty` call (the site that sends the cert hash). The
 * `HttpURLConnection` return type + exact `(URL, String)` parameters exclude the Remote Config
 * client, which shares the header name but has a different signature and uses
 * `setRequestProperty`. [instructionMatches] are, in order: the `X-Android-Cert` const-string,
 * then its `addRequestProperty(key, value)` invoke.
 */
internal object FisRequestBuilderFingerprint : Fingerprint(
    returnType = "Ljava/net/HttpURLConnection;",
    parameters = listOf("Ljava/net/URL;", "Ljava/lang/String;"),
    filters = listOf(
        string("X-Android-Cert"),
        methodCall(definingClass = "Ljava/net/URLConnection;", name = "addRequestProperty"),
    ),
)

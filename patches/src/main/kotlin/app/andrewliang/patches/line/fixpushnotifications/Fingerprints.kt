package app.andrewliang.patches.line.fixpushnotifications

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

/**
 * The Firebase Installations (FIS) request builder — obfuscated `ct.c.c(URL, String)`.
 *
 * This in-app method assembles the HTTPS request that LINE's bundled Firebase Installations
 * SDK sends to `firebaseinstallations.googleapis.com` to mint an installation / FCM token. It
 * self-reports the app's signing-certificate SHA-1 in the `X-Android-Cert` header (computed
 * from `PackageManager.GET_SIGNATURES` via `rl.a.a` → SHA-1 → uppercase hex in `rl.h.b`).
 * Google's API-key "Android apps" restriction validates the `X-Android-Package` +
 * `X-Android-Cert` pair against LINE's official cert, and does not prove key possession. Thus
 * Google rejects a re-signed build (`FisError: BAD CONFIG`), and the build never gets a push
 * token. As a result, there are no notifications when the app is fully closed.
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

/**
 * The Firebase Remote Config fetch request (`ConfigFetchHttpClient.fetch(…)`).
 *
 * It sends the same `X-Android-Cert` header as the FIS builder, from the same `GET_SIGNATURES`
 * SHA-1 helper. Google compares it against the same API-key restriction. On a re-signed build the
 * fetch fails and LINE keeps its default configuration values.
 *
 * The Google class and method names stay un-obfuscated in LINE. [instructionMatches] are, in
 * order: the `X-Android-Cert` const-string, then its `setRequestProperty(key, value)` invoke.
 */
internal object RemoteConfigFetchFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/remoteconfig/internal/ConfigFetchHttpClient;",
    name = "fetch",
    filters = listOf(
        string("X-Android-Cert"),
        methodCall(definingClass = "Ljava/net/URLConnection;", name = "setRequestProperty"),
    ),
)

/**
 * The Firebase Remote Config realtime request (`remoteconfig.internal.d.i(…)`).
 *
 * The realtime stream sets the same `X-Android-Cert` header as the fetch. The class name drifts, so
 * the fingerprint anchors on the header strings: `X-Accept-Response-Streaming` occurs only in this
 * request. [instructionMatches] are, in order: the `X-Android-Cert` const-string, its
 * `setRequestProperty(key, value)` invoke, then the streaming header.
 */
internal object RemoteConfigRealtimeFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/net/HttpURLConnection;", "Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        string("X-Android-Cert"),
        methodCall(definingClass = "Ljava/net/URLConnection;", name = "setRequestProperty"),
        string("X-Accept-Response-Streaming"),
    ),
)

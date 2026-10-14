package app.andrewliang.patches.line.removeads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The `getBanners` RECV lambda in LINE's Smart Channel Thrift client.
 *
 * Anchored on the `"getBanners"` string plus the `"getBanners failed: unknown result"` error,
 * which only the RECV side throws. This excludes the SEND lambda and the `getBanners_args` /
 * `_result` structs, and needs no name of the obfuscated Thrift classes.
 *
 * The lambda can be one case of a shared `packed-switch` (26.15.1 puts both RECV lambdas next to
 * unrelated branches), so the patch injects after the op-name string, not at method entry.
 */
internal object GetBannersRecvFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("getBanners"),
        string("getBanners failed: unknown result"),
    ),
)

/**
 * The `getPrefetchableBanners` RECV lambda. Same anchoring and injection point as
 * [GetBannersRecvFingerprint].
 */
internal object GetPrefetchableBannersRecvFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("getPrefetchableBanners"),
        string("getPrefetchableBanners failed: unknown result"),
    ),
)

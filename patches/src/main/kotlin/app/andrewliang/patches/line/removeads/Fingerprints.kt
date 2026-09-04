package app.andrewliang.patches.line.removeads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The `getBanners` RECV lambda in LINE's Smart Channel Thrift client (`zp5/c.invoke`).
 *
 * Anchored on the `"getBanners"` string plus the paired `org.apache.thrift.n.a` (recv)
 * call — this excludes the SEND lambda (which uses `n.b`) and the `getBanners_args` /
 * `_result` structs (their `toString` carries the string but returns `String` / takes no
 * `Object` param). It also excludes the two classes that carry `"getBanners"` only as
 * Kotlin `@DebugMetadata`, since that is not a `const-string`.
 *
 * On LINE 26.11.0 this was a shared `packed-switch` whose default branch ran an unrelated
 * wallet op. As of 26.14.0 it is a dedicated single-op lambda — the wallet branch moved out
 * to the calling wrapper — so injecting at method entry would now be equally correct. The
 * patch still injects after the op-name string, which is valid either way.
 */
internal object GetBannersRecvFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("getBanners"),
        methodCall(definingClass = "Lorg/apache/thrift/n;", name = "a"),
    ),
)

/**
 * The `getPrefetchableBanners` RECV lambda (`zp5/d.invoke`), dedicated to this op.
 * Same anchoring rationale as [GetBannersRecvFingerprint].
 */
internal object GetPrefetchableBannersRecvFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("getPrefetchableBanners"),
        methodCall(definingClass = "Lorg/apache/thrift/n;", name = "a"),
    ),
)

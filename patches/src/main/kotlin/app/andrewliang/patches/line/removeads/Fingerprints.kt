package app.andrewliang.patches.line.removeads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * The `getBanners` RECV lambda in LINE's Smart Channel Thrift client (`ai5/g.invoke`).
 *
 * Anchored on the `"getBanners"` string plus the paired `org.apache.thrift.o.a` (recv)
 * call — this excludes the SEND lambda (which uses `o.b`) and the `getBanners_args` /
 * `_result` structs (their `toString` carries the string but returns `String` / takes no
 * `Object` param).
 *
 * Note: this method is a shared `packed-switch` whose default branch runs an unrelated
 * wallet op, so the patch must inject inside the `getBanners` branch only, not at method
 * entry.
 */
internal object GetBannersRecvFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("getBanners"),
        methodCall(definingClass = "Lorg/apache/thrift/o;", name = "a"),
    ),
)

/**
 * The `getPrefetchableBanners` RECV lambda (`ai5/h.invoke`), dedicated to this op.
 * Same anchoring rationale as [GetBannersRecvFingerprint].
 */
internal object GetPrefetchableBannersRecvFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("getPrefetchableBanners"),
        methodCall(definingClass = "Lorg/apache/thrift/o;", name = "a"),
    ),
)

package app.andrewliang.patches.line.originalphoto

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall

/**
 * `t73.k0.b0(ArrayList)` — the picker's per-item pass that stamps `rt7.c.isOriginal` (field `B`)
 * as the selection is finalised. With the "Original" toggle on it clears the flag for anything
 * `>= 20 MB` or `>= 100 MP`, which drops those photos onto the ordinary standard path and
 * resamples them to the tier budget (4.19 MP High / 1.64 MP Normal).
 *
 * Both gates are plain `const-wide/32` literals, so the patch neutralises them by value rather
 * than by rewriting control flow. Ten classes in the APK mention both numbers, but pinning the
 * signature to `(Ljava/util/ArrayList;)V` makes this method the only match — the sibling
 * `k0.X(Z)V` and `m63.n0.d()` carry the same pair for the GIF alerts and the picker's toggle
 * state.
 *
 * The literals themselves are the anchor precisely because they are *not* obfuscated; the
 * `Lrt7/c;->g()J` / `->B:Z` descriptors around them drift between LINE versions and are resolved
 * from the matched method's own bytecode instead.
 */
internal object OriginalPhotoGateFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/ArrayList;"),
    filters = listOf(
        literal(SIZE_GATE),
        literal(PIXEL_GATE),
    ),
)

/**
 * `u13.y0.invoke(Object)` — the lambda that writes the *original* file for a message image,
 * invoked from `u13.c1.f()`'s `IMAGE_ORIGINAL` branch with the destination `File` as its argument.
 *
 * It branches on the source's mime type (`ww0.c.a`): `image/jpeg|png|gif|bmp` (and an unresolvable
 * type) get a raw byte copy, anything else — HEIC, WEBP — gets a full-resolution decode, a
 * `Matrix` rotation and a JPEG re-encode at the tier quality. The patch injects ahead of that
 * branch so oversized photos are re-encoded rather than raw-copied at the upload size limit, and
 * pins the quality the branch uses.
 *
 * Anchored entirely on framework calls in program order — `ContentResolver.getType`,
 * `MimeTypeMap.getMimeTypeFromExtension`, `BitmapFactory.decodeStream`, `Matrix.setRotate` — which
 * no other class in the APK combines, and none of which can be renamed by obfuscation. The
 * `Object invoke(Object)` signature additionally pins it to the Kotlin lambda itself.
 */
internal object OriginalFileWriterFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(definingClass = "Landroid/content/ContentResolver;", name = "getType"),
        methodCall(definingClass = "Landroid/webkit/MimeTypeMap;", name = "getMimeTypeFromExtension"),
        methodCall(definingClass = "Landroid/graphics/BitmapFactory;", name = "decodeStream"),
        methodCall(definingClass = "Landroid/graphics/Matrix;", name = "setRotate"),
    ),
)

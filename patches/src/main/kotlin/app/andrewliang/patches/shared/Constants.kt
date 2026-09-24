package app.andrewliang.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

object Constants {
    /**
     * LINE messenger (jp.naver.line.android).
     *
     * LINE ships as a split app bundle, so it uses [ApkFileType.APKM].
     * Pin the exact version the patches were developed and confirmed against.
     * Add a new version to this list after you verify it.
     */
    val COMPATIBILITY_LINE = Compatibility(
        name = "LINE",
        packageName = "jp.naver.line.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x06C755, // LINE brand green.
        targets = listOf(
            AppTarget(
                version = "26.14.0",
            ),
        ),
    )

    /**
     * Facebook (com.facebook.katana).
     *
     * Facebook ships as a split app bundle, so it uses [ApkFileType.APKM]; all app code is in
     * `base.apk`. Pin the exact version the patches were confirmed against.
     *
     * Facebook releases roughly every two weeks and Redex reassigns every `LX/…` class name on
     * each build, so these patches anchor only on names Redex cannot touch. Re-confirm on a bump —
     * see `docs/facebook-ads-map.md`.
     *
     * Each APKMirror variant is a separate build with different DEX. Thus pin the versionCode of
     * the tested variant for each ABI. The single-`Int` form gives that code to every ABI.
     */
    val COMPATIBILITY_FACEBOOK = Compatibility(
        name = "Facebook",
        packageName = "com.facebook.katana",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x0864FF, // Facebook brand blue.
        targets = listOf(
            AppTarget(
                version = "577.0.0.50.72",
                versionCodes = mapOf(SupportedAbi.ARM64_V8A to 474426275),
                minSdk = 30,
            ),
        ),
    )
}

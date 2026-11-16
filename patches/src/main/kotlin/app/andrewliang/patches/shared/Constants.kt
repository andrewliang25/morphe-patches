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
     *
     * 26.14.0 and 26.16.0 need Android 11 (API 30). 26.15.1 and 26.17.1 need Android 12L
     * (API 32). Every fingerprint must match all supported versions, so anchor only on names R8
     * keeps.
     * Keep targets sorted in ascending version order (oldest to newest).
     *
     * Pin the versionCode of the tested build. All splits of a LINE bundle (every ABI and
     * density) share one versionCode, so the single-`Int` form applies it to every ABI. Read it
     * from `base.apk` (`aapt2 dump badging`).
     */
    val COMPATIBILITY_LINE = Compatibility(
        name = "LINE",
        packageName = "jp.naver.line.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x06C755, // LINE brand green.
        targets = listOf(
            AppTarget(
                version = "26.14.0",
                versionCode = 261400121,
                minSdk = 30,
            ),
            AppTarget(
                version = "26.15.1",
                versionCode = 261510192,
                minSdk = 32,
            ),
            AppTarget(
                version = "26.16.0",
                versionCode = 261600106,
                minSdk = 30,
            ),
            AppTarget(
                version = "26.17.1",
                versionCode = 261710173,
                minSdk = 32,
            ),
        ),
    )

    /**
     * LINE for the call recording patches. Call recording exists only from 26.16.0, so these
     * patches list only the versions that have it. Add each new LINE version here too.
     */
    val COMPATIBILITY_LINE_CALL_RECORDING = Compatibility(
        name = "LINE",
        packageName = "jp.naver.line.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x06C755, // LINE brand green.
        targets = COMPATIBILITY_LINE.targets.filter { it.version in setOf("26.16.0", "26.17.1") },
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

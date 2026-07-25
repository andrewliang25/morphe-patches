package app.andrewliang.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * LINE messenger (jp.naver.line.android).
     *
     * LINE is distributed as a split app bundle, hence [ApkFileType.APKM].
     * Pin the exact version the patches were developed and confirmed against;
     * add to this list as new versions are verified.
     */
    val COMPATIBILITY_LINE = Compatibility(
        name = "LINE",
        packageName = "jp.naver.line.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x06C755, // LINE brand green.
        targets = listOf(
            AppTarget(
                version = "26.11.0",
            ),
        ),
    )
}

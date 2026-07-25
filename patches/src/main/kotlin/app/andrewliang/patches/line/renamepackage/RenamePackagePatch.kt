package app.andrewliang.patches.line.renamepackage

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.resourcePatch

private const val NEW_PACKAGE = "app.andrewliang.line.android"

@Suppress("unused")
val renamePackagePatch = resourcePatch(
    name = "Rename package",
    description = "Renames the app package to $NEW_PACKAGE so a patched, re-signed build is a " +
        "distinct app the Play Store never auto-updates. WARNING: because LINE's identity is " +
        "tied to its package name, this will likely break push notifications and may break " +
        "login. It cannot be installed alongside the stock LINE (uninstall that first). Opt-in.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Change ONLY the manifest `package` attribute (the applicationId Play keys updates off).
    // Deliberately leave provider authorities / permissions as jp.naver.line.android.* so they
    // stay consistent with the strings LINE's own code uses — renaming them would break
    // features whose code references the old authority (e.g. FileProvider file/image sharing).
    // Authorities only need to differ for side-by-side coexistence, which is not the goal here.
    execute {
        document("AndroidManifest.xml").use { document ->
            document.documentElement.setAttribute("package", NEW_PACKAGE)
        }
    }
}

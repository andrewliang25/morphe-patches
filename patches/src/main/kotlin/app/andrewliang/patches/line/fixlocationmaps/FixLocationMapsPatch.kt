package app.andrewliang.patches.line.fixlocationmaps

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import org.w3c.dom.Element
import org.w3c.dom.Node

/** MicroG-RE's package. Its namespace stays `com.google.android.gms`. */
private const val MICROG_PACKAGE = "app.revanced.android.gms"

private const val EXTENSION_CLASS = "Lapp/andrewliang/extension/LocationMaps;"

/**
 * Manifest half: package visibility for MicroG-RE.
 *
 * LINE is `targetSdk 30+`, so without this `createPackageContext` cannot even see MicroG-RE and
 * fails as "package not found". Nameless, so it stays an internal dependency rather than a
 * separate entry in the Manager list.
 *
 * "[Fix] Restore chat backup sign-in via MicroG-RE" adds the same entry, so the check before appending is what
 * keeps the two patches from producing a duplicate when both are enabled.
 */
private val fixLocationMapsManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.getElementsByTagName("manifest").item(0)

            fun Node.hasChild(tag: String, name: String) =
                (0 until childNodes.length)
                    .map { childNodes.item(it) }
                    .any { it is Element && it.tagName == tag && it.getAttribute("android:name") == name }

            val queries = (0 until manifest.childNodes.length)
                .map { manifest.childNodes.item(it) }
                .firstOrNull { it is Element && it.tagName == "queries" }
                ?: manifest.appendChild(document.createElement("queries"))

            if (!queries.hasChild("package", MICROG_PACKAGE)) {
                queries.appendChild(
                    document.createElement("package").apply {
                        setAttribute("android:name", MICROG_PACKAGE)
                    },
                )
            }
        }
    }
}

@Suppress("unused")
val fixLocationMapsPatch = bytecodePatch(
    name = "[Fix] Restore location maps via MicroG-RE",
    description = "Shows a map again on the location screens of a re-signed build. This covers " +
        "the location picker, the location messages in a chat, and the location posts. The tiles " +
        "come from OpenFreeMap and do not look like Google Maps. This patch needs MicroG-RE " +
        "7.0.0 or later. A Root Mount install does not need this patch.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(fixLocationMapsManifestPatch)

    extendWith("extensions/extension.mpe")

    // Google decides whether a map may draw from the API key plus the app's signing certificate.
    // The renderer reports both from a Play Services process, not from LINE, so no patch can
    // correct the certificate — see the extension for the full path. What a patch CAN do is hand
    // LINE a different renderer.
    //
    // `fo/p.b` is where LINE resolves the context it loads the renderer from, and all three of its
    // callers are in `fo/p`, so this one injection covers every map surface in the app.
    //
    // Only this method moves. `DynamiteModule` holds its own "com.google.android.gms" literal, and
    // rewriting that would send ads, vision, ML Kit and TFLite to MicroG-RE as well — the same trap
    // "[Fix] Restore chat backup sign-in via MicroG-RE" avoids by overriding one client instead of the shared
    // base class.
    execute {
        val method = MapsModuleContextFingerprint.method

        // The extension returns null when MicroG-RE is missing, or too old to carry a renderer,
        // and the branch then falls into the original body, which keeps talking to Play Services.
        // So the patch changes nothing on a device that cannot serve the map.
        //
        // v0 is safe to use: the method declares it, and its own first instruction overwrites it
        // (`sget-object v0, Lfo/p;->a`), so nothing downstream reads what this leaves behind.
        // The label binds to a real instruction. A label written inside an injected block is
        // resolved against the block's own addresses and would branch into the middle of an
        // earlier instruction.
        method.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS->getMapsContext(Landroid/content/Context;)Landroid/content/Context;
                move-result-object v0
                if-eqz v0, :original
                return-object v0
            """,
            ExternalLabel("original", method.getInstruction(0)),
        )
    }
}

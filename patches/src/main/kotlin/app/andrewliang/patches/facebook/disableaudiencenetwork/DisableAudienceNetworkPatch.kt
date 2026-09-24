package app.andrewliang.patches.facebook.disableaudiencenetwork

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * The bridge through which the Facebook app serves ads to *other* apps: an app embedding the
 * Audience Network SDK binds to the installed Facebook app to fetch and render its ads.
 */
private val AUDIENCE_NETWORK_COMPONENTS = setOf(
    "com.facebook.ads.internal.ipc.AudienceNetworkRemoteService",
    "com.facebook.ads.internal.ipc.AudienceNetworkRemoteActivity",
    "com.facebook.ads.internal.ipc.AudienceNetworkExportedActivity",
    "com.facebook.ads.AudienceNetworkActivity",
    "com.facebook.audiencenetwork.AudienceNetworkService",
)

private val COMPONENT_TAGS = setOf("activity", "activity-alias", "service", "receiver", "provider")

@Suppress("unused")
val disableAudienceNetworkPatch = resourcePatch(
    name = "[Ad] Disable Audience Network",
    description = "Stops Facebook serving ads to other apps. Those apps then show their own ads " +
        "or none, and rewarded ads can fail.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Disabling is enough — nothing can bind to or start the component — and keeps the manifest
    // diff readable and reversible, unlike removing the entries.
    execute {
        document("AndroidManifest.xml").use { document ->
            var disabled = 0

            COMPONENT_TAGS.forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("android:name") !in AUDIENCE_NETWORK_COMPONENTS) {
                        continue
                    }

                    element.setAttribute("android:enabled", "false")
                    disabled++
                }
            }

            check(disabled > 0) {
                "No Audience Network components found in the manifest; they were renamed or removed"
            }
        }
    }
}

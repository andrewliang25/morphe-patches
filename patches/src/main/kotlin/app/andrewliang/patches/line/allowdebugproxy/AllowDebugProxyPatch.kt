package app.andrewliang.patches.line.allowdebugproxy

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val allowDebugProxyPatch = resourcePatch(
    name = "[General] Trust user-installed CAs",
    description = "Makes LINE trust certificate authorities you install, so your own HTTPS " +
        "proxy can read LIFF and mini-app web traffic. Bank and LINE Pay stay pinned. " +
        "For debugging. Off by default.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // LINE's network security config does not trust user-installed certificate authorities, so a
    // debugging proxy cannot read TLS traffic. The LIFF and mini-app hosts have no pin-set, so the
    // only block is this missing trust. Add a <trust-anchors> block to <base-config> that keeps the
    // system authorities and adds the user ones.
    //
    // This patch does not touch the pin-set blocks on the bank and LINE Pay hosts. Android checks a
    // pin after it trusts the chain. So a proxy certificate still fails the pin, and those
    // connections stay safe.
    //
    // This patch only trusts user authorities for traffic on the platform TLS stack. LINE's own
    // messaging transport (LEGY) uses a bundled TLS library that does not read this file, so it is
    // unaffected.
    execute {
        document("res/xml/network_security_config.xml").use { document ->
            val baseConfig = document.getElementsByTagName("base-config").item(0) as? Element
                ?: throw PatchException("No <base-config> in network_security_config.xml")

            val hasTrustAnchors = (0 until baseConfig.childNodes.length)
                .map { baseConfig.childNodes.item(it) }
                .any { it is Element && it.tagName == "trust-anchors" }

            if (!hasTrustAnchors) {
                baseConfig.appendChild(
                    document.createElement("trust-anchors").apply {
                        appendChild(
                            document.createElement("certificates").apply { setAttribute("src", "system") },
                        )
                        appendChild(
                            document.createElement("certificates").apply { setAttribute("src", "user") },
                        )
                    },
                )
            }
        }
    }
}

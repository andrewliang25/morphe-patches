package app.andrewliang.patches.shared

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val resourceIds = mutableMapOf<String, Long>()

/**
 * Gives the id of the resource [name] of [type] in the app that is patched.
 *
 * Resource ids change between app versions, but resource names do not. A fingerprint that
 * matches a resource id must get the id by name, so that it works on each supported version.
 * Use it in a lazy filter, `literal({ resourceId("string", "...") })`, and add
 * [resourceIdsPatch] to `dependsOn`. The ids are read before any bytecode patch runs.
 */
internal fun resourceId(type: String, name: String): Long =
    resourceIds["$type/$name"] ?: throw PatchException("No resource $type/$name in this APK")

/** Reads all resource ids from `res/values/public.xml`, for [resourceId]. */
internal val resourceIdsPatch = resourcePatch {
    execute {
        resourceIds.clear()
        document("res/values/public.xml").use { document ->
            val nodes = document.getElementsByTagName("public")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as Element
                val id = node.getAttribute("id").removePrefix("0x").toLong(16)
                resourceIds["${node.getAttribute("type")}/${node.getAttribute("name")}"] = id
            }
        }
    }
}

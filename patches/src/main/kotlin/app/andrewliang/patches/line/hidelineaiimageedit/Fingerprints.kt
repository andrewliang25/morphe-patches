package app.andrewliang.patches.line.hidelineaiimageedit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string

/**
 * The parse of the server list of LINE AI image edit entry points, for example
 * `chatroom_context_menu` and `chatroom_image_viewer`. LINE shows an entry only if its name is in
 * this list. The parser reads the key, gets its value from the map, then calls the getter of the
 * current list for the default value. R8 renames the getter but keeps the key, so the key finds it.
 */
internal object LineAiEntryPointsParseFingerprint : Fingerprint(
    filters = listOf(
        string("function.ai.lineai.enabled_entry_points"),
        methodCall(definingClass = "Ljava/util/Map;", name = "get"),
        methodCall(returnType = "Ljava/util/List;", parameters = listOf()),
    ),
)

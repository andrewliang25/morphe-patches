package app.andrewliang.patches.line.groupreaders

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Builds the grid of the message long-press menu (`wl1.l.i` in 26.18.2). Only this method
 * inflates the menu, its rows and its cells.
 */
internal object ContextMenuBuildFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        literal({ resourceId("layout", "chat_ui_message_context_menu_content") }),
        literal({ resourceId("layout", "chat_ui_message_context_menu_content_row") }),
        literal({ resourceId("layout", "chat_ui_message_context_menu_content_item") }),
    ),
)

/** `ChatMessageCommonViewData.toString()`: it prints the chat id, server id and sender of a message. */
internal object MessageCommonToStringFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(
        string("ChatMessageCommonViewData(chatId="),
        string(", serverMessageId="),
        string(", senderMid="),
    ),
)

/**
 * `Profile.toString()` of the account on this phone: it prints the account id (mid). The method
 * loads ", mid=" before "Profile(authTokenV1=", and filters match in program order.
 */
internal object ProfileToStringFingerprint : Fingerprint(
    name = "toString",
    filters = listOf(string(", mid="), string("Profile(authTokenV1=")),
)

/**
 * The coroutine of `ReadCountManager.syncAllNonOpenChatReadCounts` (`gr8.d` in 26.18.2). It is the
 * only caller of the read-range sync `(SQLiteDatabase, Set<chatId>, reason)`.
 */
internal object ReadRangeSyncFingerprint : Fingerprint(
    name = "invokeSuspend",
    custom = { method, _ -> readRangeSyncCall(method) != null },
)

/** The read-range sync call in [method], or null. */
internal fun readRangeSyncCall(method: Method): MethodReference? =
    method.implementation?.instructions?.firstNotNullOfOrNull { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.takeIf {
            it.returnType.startsWith("Lorg/apache/thrift/") &&
                it.parameterTypes.size == 3 &&
                it.parameterTypes[0] == "Landroid/database/sqlite/SQLiteDatabase;" &&
                it.parameterTypes[1] == "Ljava/util/Set;"
        }
    }

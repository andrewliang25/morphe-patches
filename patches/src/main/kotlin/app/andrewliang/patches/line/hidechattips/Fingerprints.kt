package app.andrewliang.patches.line.hidechattips

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * The bind coroutine of `ChatBubbleEntryBannerViewBinder` (`rm1.u` in 26.14.0). It shows the tip
 * under a message bubble, for example "CHECK 探索如何收藏好友傳來的影片 >" under a video. The tip
 * is a ComposeView that inflates from the `chat_ui_entry_banner_stub` ViewStub.
 *
 * The first `instance-of` in the method tests for the "show" state (`sm1.g$b`). If the test is
 * false, the method keeps the stub hidden and removes its click listener.
 *
 * The other filters make the match unique in the APK: `String.valueOf(J)`, the
 * `view_tree_lifecycle_owner` tag id (0x7f0b29ab) and `ComposeView.setContent`.
 */
internal object ChatEntryBannerBindFingerprint : Fingerprint(
    name = "invokeSuspend",
    filters = listOf(
        opcode(Opcode.INSTANCE_OF),
        methodCall(definingClass = "Ljava/lang/String;", name = "valueOf"),
        literal(0x7f0b29ab),
        methodCall(
            definingClass = "Landroidx/compose/ui/platform/ComposeView;",
            name = "setContent",
        ),
    ),
)

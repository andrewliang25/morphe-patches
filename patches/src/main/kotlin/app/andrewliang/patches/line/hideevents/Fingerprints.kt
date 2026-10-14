package app.andrewliang.patches.line.hideevents

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal

/**
 * The slide-out chat menu's **"Events"** row. It is a generic menu item built inside
 * `ChatHistoryMenuFragment.onViewCreated` (the same item class backs other rows such as Notes),
 * so there is no dedicated row class to neuter. Its visibility is instead decided at the build
 * site by a boolean field read (`iget-boolean`), which is then passed as the row's `isVisible`
 * constructor argument right before the Events label `chatmenu_mainlist_button_events`.
 *
 * The label is used only here. The patch takes the last `iget-boolean` before it and overwrites
 * that load with a constant `false`.
 */
internal object EventsMenuRowFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/chathistory/menu/ChatHistoryMenuFragment;",
    name = "onViewCreated",
    filters = listOf(
        literal({ resourceId("string", "chatmenu_mainlist_button_events") }),
    ),
)

package app.andrewliang.patches.line.hideevents

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal

/**
 * The slide-out chat menu's **"Events"** row. It is a generic `d00.z` menu item built inside
 * `ChatHistoryMenuFragment` (the same `d00.z` class backs other rows such as Notes), so there is
 * no dedicated row class to neuter. Its visibility is instead decided at the build site by the
 * boolean field `Lyz/s4;->l:Z` — the only read of that field in the whole app — which is loaded
 * and then passed as the row's `isVisible` constructor argument immediately before the Events
 * label `chatmenu_mainlist_button_events` (0x7f150dfa).
 *
 * Matching the `iget-boolean` of `s4.l` followed (in program order) by the Events label literal
 * uniquely locates that load, which the patch overwrites with a constant `false`.
 */
internal object EventsMenuRowFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(definingClass = "Lyz/s4;", name = "l"),
        literal(0x7f150dfa),
    ),
)

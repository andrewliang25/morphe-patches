package app.andrewliang.patches.line.hideshoppingtab

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideShoppingTabPatch = bytecodePatch(
    name = "Hide Shopping tab",
    description = "Removes the Shopping tab from the main bottom navigation, covering both the " +
        "Japan (Shopping / ショッピング) and Taiwan (Discover / 逛逛) variants.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Remove the COMMERCE and COMMERCE_TW `sget-object` + following `ArrayList.add` pairs from
    // the tab-list builder. Both live in the same `if`/`else-if` chain and are mutually
    // exclusive (each is enabled by its own server setting, for its own region), so a user only
    // ever sees one of them. Dropping only the `add` leaves the branch's trailing `goto` in
    // place, so no other tab moves into the freed slot — matching stock LINE, which shows
    // nothing there when the commerce gate is on.
    // instructionMatches[0] = COMMERCE (earlier), [1] = COMMERCE_TW (later); remove the higher
    // index first so the earlier one stays valid.
    execute {
        val matches = ShoppingTabListFingerprint.instructionMatches
        val commerceIndex = matches[0].index
        val commerceTwIndex = matches[1].index
        ShoppingTabListFingerprint.method.apply {
            removeInstructions(commerceTwIndex, 2)
            removeInstructions(commerceIndex, 2)
        }
    }
}

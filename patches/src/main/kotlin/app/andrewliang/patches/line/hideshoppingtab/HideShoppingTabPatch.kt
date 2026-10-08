package app.andrewliang.patches.line.hideshoppingtab

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipTab
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideShoppingTabPatch = bytecodePatch(
    name = "[Tab] Hide Shopping tab",
    description = "Removes the Shopping tab from the main bottom navigation. This includes the " +
        "Japan variant (Shopping, ショッピング) and the Taiwan variant (Discover, 逛逛).",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Skip the COMMERCE and COMMERCE_TW `sget-object` + following `ArrayList.add` pairs of the
    // tab-list builder while the setting is on. Both live in the same `if`/`else-if` chain and
    // are mutually exclusive (each is enabled by its own server setting, for its own region), so a
    // user only ever sees one of them. Skipping only the pair keeps the branch's trailing `goto`,
    // so no other tab moves into the freed slot — matching stock LINE, which shows nothing there
    // when the commerce gate is on.
    // instructionMatches[0] = COMMERCE (earlier), [1] = COMMERCE_TW (later). Change the higher
    // index first so the earlier one stays valid.
    execute {
        val matches = ShoppingTabListFingerprint.instructionMatches
        val commerceIndex = matches[0].index
        val commerceTwIndex = matches[1].index
        ShoppingTabListFingerprint.method.apply {
            skipTab(commerceTwIndex, "hideShoppingTab")
            skipTab(commerceIndex, "hideShoppingTab")
        }
        markLineSettingIncluded("hideShoppingTab")
    }
}

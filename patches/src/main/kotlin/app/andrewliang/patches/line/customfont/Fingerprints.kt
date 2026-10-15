package app.andrewliang.patches.line.customfont

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val PROVIDER_CLASS = "Lcom/linecorp/line/premiumfont/provider/PremiumFontProvider;"
private const val FONT_SETTING_CLASS = "Lcom/linecorp/line/premiumfont/ui/setting/FontSettingFragment;"

/**
 * androidx `FontProvider.getProvider(PackageManager, FontRequest, Resources)`. It returns the
 * provider of a font request only when the provider app is signed with one of the certificates
 * in the request. It returns `null` when no certificate matches.
 *
 * Anchored on its own failure message. R8 obfuscates androidx together with LINE, so the class
 * drifts (`f7/e` in 26.14.0, `j7/e` in 26.15.1), but the message does not.
 */
internal object FontProviderCheckFingerprint : Fingerprint(
    returnType = "Landroid/content/pm/ProviderInfo;",
    filters = listOf(string("No package found for authority: ")),
)

/**
 * The loader of the Font page list (`er4/f.b` in 26.14.0, `qx4/f.b` in 26.15.1). It decides with
 * a 0/1 flag whether to sync the font list from LINE's server first. Then it reads the fonts from
 * the local database and puts the "Default" row first.
 *
 * Anchored on the "Default" row name, the sync-time check (`System.currentTimeMillis`) and the
 * shape of the flag. Two premium-font use cases also hold the first two, but not the flag.
 */
internal object FontListLoaderFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(definingClass = "Ljava/lang/System;", name = "currentTimeMillis"),
        string("Default"),
    ),
    custom = { method, _ -> findSyncFlag(method) != null },
)

/** The `move` at [index] that sets [register] to the "sync" value. */
internal class SyncFlag(val index: Int, val register: Int)

/**
 * In the list loader, two `move` instructions set the sync flag register. One copies a register
 * that holds `const/4 0x1` (sync), the other a register that holds `const/4 0x0` (no sync). The
 * method sets each of the two constants once, at its top.
 */
internal fun findSyncFlag(method: Method): SyncFlag? {
    val instructions = method.implementation?.instructions?.toList() ?: return null
    fun constantOf(register: Int): Int? = instructions
        .filter { it.opcode == Opcode.CONST_4 && (it as OneRegisterInstruction).registerA == register }
        .singleOrNull()
        ?.let { (it as NarrowLiteralInstruction).narrowLiteral }

    val moves = instructions.withIndex()
        .filter { it.value.opcode == Opcode.MOVE }
        .map { (index, instruction) ->
            Triple(index, (instruction as TwoRegisterInstruction).registerA, instruction.registerB)
        }
    for ((register, group) in moves.groupBy { it.second }) {
        if (group.size != 2) continue
        val sync = group.singleOrNull { constantOf(it.third) == 1 } ?: continue
        if (group.none { constantOf(it.third) == 0 }) continue
        return SyncFlag(sync.first, register)
    }
    return null
}

// The three provider methods override `ContentProvider`, and the settings navigation graph names
// the Fragment. R8 keeps these names, so the fingerprints name them.

internal object PremiumFontProviderOnCreateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf(),
    custom = { method, classDef -> classDef.type == PROVIDER_CLASS && method.name == "onCreate" },
)

internal object PremiumFontProviderQueryFingerprint : Fingerprint(
    returnType = "Landroid/database/Cursor;",
    parameters = listOf(
        "Landroid/net/Uri;",
        "[Ljava/lang/String;",
        "Ljava/lang/String;",
        "[Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, classDef -> classDef.type == PROVIDER_CLASS && method.name == "query" },
)

internal object PremiumFontProviderOpenFileFingerprint : Fingerprint(
    returnType = "Landroid/os/ParcelFileDescriptor;",
    parameters = listOf("Landroid/net/Uri;", "Ljava/lang/String;"),
    custom = { method, classDef -> classDef.type == PROVIDER_CLASS && method.name == "openFile" },
)

internal object FontSettingViewFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup;", "Landroid/os/Bundle;"),
    custom = { method, classDef -> classDef.type == FONT_SETTING_CLASS && method.name == "onCreateView" },
)

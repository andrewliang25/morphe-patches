package app.andrewliang.patches.line.fixpushnotifications

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

/**
 * LINE's ORIGINAL signing-certificate SHA-1 (uppercase hex, no separators) — the value the
 * unpatched app reports in `X-Android-Cert` and which LINE's Firebase API key is allow-listed
 * against.
 *
 * `base.apk` (26.11.0) is signed with APK Signature Scheme v3.1 key rotation, so it carries two
 * certificates: the lineage-ROOT / original key (Android SDK 24–32) and a rotated key (SDK
 * 33+). `rl.a.a` reads the cert via the deprecated `GET_SIGNATURES`, which for a rotated app
 * returns the ORIGINAL (lineage-root) certificate for backward compatibility — so this is the
 * SDK 24–32 signer.
 *
 * If a re-signed build still shows `FisError: "BAD CONFIG"` after applying this patch, the
 * device may be reporting the rotated signer instead; swap in the SDK 33+ cert:
 * `6A2927D945AEA6571E1DA5566802F25045D367BD`.
 */
private const val LINE_ORIGINAL_CERT_SHA1 = "89396DC419292473972813922867E6973D6F5C50"

@Suppress("unused")
val fixPushNotificationsPatch = bytecodePatch(
    name = "Fix push notifications",
    description = "Restores push notifications on re-signed builds when LINE is fully " +
        "closed. If they still don't arrive, use a Root Mount install.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The FIS request builder loads `const-string "X-Android-Cert"` then calls
    // `addRequestProperty(key, value)` where `value` holds the runtime-computed cert hash.
    // Overwrite that value register with LINE's original cert hash right before the call, so a
    // re-signed build reports the allow-listed SHA-1. Only this FIS call site is touched; the
    // shared `rl.a`/`rl.h` helpers (also used by Remote Config) are left intact.
    execute {
        // instructionMatches[1] is the `addRequestProperty` invoke for X-Android-Cert; its
        // value argument is registerE ({connection, key, value}). Reading it avoids hardcoding
        // the register, which can drift between versions.
        val addRequestProperty = FisRequestBuilderFingerprint.instructionMatches[1]
        val valueReg = (addRequestProperty.instruction as FiveRegisterInstruction).registerE

        FisRequestBuilderFingerprint.method.addInstructions(
            addRequestProperty.index,
            "const-string v$valueReg, \"$LINE_ORIGINAL_CERT_SHA1\"",
        )
    }
}

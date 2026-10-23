package app.andrewliang.patches.line.fixbiometriclogin

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/andrewliang/extension/LineSignature;"

@Suppress("unused")
val fixBiometricLoginPatch = bytecodePatch(
    name = "[Fix] Restore biometric login",
    description = "Biometric login can be linked again on a re-signed build. A Root Mount " +
        "install does not need this patch.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // The WebAuthn origin carries the SHA-256 of the app signer, and the FIDO server refuses an
    // unknown origin. Both stacks build the origin in the app process, so replace the encoded hash
    // with the genuine one right after `Base64.encodeToString` returns it.
    execute {
        listOf(
            Fido2ClientDataFingerprint to 2,
            WebAuthnFacetIdFingerprint to 1,
        ).forEach { (fingerprint, encodeMatch) ->
            val method = fingerprint.method
            val moveResultIndex = fingerprint.instructionMatches[encodeMatch].index + 1
            val hashReg = method.getInstruction<OneRegisterInstruction>(moveResultIndex).registerA

            method.addInstructions(
                moveResultIndex + 1,
                """
                    invoke-static {}, $EXTENSION_CLASS->apkKeyHash()Ljava/lang/String;
                    move-result-object v$hashReg
                """,
            )
        }
    }
}

package app.andrewliang.patches.line.fixgooglelogin

import app.andrewliang.patches.line.gmscoreauth.gmsCoreAuthManifestPatch
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val GOOGLE_LOGIN = "Lapp/andrewliang/extension/GoogleLogin;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"

/**
 * Restores "Continue with Google" (log in, re-verify, restore an account) on a re-signed build,
 * through MicroG-RE.
 *
 * On Android 14 and later, androidx.credentials sends the Google ID token request to the system
 * Credential Manager, and Play Services checks LINE's real certificate in its own process. No value
 * in the APK can change that. But LINE also carries the Play Services provider of androidx.credentials,
 * which runs in LINE's process. For a request that asks only for a Google ID token, the patch:
 *
 * 1. picks that provider instead of the system Credential Manager,
 * 2. makes the provider use its Identity Sign-In client instead of the newer Identity Credentials API,
 * 3. binds that one Sign-In connection to MicroG-RE, which reports LINE's original certificate (the
 *    manifest entry that the backup patch adds).
 *
 * Passkeys, passwords and every other Play Services client stay unchanged. Without MicroG-RE every
 * hook returns LINE's original value.
 */
@Suppress("unused")
val fixGoogleLoginPatch = bytecodePatch(
    name = "[Fix] Restore Google login via MicroG-RE",
    description = "Makes \"Continue with Google\" work on a re-signed build, through MicroG-RE. " +
        "Root Mount does not need it.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(gmsCoreAuthManifestPatch)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Provider choice: for a Google ID token request, return LINE's Play Services provider.
        // The sibling `b()` builds it from the manifest, as LINE does before Android 14.
        CredentialProviderChoiceFingerprint.method.apply {
            val providerType = returnType
            val factory = CredentialProviderChoiceFingerprint.classDef.methods.single {
                it.parameterTypes.isEmpty() && it.returnType == providerType &&
                    !AccessFlags.STATIC.isSet(it.accessFlags) &&
                    it.hasString("androidx.credentials.CREDENTIAL_PROVIDER_KEY")
            }
            val p0 = implementation!!.registerCount - 2
            check(p0 + 1 <= 15 && p0 > 0) { "Provider choice registers out of range: p0 = v$p0" }
            addInstructionsWithLabels(
                0,
                """
                    invoke-static { p1 }, $GOOGLE_LOGIN->usePlayServicesProvider($OBJECT)Z
                    move-result v0
                    if-eqz v0, :original
                    invoke-virtual { p0 }, $definingClass->${factory.name}()$providerType
                    move-result-object v0
                    if-eqz v0, :original
                    return-object v0
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }

        // 2. Version check: pass its result through the hook, which returns false for a Google ID
        // token request. The request register is the one that the first Sign-In call after the
        // check, `g(request, cancellationSignal, executor, callback)`, gets. That call is on the
        // false side of the `if-eqz` after the check, and nothing on that side writes the register
        // before the call, so it holds the request at the check too.
        PlayServicesGetCredentialFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val moveResult = fingerprint.instructionMatches[2]
            val result = (moveResult.instruction as OneRegisterInstruction).registerA
            val instructions = method.implementation!!.instructions.toList()
            val requestType = method.parameterTypes[1].toString()
            val call = (moveResult.index + 1 until instructions.size).firstOrNull { index ->
                val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
                instructions[index].opcode == Opcode.INVOKE_VIRTUAL && reference?.returnType == "V" &&
                    reference.parameterTypes.size == 4 && reference.parameterTypes[0].toString() == requestType
            } ?: error("No Sign-In call after the version check in onGetCredential")
            val request = (instructions[call] as FiveRegisterInstruction).registerD
            val branch = instructions[moveResult.index + 1]
            check(branch.opcode == Opcode.IF_EQZ && (branch as OneRegisterInstruction).registerA == result) {
                "No if-eqz on the version check result in onGetCredential"
            }
            val addresses = instructions.runningFold(0) { address, instruction -> address + instruction.codeUnits }
            val target = addresses.indexOf(addresses[moveResult.index + 1] + (branch as OffsetInstruction).codeOffset)
            check(target in (moveResult.index + 2)..call) { "The Sign-In call is not on the false side" }
            val overwritten = (target until call).any {
                instructions[it].opcode.setsRegister() &&
                    (instructions[it] as? OneRegisterInstruction)?.registerA == request
            }
            check(!overwritten && request <= 15 && result <= 15) {
                "Unexpected onGetCredential shape: request v$request, result v$result"
            }
            method.addInstructions(
                moveResult.index + 1,
                """
                    invoke-static { v$result, v$request }, $GOOGLE_LOGIN->useIdentityCredentialsApi(Z$OBJECT)Z
                    move-result v$result
                """,
            )
        }

        // 3. Sign-In client: its package, action and dynamic-lookup getters come from the hooks.
        // The connection reads them in that order. Each getter only returns a constant, so p0 is
        // free to hold the result.
        val actionGetter = SignInClientActionFingerprint.method
        val client = mutableClassDefBy(actionGetter.definingClass)
        val base = superclasses(client).firstOrNull { classDef ->
            classDef.methods.any(::isGmsPackageGetter)
        } ?: error("No GMS package getter above ${client.type}")
        val packageGetter = base.methods.single(::isGmsPackageGetter)
        val lookupName = dynamicLookupName(base, packageGetter.name, actionGetter.name)
            ?: error("No dynamic lookup getter in ${base.type}")
        val lookupGetter = client.methods.single {
            it.name == lookupName && it.parameterTypes.isEmpty() && it.returnType == "Z"
        }
        check(isConstantReturn(actionGetter, Opcode.CONST_STRING) && isConstantReturn(lookupGetter, Opcode.CONST_4)) {
            "Unexpected Sign-In client getters in ${client.type}"
        }
        check(client.methods.none { it.name == packageGetter.name && it.parameterTypes.isEmpty() }) {
            "${client.type} already overrides ${packageGetter.name}"
        }

        actionGetter.addInstructions(
            0,
            """
                invoke-static { p0 }, $GOOGLE_LOGIN->signInAction($OBJECT)$STRING
                move-result-object p0
                return-object p0
            """,
        )
        lookupGetter.addInstructions(
            0,
            """
                invoke-static { p0 }, $GOOGLE_LOGIN->signInDynamicLookup($OBJECT)Z
                move-result p0
                return p0
            """,
        )
        MutableMethod(
            ImmutableMethod(
                client.type,
                packageGetter.name,
                listOf(),
                STRING,
                AccessFlags.PUBLIC.value,
                null,
                null,
                MutableMethodImplementation(1),
            ),
        ).apply {
            addInstructions(
                0,
                """
                    invoke-static { p0 }, $GOOGLE_LOGIN->signInPackage($OBJECT)$STRING
                    move-result-object p0
                    return-object p0
                """,
            )
            client.methods.add(this)
        }
    }
}

private fun Method.hasString(value: String) = implementation?.instructions?.any {
    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == value
} == true

/** Each class above [classDef], nearest first. */
private fun app.morphe.patcher.patch.BytecodePatchContext.superclasses(classDef: ClassDef): Sequence<ClassDef> =
    generateSequence(classDef.superclass?.let { classDefByOrNull(it) }) { current ->
        current.superclass?.let { classDefByOrNull(it) }
    }

/** The GMS client getter of the bind package: `const-string "com.google.android.gms"`, then return. */
private fun isGmsPackageGetter(method: Method): Boolean {
    val instructions = method.implementation?.instructions?.toList() ?: return false
    return method.parameterTypes.isEmpty() && method.returnType == STRING &&
        !AccessFlags.STATIC.isSet(method.accessFlags) && !AccessFlags.FINAL.isSet(method.accessFlags) &&
        instructions.map { it.opcode } == listOf(Opcode.CONST_STRING, Opcode.RETURN_OBJECT) &&
        ((instructions[0] as ReferenceInstruction).reference as StringReference).string == "com.google.android.gms"
}

/**
 * The name of the `()Z` getter that the connection calls right after the package and action
 * getters: whether to look the service up through Play Services.
 */
private fun dynamicLookupName(base: ClassDef, packageGetter: String, actionGetter: String): String? {
    for (method in base.methods) {
        val calls = method.implementation?.instructions?.mapNotNull {
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.takeIf { ref -> ref.definingClass == base.type }
        }?.toList() ?: continue
        for (i in 0 until calls.size - 2) {
            if (calls[i].name == packageGetter && calls[i + 1].name == actionGetter &&
                calls[i + 2].parameterTypes.isEmpty() && calls[i + 2].returnType == "Z"
            ) {
                return calls[i + 2].name
            }
        }
    }
    return null
}

/** A getter that loads one constant into p0 and returns it, with p0 as its only register. */
private fun isConstantReturn(method: Method, opcode: Opcode): Boolean {
    val implementation = method.implementation ?: return false
    val instructions = implementation.instructions.toList()
    return implementation.registerCount == 1 && instructions.size == 2 && instructions[0].opcode == opcode
}

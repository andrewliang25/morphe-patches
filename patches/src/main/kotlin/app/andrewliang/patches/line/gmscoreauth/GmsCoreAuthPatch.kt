package app.andrewliang.patches.line.gmscoreauth

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Play Services' own package, returned by the shared GMS client base class. */
internal const val GMS_PACKAGE = "com.google.android.gms"

/** GmsCore's package: upstream GmsCore's `basePackageName` is `app.revanced`. */
private const val MICROG_PACKAGE = "app.revanced.android.gms"

internal const val GOOGLE_ACCOUNT_TYPE = "com.google"

/** GmsCore's account type — `var_authenticator_account_type` resolves to `basePackageName`. */
private const val MICROG_ACCOUNT_TYPE = "app.revanced"

internal const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

internal const val GMS_CHOOSE_ACCOUNT_ACTION = "com.google.android.gms.common.account.CHOOSE_ACCOUNT"
private const val MICROG_CHOOSE_ACCOUNT_ACTION = "app.revanced.android.gms.common.account.CHOOSE_ACCOUNT"

/** Also the class name GmsCore uses: its namespace stays `com.google.android.gms`. */
internal const val GMS_GET_TOKEN_CLASS = "com.google.android.gms.auth.GetToken"

internal const val GMS_AUTH_SERVICE_ACTION = "com.google.android.gms.auth.service.START"
private const val MICROG_AUTH_SERVICE_ACTION = "app.revanced.android.gms.auth.service.START"

/**
 * Meta-data GmsCore reads to override the certificate it reports for a package. Generic — no
 * allow-list — so GmsCore needs no fork.
 *
 * Only reached while LINE keeps its own package name: GmsCore spoofs the caller's *name* first and
 * looks the certificate up under the spoofed name, so renaming the package (as the YouTube/Photos
 * GmsCore patches do) silently defeats this. Device-confirmed both ways.
 */
private const val META_SPOOFED_SIGNATURE = "app.revanced.android.gms.SPOOFED_PACKAGE_SIGNATURE"

/**
 * LINE's original (lineage-root, SDK 24–32) signer, **lowercase** — GmsCore's own digest comes from
 * `String.format("%02x")` and goes to Google verbatim as `client_sig`, so the override must match
 * that shape. (`fixpushnotifications` needs the same certificate UPPERCASE, because LINE's `rl.h.b`
 * uppercases for `X-Android-Cert`.)
 *
 * Fallback if Google refuses: the rotated SDK 33+ signer `6a2927d945aea6571e1da5566802f25045d367bd`.
 * Re-derive both with `apksigner verify --print-certs base.apk` on a version bump.
 */
private const val LINE_ORIGINAL_CERT_SHA1 = "89396dc419292473972813922867e6973d6f5c50"

/**
 * Manifest half: package visibility for GmsCore, and the certificate override it reads back.
 * Nameless, so it stays an internal dependency rather than a separate entry in the Manager list.
 */
private val gmsCoreAuthManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.getElementsByTagName("manifest").item(0)
            val application = document.getElementsByTagName("application").item(0)

            fun Node.hasChild(tag: String, name: String) =
                (0 until childNodes.length)
                    .map { childNodes.item(it) }
                    .any { it is Element && it.tagName == tag && it.getAttribute("android:name") == name }

            // Applying twice must not leave two entries — PackageSpoofUtils reads whichever the
            // platform returns first, making the effective value ambiguous.
            if (!application.hasChild("meta-data", META_SPOOFED_SIGNATURE)) {
                application.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", META_SPOOFED_SIGNATURE)
                        setAttribute("android:value", LINE_ORIGINAL_CERT_SHA1)
                    },
                )
            }

            // Without this, LINE (targetSdk 30+) cannot see GmsCore at all: every lookup fails as
            // "package not found" long before the auth logic runs. GmsCore targets SDK 29, so it is
            // exempt from the same filtering and can still read the meta-data above.
            val queries = (0 until manifest.childNodes.length)
                .map { manifest.childNodes.item(it) }
                .firstOrNull { it is Element && it.tagName == "queries" }
                ?: manifest.appendChild(document.createElement("queries"))

            if (!queries.hasChild("package", MICROG_PACKAGE)) {
                queries.appendChild(
                    document.createElement("package").apply {
                        setAttribute("android:name", MICROG_PACKAGE)
                    },
                )
            }
        }
    }
}

/**
 * Restores Google sign-in for chat-history backup on re-signed builds by routing the Drive account
 * picker and token fetch to GmsCore, leaving the rest of Play Services alone.
 *
 * An Android OAuth client is keyed to (package name, signing certificate), so re-signing means no
 * client matches and Google refuses the `drive.appdata` grant with `UNREGISTERED_ON_API_CONSOLE`.
 * LINE never reports the certificate itself, so — unlike `fixpushnotifications` — there is no value
 * in the APK to correct. GmsCore builds the token request in userspace and *can* be told to report
 * LINE's original certificate (the manifest meta-data above), which is device-proven to turn the
 * refusal into a `drive.appdata` consent flow under `jp.naver.line.android`.
 *
 * The backup flow needs two things from Play Services — an account *name*
 * (`AccountPicker.newChooseAccountIntent` → extra `authAccount`) and a token for it
 * (`GoogleAuthUtil.getToken`) — reached through the five sites below. Everything else (Maps, ML Kit,
 * FCM, anything Pay touches) keeps talking to real Play Services, so the "Play Services missing"
 * checks a wholesale GmsCore patch must defeat never fire here.
 *
 * **Does NOT fix Google account login/linking**, which goes through
 * `androidx.credentials.CredentialManager`: the *system* picks the provider, so no string in the APK
 * can redirect it. Only the backup picker — an activity intent LINE aims itself — is reachable.
 *
 * Requires MicroG-RE installed, signed in, with device registration enabled. Safe on by default:
 * device-checked with GmsCore disabled, the redirected picker resolves to nothing and the account row
 * does nothing rather than crashing.
 *
 * Device-confirmed end to end (LINE 26.11.0 + MicroG-RE 6.1.4, Android 16): picker, token grant and
 * a completed chat-history restore. Full investigation in `docs/line-patch-map.md`.
 */
@Suppress("unused")
val gmsCoreAuthPatch = bytecodePatch(
    name = "Fix chat backup sign-in via GmsCore",
    description = "Sends the Google account picker and the Drive token of chat-history backup " +
        "through GmsCore. Backup and restore then work on a re-signed build. This patch needs " +
        "MicroG-RE. It does not change how you sign in to a Google account. A Root Mount install " +
        "does not need this patch.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(gmsCoreAuthManifestPatch)

    execute {
        /** Rewrite the string literal a fingerprint match loads, keeping its own register. */
        fun Fingerprint.rewriteString(matchIndex: Int, value: String) {
            val match = instructionMatches[matchIndex]
            val register = (match.instruction as OneRegisterInstruction).registerA
            method.replaceInstruction(match.index, "const-string v$register, \"$value\"")
        }

        // region account selection

        // Matches are, in program order: drive.appdata scope, allowable account type, picker
        // action, target package. The scope is only an anchor; the other three all move.
        AccountPickerIntentFingerprint.apply {
            rewriteString(1, MICROG_ACCOUNT_TYPE)
            rewriteString(2, MICROG_CHOOSE_ACCOUNT_ACTION)
            rewriteString(3, MICROG_PACKAGE)
        }

        // The name the picker returns is matched against AccountManager accounts of this type, and
        // GmsCore registers its accounts under its own type, so this moves with the picker.
        SelectedAccountNameFingerprint.rewriteString(0, MICROG_ACCOUNT_TYPE)

        // ...but the type alone is not enough — this is what sent the first device test back to the
        // picker with no token ever requested. Since Android 8 an authenticator controls account
        // *visibility*: `getAccountsByType` returns only accounts the caller was granted, and
        // GmsCore grants that on its first `setAuthToken` — i.e. after a token, which needs the
        // account to resolve first. The lookup returns empty, both fields stay null, LINE re-prompts.
        //
        // Break the circle by building the Account straight from the name the picker returned; the
        // search only ever existed to turn a name into an Account.
        //
        // Null AND empty string must branch into the original body, which clears the fields: LINE
        // calls this with "" when the backup screen opens before an account is chosen, and
        // `new Account("", type)` throws IllegalArgumentException. The original tolerated "" only
        // because a search for it matched nothing.
        SelectedAccountNameFingerprint.method.apply {
            val fields = implementation!!.instructions
                .filter { instruction -> instruction.opcode == Opcode.IPUT_OBJECT }
                .mapNotNull { instruction ->
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference
                }

            val accountField = fields.first { it.type == "Landroid/accounts/Account;" }
            val nameField = fields.first { it.type == "Ljava/lang/String;" }

            addInstructionsWithLabels(
                0,
                """
                    if-eqz p1, :clear
                    invoke-virtual { p1 }, Ljava/lang/String;->length()I
                    move-result v0
                    if-eqz v0, :clear
                    new-instance v0, Landroid/accounts/Account;
                    const-string v1, "$MICROG_ACCOUNT_TYPE"
                    invoke-direct { v0, p1, v1 }, Landroid/accounts/Account;-><init>(Ljava/lang/String;Ljava/lang/String;)V
                    iput-object v0, p0, ${accountField.definingClass}->${accountField.name}:${accountField.type}
                    iput-object p1, p0, ${nameField.definingClass}->${nameField.name}:${nameField.type}
                    return-void
                """,
                ExternalLabel("clear", getInstruction(0)),
            )
        }

        // endregion

        // region token fetch

        // GoogleAuthUtil re-wraps the name as `new Account(name, "com.google")` before the token
        // request, validating it against its own account-type allow-list first.
        AuthUtilAccountTypeFingerprint.rewriteString(1, MICROG_ACCOUNT_TYPE)
        AuthUtilConstantsFingerprint.rewriteString(2, MICROG_ACCOUNT_TYPE)

        // ...and it binds the token service by explicit ComponentName, not by action — which is how
        // the request still reached real Play Services with every action redirected.
        //
        // Only the PACKAGE half moves. MicroG-RE ships applicationId `app.revanced.android.gms` but
        // keeps namespace `com.google.android.gms`, so its `<service android:name=".auth.GetToken">`
        // really is the class `com.google.android.gms.auth.GetToken` inside the app.revanced
        // package. Rewriting the class name too binds a component that does not exist, and LINE
        // reports a generic access failure with nothing in the log.
        AuthUtilConstantsFingerprint.rewriteString(3, MICROG_PACKAGE)

        // endregion

        // region generic auth binder
        //
        // Not on the getToken path (that binds by ComponentName above), but it is the auth client
        // the rest of the GMS auth surface uses and was part of the device-confirmed build.
        // Retained for that reason; trimming it is untested.

        /**
         * The obfuscated name of `BaseGmsClient.getStartServicePackage()`, found by walking up from
         * the auth client to whichever superclass returns the GMS package literal. Reading it off
         * the hierarchy instead of hardcoding `E` survives the name drifting between versions.
         */
        fun findPackageSupplierName(startClass: String): String {
            var current: String? = startClass
            while (current != null && !current.startsWith("Ljava/")) {
                val classDef = mutableClassDefBy(current)

                classDef.methods.forEach { method ->
                    if (method.returnType == "Ljava/lang/String;" && method.parameterTypes.isEmpty()) {
                        val returnsGmsPackage = method.implementation?.instructions?.any {
                            ((it as? ReferenceInstruction)?.reference as? StringReference)
                                ?.string == GMS_PACKAGE
                        } == true

                        if (returnsGmsPackage) return method.name
                    }
                }

                current = classDef.superclass
            }

            error("No GMS service-package supplier found above $startClass")
        }

        val authClass = AuthServiceActionFingerprint.method.definingClass
        val supplierName = findPackageSupplierName(authClass)

        AuthServiceActionFingerprint.rewriteString(0, MICROG_AUTH_SERVICE_ACTION)

        // Override the package supplier on the auth client alone. The base class declares it
        // `public` (not `final`), so rewriting its literal would redirect EVERY GMS client in the
        // app — Maps, FCM, ML Kit, Pay — which is exactly what this patch avoids.
        val classDef = mutableClassDefBy(authClass)
        if (classDef.methods.any { it.name == supplierName && it.parameterTypes.isEmpty() }) {
            error("$authClass already declares $supplierName(); refusing to rewrite it")
        }

        val servicePackage = MutableMethod(
            ImmutableMethod(
                authClass,
                supplierName,
                emptyList(),
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value,
                null,
                null,
                // 2 registers: v0 scratch, v1 = p0 (this), so `this` is never clobbered.
                MutableMethodImplementation(2),
            ),
        )
        classDef.methods.add(servicePackage)
        servicePackage.addInstructions(
            0,
            """
                const-string v0, "$MICROG_PACKAGE"
                return-object v0
            """,
        )

        // endregion
    }
}

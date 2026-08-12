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
 * Meta-data GmsCore reads to override the certificate it reports for a package.
 *
 * `PackageSpoofUtils.getSpoofedSignature()` calls `getPackageInfo(pkg, GET_META_DATA)` on the
 * package whose signature is being looked up and returns this instead of the real digest. It is
 * generic — no allow-list — so GmsCore itself needs no modification.
 *
 * Only reached while LINE keeps its own package name: GmsCore spoofs the caller's *name* first and
 * then looks the certificate up under the spoofed name, so a patch that renamed the package (as
 * the YouTube/Photos GmsCore patches do) would silently defeat this. Device-confirmed both ways.
 */
private const val META_SPOOFED_SIGNATURE = "app.revanced.android.gms.SPOOFED_PACKAGE_SIGNATURE"

/**
 * LINE's original (lineage-root, SDK 24–32) signer, **lowercase**.
 *
 * GmsCore's own digest comes from `String.format("%02x")` and is handed to Google verbatim as
 * `client_sig`, so the override has to look like the value it replaces. Contrast
 * `fixpushnotifications`, which needs the same certificate UPPERCASE because LINE's `rl.h.b`
 * uppercases before building the `X-Android-Cert` header.
 *
 * Fallback if Google refuses: the rotated SDK 33+ signer
 * `6a2927d945aea6571e1da5566802f25045d367bd`. Re-derive both with
 * `apksigner verify --print-certs base.apk` when bumping the pinned LINE version.
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

            // Applying twice must not leave two entries: PackageSpoofUtils reads whichever the
            // platform hands back first, making the effective value ambiguous.
            if (!application.hasChild("meta-data", META_SPOOFED_SIGNATURE)) {
                application.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", META_SPOOFED_SIGNATURE)
                        setAttribute("android:value", LINE_ORIGINAL_CERT_SHA1)
                    },
                )
            }

            // Without this, LINE (targetSdk 30+) cannot see GmsCore at all and every lookup fails
            // as "package not found" long before any of the auth logic runs. GmsCore itself
            // targets SDK 29 and is exempt from the same filtering, which is why it can read the
            // meta-data above.
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
 * Restores Google sign-in for chat-history backup on re-signed builds by routing the Drive
 * account picker and token fetch to GmsCore, leaving the rest of Play Services alone.
 *
 * **The problem.** An Android OAuth client is keyed to (package name, signing certificate).
 * Re-signing changes the certificate, so Google refuses the `drive.appdata` grant with
 * `UNREGISTERED_ON_API_CONSOLE` and backup/restore is unavailable. LINE's own code never reports
 * the certificate, so — unlike `fixpushnotifications` — there is no value in the APK to correct.
 *
 * **The route.** GmsCore builds the token request in userspace, so it *can* be told to report
 * LINE's original certificate (via the manifest meta-data above). Device-proven: with the original
 * certificate presented, Google returns a consent flow for `drive.appdata` under
 * `jp.naver.line.android` instead of refusing.
 *
 * **What is redirected.** The backup flow needs exactly two things from Play Services: an account
 * *name* (`AccountPicker.newChooseAccountIntent` → result extra `authAccount`) and a token for it
 * (`GoogleAuthUtil.getToken`). Reaching GmsCore for both took five separate sites, each only
 * visible on device once the previous one stopped failing:
 *
 * 1. the picker intent's action, target package and allowable account **type** (`ix4.a0`);
 * 2. how the returned name is turned into an `Account` (`aq.a.b`) — the account type, plus
 *    bypassing the `AccountManager` scan entirely;
 * 3. the account type `GoogleAuthUtil` re-wraps the name with (`vk.b.i`);
 * 4. `GoogleAuthUtil`'s account-type allow-list (`vk.g.<clinit>`);
 * 5. the **ComponentName** `GoogleAuthUtil` binds for token requests (`vk.g.<clinit>`) — an
 *    explicit bind that ignores every action redirect.
 *
 * Everything else — Maps and location sharing, ML Kit, FCM, anything Pay touches — keeps talking to
 * real Play Services, so the "Play Services missing" checks that the wholesale ReVanced-style
 * GmsCore patch has to defeat never trigger here.
 *
 * **What this does NOT fix.** LINE reaches a Google account by two independent routes. Logging in
 * with, or linking, a Google account (`jl0.t` → `kl0.i`, shared by `LineUserAccountSettingsFragment`,
 * `EapLoginFragment`, `RegistrationMethodSelectionFragment`, `AccountRestoreEapLoginFragment`)
 * goes through **`androidx.credentials.CredentialManager`**: LINE asks the platform for a
 * credential and the *system* picks the provider, so no string in the APK names Play Services and
 * nothing here can redirect it. That route stays broken on re-signed builds. Only the backup
 * picker — a plain activity intent LINE aims itself — is reachable.
 *
 * Requires MicroG-RE installed and signed in, with device registration enabled. Without it the
 * redirected picker finds no activity and sign-in fails as it already does — no worse.
 *
 * Device-confirmed end to end (LINE 26.11.0 + MicroG-RE 6.1.4, Android 16): account picker,
 * token grant, and a completed chat-history restore.
 */
@Suppress("unused")
val gmsCoreAuthPatch = bytecodePatch(
    name = "Fix chat backup sign-in via GmsCore",
    description = "Routes chat-history backup's Google account picker and Drive token through " +
        "GmsCore, so backup and restore work on re-signed builds. Requires MicroG-RE. Doesn't " +
        "affect Google account login.",
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

        // The name the picker returns is matched against AccountManager accounts of this type;
        // GmsCore registers its accounts under its own type, so this must move with the picker.
        SelectedAccountNameFingerprint.rewriteString(0, MICROG_ACCOUNT_TYPE)

        // ...but rewriting the type is not enough, and this is what made the first device test
        // drop back to the picker with no token ever requested. Since Android 8 an authenticator
        // controls account *visibility*: `getAccountsByType` returns only accounts the caller has
        // been granted, and GmsCore grants that on its first `setAuthToken` — i.e. after a token,
        // which we cannot get until the account resolves. The lookup therefore returns an empty
        // array, both fields are left null, and LINE re-prompts.
        //
        // Break the circle by constructing the Account directly from the name the picker already
        // returned; the search only ever existed to turn a name into an Account.
        //
        // Both the null AND the empty-string case must branch into the original body, which
        // clears the fields: LINE calls this with "" when the backup screen opens before an
        // account is chosen, and `new Account("", type)` throws IllegalArgumentException. The
        // original code tolerated "" only because a search for it matched nothing.
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

        // GoogleAuthUtil wraps the name back into `new Account(name, "com.google")` before the
        // token request, and validates it against its own allow-list of account types first.
        AuthUtilAccountTypeFingerprint.rewriteString(1, MICROG_ACCOUNT_TYPE)
        AuthUtilConstantsFingerprint.rewriteString(2, MICROG_ACCOUNT_TYPE)

        // ...and it binds the token service by an explicit ComponentName rather than by action,
        // which is how the request reached real Play Services even with every action redirected.
        //
        // Only the PACKAGE half moves. MicroG-RE ships applicationId `app.revanced.android.gms`
        // but keeps namespace `com.google.android.gms`, so its `<service android:name=".auth.
        // GetToken">` really is the class `com.google.android.gms.auth.GetToken` living inside
        // the app.revanced package. Rewriting the class name too binds a component that does not
        // exist, and LINE reports a generic access failure with nothing in the log.
        AuthUtilConstantsFingerprint.rewriteString(3, MICROG_PACKAGE)

        // endregion

        // region generic auth binder
        //
        // Not on the getToken path (that binds by ComponentName above), but this is the auth
        // client the rest of the GMS auth surface uses, and it was part of the build confirmed
        // working on device. Retained for that reason; trimming it is untested.

        /**
         * The obfuscated name of `BaseGmsClient.getStartServicePackage()`, resolved by walking up
         * from the auth client to whichever superclass returns the GMS package literal. Reading it
         * off the class hierarchy rather than hardcoding `E` keeps this working when the
         * obfuscated name drifts between LINE versions.
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
        // `public` (not `final`), so rewriting its literal instead would redirect EVERY GMS client
        // in the app — Maps, FCM, ML Kit, Pay — which is exactly what this patch avoids.
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

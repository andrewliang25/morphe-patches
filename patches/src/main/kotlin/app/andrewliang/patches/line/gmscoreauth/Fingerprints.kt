package app.andrewliang.patches.line.gmscoreauth

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `ix4.a0.b(Context, Object)` — builds the account picker for chat-history backup.
 *
 * This is `AccountPicker.newChooseAccountIntent(...)`: a plain **activity** intent with a hardcoded
 * `setPackage("com.google.android.gms")`, whose result extra `authAccount` is the chosen account
 * *name*. That name is all the Drive path needs — it flows to `hx4.d.invoke(String)` → `l18.d` →
 * `aq.a.b(name)`, and tokens then come from `GoogleAuthUtil`. Credential Manager, which serves
 * LINE's Google *login*, is not involved here.
 *
 * Anchored on the `drive.appdata` scope built in the same method, which pins it to the backup
 * picker rather than any other account chooser. [instructionMatches] in program order: the scope,
 * the allowable account **type**, the picker action, and the target package.
 */
internal object AccountPickerIntentFingerprint : Fingerprint(
    returnType = "Landroid/content/Intent;",
    filters = listOf(
        string(DRIVE_APPDATA_SCOPE),
        string(GOOGLE_ACCOUNT_TYPE),
        string(GMS_CHOOSE_ACCOUNT_ACTION),
        string(GMS_PACKAGE),
    ),
)

/**
 * `aq.a.b(String)` — `GoogleAccountCredential.setSelectedAccountName`.
 *
 * Turns the picked name into an `Account` by scanning
 * `AccountManager.getAccountsByType("com.google")` for a name match, then stores the match and the
 * name in two fields. Both the account type *and* the scan itself need dealing with — see the patch
 * for why the scan cannot succeed for a GmsCore account.
 *
 * [instructionMatches]: the account type, then the `getAccountsByType` call it feeds.
 */
internal object SelectedAccountNameFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        string(GOOGLE_ACCOUNT_TYPE),
        methodCall(name = "getAccountsByType"),
    ),
)

/**
 * `vk.b.i(Context, String, String)` — `GoogleAuthUtil.getToken(context, accountName, scope)`.
 *
 * Wraps the account *name* back into `new Account(name, "com.google")` before handing it to the auth
 * service. Left alone, GmsCore receives an account of a type it does not own and can resolve
 * nothing. [instructionMatches]: the `Account` allocation, then the account type literal.
 */
internal object AuthUtilAccountTypeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        newInstance("Landroid/accounts/Account;"),
        string(GOOGLE_ACCOUNT_TYPE),
    ),
)

/**
 * `vk.g.<clinit>` — `GoogleAuthUtil`'s two static constants:
 *
 * 1. its allow-list of account types (`{"com.google.work", "cn.google", "com.google"}`), validated
 *    before any token request — a GmsCore account fails that check;
 * 2. the **ComponentName it binds for token requests**,
 *    `com.google.android.gms/com.google.android.gms.auth.GetToken`.
 *
 * The second is the one that is easy to miss: `getToken` does not resolve a service by action at
 * all, it binds that component *explicitly*, so redirecting every auth action still left the request
 * going to real Play Services, which refused an account type it does not own
 * (`SecurityException: uid … cannot get user data for accounts of type: app.revanced`).
 *
 * [instructionMatches] in program order: two account types left alone, the `com.google` type, then
 * the ComponentName's package and class.
 */
internal object AuthUtilConstantsFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        string("com.google.work"),
        string("cn.google"),
        string(GOOGLE_ACCOUNT_TYPE),
        string(GMS_PACKAGE),
        string(GMS_GET_TOKEN_CLASS),
    ),
)

/**
 * `com.google.android.gms.internal.auth.d.D()` — the start-service action of the auth service
 * client (the obfuscated `BaseGmsClient.getStartServiceAction()` override).
 *
 * Not on the `getToken` path, which binds by ComponentName (see [AuthUtilConstantsFingerprint]),
 * but it is the generic auth binder and was part of the build confirmed working on device.
 * Retained for that reason. Trimming it is untested.
 */
internal object AuthServiceActionFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(string(GMS_AUTH_SERVICE_ACTION)),
)

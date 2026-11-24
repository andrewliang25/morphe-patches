# LINE on a re-signed build

What breaks when LINE is signed with another certificate, and which patches fix or work around it. See [README.md](README.md) for the other LINE docs.

## App-signature reads & the re-signing family (full inventory)

A re-signed (Standard) install breaks any feature where LINE **reports its own signing certificate
to a server that pinned LINE's official certificate**. This section inventories every place LINE
26.15.1 and 26.16.0 read their signer, so the full blast radius is visible in one pass. It records the
biometric-link finding (issue #195) and the payment analysis (issues #96, #161).

Scan: `getApkContentsSigners`, `PackageInfo.signatures`, `SigningInfo`, and the `android:apk-key-hash`
literal across all `smali*` trees. Twenty sites read the signer. Four self-report it to a pinning
server; the rest check Google, a third-party SDK, or a caller, or are Pay-native.

Obfuscated names are given as `26.15.1` / `26.16.0`. 26.16.0 has the same sites as 26.15.1. Only
the obfuscated names changed, and the text strings of each class are the same.

### Sites that break on re-sign (self-report to a pinning server)

| Site | Mechanism | Hash format | Status |
|---|---|---|---|
| `fz/c` / `ez/c` → Firebase Installations (FIS) | `X-Android-Cert` header → FCM token | SHA-1 hex | Fixed (`fixpushnotifications`) |
| `pr/a` / `or/a` → Firebase Remote Config (`ConfigFetchHttpClient.fetch`, `remoteconfig.internal.d.i`) | same `X-Android-Cert` header, shared Firebase helper | SHA-1 hex | Fixed (`fixpushnotifications`) |
| `com/linecorp/ltsm/fido2/v.b` | WebAuthn `clientDataJSON` origin `android:apk-key-hash-sha256:…` → LINE FIDO server | SHA-256 base64 | Fixed (`fixbiometriclogin`, on by default, issue #195) |
| `com/linecorp/webauthn/util/Fido2Util` | a second WebAuthn stack (`com.linecorp.webauthn`, callers `PublicKeyCredential`, `Authenticator`), same origin | SHA-256 base64 | Fixed (`fixbiometriclogin`, on by default) |

### Sites that do NOT break on re-sign

| Site | What it is | Why re-sign does not break it |
|---|---|---|
| `er/j`, `er/k` / `dr/j`, `dr/k` | GMS / Play availability bootstrap | reads Google's cert, not LINE's |
| `zs/e` / `ys/e` (`PhoneskyVerificationUtils`), `qv/a` / `pv/a` (Google signature verifier), `cu/f5` / `bu/f5` (wearable) | bundled Google SDK verifiers | verify Google / Play certs, local |
| `j7/e` (both versions) | androidx `FontProvider` cert check | checks the font provider, not LINE |
| `wv/f` / `vv/f` | AndroidManifest / `versionCode` reader | not a certificate report |
| `qy5/f0` / `yy5/g0` | Samsung Smart Switch migration provider | verifies the caller's cert, local |
| `u48/c` / `g88/b` | LY shared-storage ContentProvider allowlist (`lysharedstorage_release`) | verifies calling apps, local |
| `VosWrapperBase`, `com/vkey/android/cg` | VKey V-OS / V-Guard (LINE Pay) | native; Pay only; out of scope |
| `fsecurity/model/Jws.signatures` | a JWS / JWT signature list | not the APK signing cert |

### LINE's integrity checks — what enforces and what is telemetry

From decompiling LINE 26.14.0 (detail in `work/decompiled-line-<ver>/NOTES-integrity-checks.md`, gitignored):

- The **core messenger has no enforcing** signature/integrity check — a re-signed patched APK runs fine for login, chat and general features.
- Every root/debugger/emulator/signature check in the general app is **telemetry only** (Firebase Crashlytics, obfuscated `es` package; Sentry `io.sentry.android.core`) — no `exit`/`finish`/`throw`.
- **No Play Integrity / SafetyNet** is bundled. The `attest` code is LINE's own *server-side* WebAuthn/FIDO2 plus a fire-and-forget "DeviceAttestation" WorkManager job that always returns success.
- **Enforcement is confined to LINE Pay**, via the bundled native **VKey V-OS / V-Guard** engine (`libvosWrapperEx.so`; `VosWrapperBase.getAppSignerHash()` → native SHA-256 signer compare), which initializes only on entering Pay flows. Block/Warn/Bypass per threat is **server-driven** (`TamperSettingsGetResDto`); on block, `VGuardDetectionActivity` ends the Pay flow without killing the app.
- **Implication:** messaging patches are safe on a re-signed build. Defeating LINE Pay would mean neutralizing the VKey native library (out of scope). LINE obfuscates class and method names *including* `org.apache.thrift`'s, so a seemingly-stable framework type is not a safe anchor.

### Push notifications while closed & the "[Fix] Restore push notifications" patch

Re-signing (Standard install) breaks push while the app is *fully closed*. Unlike Google sign-in Path 1, this **is** fixable in-APK — `patches/line/fixpushnotifications/`, device-confirmed on a re-signed LINE 26.11.0, descriptors re-anchored for 26.14.0.

- **Symptom:** notifies while open or backgrounded (LINE's persistent **LEGY** socket) but not when swiped from Recents; reopening flushes the queue. Closed, LINE's only wakeup path is **FCM**.
- **Root cause:** LINE's bundled Firebase Installations (FIS) SDK self-reports the signing-cert SHA-1 in the `X-Android-Cert` header (`ct.c.c(URL,String)` → `rl.a.a` = `getPackageInfo(pkg, GET_SIGNATURES)` → SHA-1 of `signatures[0]` → uppercase hex in `rl.h.b`). LINE's embedded Firebase API key is restricted to LINE's official cert SHA-1, so a re-signed APK is rejected. Evidence: `/data/data/jp.naver.line.android/no_backup/PersistedInstallation.*.json` = `"Status":4` (REGISTER_ERROR) + `"FisError":"BAD CONFIG"` + `"TokenCreationEpochInSecs":0` → no FCM token, so LINE's servers have nothing to push to. Battery/App-Standby ruled out (bucket `5` EXEMPTED + deviceidle-whitelisted still fails).
- Why the patch works: the cert is a self-reported header that LINE builds in its own process, so a patch can overwrite it. The patch anchors the FIS request builder (`ct.c.c(URL, String)`), the Remote Config fetch (`ConfigFetchHttpClient.fetch`), and the Remote Config realtime stream (`remoteconfig.internal.d.i`) on the `X-Android-Cert` string and its `addRequestProperty` or `setRequestProperty` call. It overwrites the value register (`registerE`) with the original LINE SHA-1. Only those three call sites change. The shared `rl.a` and `rl.h` helpers stay intact.
- Which cert: `base.apk` uses APK Signature Scheme v3.1 key rotation (two certs), and `GET_SIGNATURES` returns the root one. The patch writes the SDK 24 to 32 signer `89396DC419292473972813922867E6973D6F5C50`. If `BAD CONFIG` stays, use the rotated SDK 33+ signer `6A2927D945AEA6571E1DA5566802F25045D367BD`. Read both with `apksigner verify --print-certs base.apk` on a version bump. LINE 26.14.0 and 26.15.1 carry the same v3.1 lineage, so both hashes are unchanged.
- Disassembly and device proof: in `ct/c`, `ConfigFetchHttpClient`, and `remoteconfig.internal.d`, `const-string v$reg, "<sha1>"` must land in `registerE` right before each `X-Android-Cert` send. On the device, `PersistedInstallation*.json` `Status` changes from `4` to `3` (REGISTERED).

### Biometric login link and the "[Fix] Restore biometric login" patch (issue #195)

Settings ▸ Account ▸ Device unlock method ▸ Link registers the device as a FIDO2 / WebAuthn
authenticator for passwordless login (`PrimaryFidoRegistrationActivity`). This is account security,
not LINE Pay. Both `com.linecorp.ltsm.fido2.v.b(Context, String, byte[])` and
`com.linecorp.webauthn.util.Fido2Util$Companion.getPackageFacetID(Context)` read
`SigningInfo.getApkContentsSigners()[0]`. Each method takes the SHA-256 digest, encodes it with
`Base64.encodeToString` (no padding), and writes `android:apk-key-hash-sha256:<base64>` into the
WebAuthn `clientDataJSON` origin. On a re-signed build the hash is the certificate of the user. The
origin matches no facet of the account, so the FIDO server refuses the registration.

- Patch (`patches/line/fixbiometriclogin/`, on by default): in both methods, right after the
  `move-result-object v$hashReg` of `Base64.encodeToString`, the patch calls
  `LineSignature.apkKeyHash()` and writes the genuine signer hash into `v$hashReg`.
- Which cert: `SigningInfo.getApkContentsSigners()` returns the rotated v3.1 signer on Android 13
  (SDK 33) and later (`5ugDbgEjTpGfvpz56l0hBDXPwFQjKGQapwoBj1L2PPg`). It returns the original
  signer on SDK 32 and earlier (`5oL+C81gkH3+1RXguKTeA6ocKB0RGgeDOYZgK2CYr9I`).
  `LineSignature.apkKeyHash()` selects the hash with `Build.VERSION.SDK_INT >= 33`.
- Device test (LINE 26.15.1, SDK 35): opening Settings ▸ Account ▸ Device unlock method ▸ Link ▸
  Enable and touching the fingerprint sensor ran `com.linecorp.ltsm.fido2.v.b` through
  `Fido2Authenticator.c`. It replaced `d2LuJD0RV+89beiEaQ/3CjhfwlH+j0ketscfao45Liw` with
  `5ugDbgEjTpGfvpz56l0hBDXPwFQjKGQapwoBj1L2PPg`. The FIDO server accepted the registration, and the
  button changed from Link to Unlink. Native `nativeMakeCredential` signs the `clientDataJSON` from
  Java and does not read the APK certificate on its own.

### Google passkey sign-in and creation — not fixable on a re-signed build

On a re-signed (Standard) build, a Google-managed passkey cannot sign in to LINE. Settings ▸
Account ▸ Passkey ▸ Create a passkey also fails (issue #256). This is a dead end, not a bug in a patch.

Device test (26.17.1, Android SDK 37, 2026-11-20). The passkey option on the login screen goes
through the Android Credential Manager, which passes the request to Google Play Services
(`PasswordAndPasskeyService`). Play Services looks at the calling app and finds no passkey for it. The
log shows, in the Play Services process, `GoogleSignatureVerifier: package info is not set correctly`
and then `[ListPasskeyCredentialsOperation] Result size: 0`. The chooser never finishes, so the
passkey page loads forever.

Create a passkey takes the same path (26.18.2). `bg0/f` builds an `androidx.credentials`
`CreatePublicKeyCredentialRequest` (`v7/j`) and calls `CredentialManager.createCredential` (`v7/t.b`).
The reporter of #256 saw the biometric prompt, and then the create failed. This is static only: we
did not reproduce the create on our device.

The check is not in LINE. A passkey is bound to LINE's web site (relying party `line.me`). Before an
Android app can use it, Play Services reads the app signing certificate. Then it compares the
certificate with the Digital Asset Links file on LINE's server (`line.me/.well-known/assetlinks.json`). A re-signed build
has a different certificate, so it is not in that file. Play Services returns no passkey, and it
does not save a new one. This is the same class as Google account sign-in (Path 1 below). Another
process reads the certificate and compares it with a file on a server, so no APK patch reaches it.

"[Fix] Restore biometric login" does not help here. It rewrites the origin that LINE builds in its
own process for the first-party FIDO2 flow (the biometric device link). Every caller of that flow
asks `Fido2.getApiClient(Context, true)`, which gives LINE's own `LineFidoApiClient`. The Credential
Manager passkey path does not run that code.

MicroG-RE does not help either, so do not send passkey calls to it. Its release notes list passkey
support from 7.1.0 (MicroG-RE PR #230), but its FIDO code makes the same asset-links check as Play
Services. `RequestHandling.kt` (`getAllFacetIdCandidates`) reads the caller's real installed
certificate with `PackageManager.getSignatures`, and `checkIsValid` then compares it with
`line.me/.well-known/assetlinks.json`. A re-signed LINE fails there. MicroG-RE's signature spoofing
(the `SPOOFED_PACKAGE_SIGNATURE` manifest metadata, `PackageSpoofUtils`) applies only to
`PackageUtils.firstSignatureDigest`, which the GMS API calls use. The FIDO check does not use it. This
is by design. If an app can claim another certificate, any app can use your `line.me` passkey.
The routes through MicroG-RE (`fixgooglelogin`, `gmscoreauth`) work because MicroG-RE skips a check
that Play Services makes. For passkeys it makes the same check, so it gives nothing that Play
Services does not. Checked against the MicroG-RE source (2026-11-24), not on a device.

MicroG-RE also cannot give LINE the passkeys that Google syncs. It keeps passkeys only on the
phone. `ScreenLockCredentialStore` makes each private key in the AndroidKeyStore, and that key
cannot leave the phone. The credential ID goes into the local `fido.db` database
(`Database.insertKnownRegistration`). For a sign-in, `PasswordAndPasskeyService` lists only the
`fido.db` entries with `Transport.SCREEN_LOCK` for the relying party. Its only other choice is
"hybrid", which means a QR code for another phone or a security key.

The `folsom` code (`KeyRetrievalService`, `LocalKeyManager`) does reach Google for the
`gpm_passkeys` security domain. But it only gets the Google Password Manager encryption key for the
app that asks for it. It does not download or use synced passkeys, and it gives none to other apps.
So a signature-spoofing fork of MicroG-RE can only make new passkeys that stay on one phone. Checked
against the MicroG-RE source (commit `ab96213`, 2026-11-21), not on a device.

Google Password Manager is part of Play Services, not a service that MicroG-RE can call. To use
synced passkeys, MicroG-RE must rebuild passkey sync itself, and it has no sync code today.
Chromium contains the Chrome desktop client for synced passkeys, so a rebuild has a public model.

A rebuild is still a large job. Google can change or block its servers at any time. Chrome desktop
also seems to enroll each device with a Google server (the "enclave") with the Google Password
Manager PIN. This is from memory and not read in the Chromium source. A rebuild also does not fix
a re-signed LINE, because MicroG-RE must still make the asset-links check above. Not investigated
further (2026-11-24).

Workarounds: sign in with phone number and password, or with the QR code from a signed-in device.
These sign you in, but they do not give you a passkey.

Root Mount is the only install that keeps passkeys working. It mounts the patched `base.apk` over
the Play Store install, so Android still reports LINE's genuine certificate and Play Services
accepts it. Device test (2026-11-24): on a Root Mount build, Settings ▸ Account ▸ Passkey ▸ Create
a passkey works. Passkey sign-in on Root Mount is not tested yet.

### Can a payment run inside a modified LINE app? (issues #96, #161)

No, and the bundle does not try. Three layers:

1. LINE's in-app Pay screen runs the native VKey V-OS / V-Guard check at Pay-flow start
   (`getAppSignerHash()` in `libvosWrapperEx.so`, server policy BLOCK / WARN). A re-signed build
   fails it — the root / jailbreak warning in issue #96. Native; not patchable in Java; out of scope.
2. `[General] Redirect LINE Pay` (`line.disablepay`) does not try to pass the check. It closes LINE's
   Pay screens and forwards to the standalone LINE Pay app, which keeps its original signature (see the
   section above).
3. A merchant checkout that must finish inside LINE (issue #161) therefore fails: the redirect closes
   the screen, and the standalone app cannot always resume the web hand-off.

So in-app payment on a re-signed build would need to defeat the native VKey engine. That is out of
scope, and it is the regulated layer where a flagged account carries money.

**Root Mount and the unmount route.** A Root Mount install overlays the patched APK over the genuine
Play Store `base.apk`; the original is still on disk. A user can unmount the module, force-stop LINE
(or reboot, per the mount), and launch the genuine app to pay. VKey checks the running process's signer
at Pay-flow start, so the genuine process passes with nothing tampered. This is mechanically clean
because the payment runs in the unmodified app, not in the mod. The residual unknown is at the account
level: whether LINE's servers keep a device-risk signal from earlier tampered Pay attempts. This is
untested by us. So it stays a thing a user can choose on their own device — not a README
recommendation, and we still do not recommend Root Mount for Pay.

---

## Google account access — two independent paths (one patched, one not)

LINE reaches a Google account by **two mechanisms that share nothing**. Establish which one a symptom
belongs to before touching anything — conflating them cost a round of wrong conclusions.

| | **Path 1 — log in / link a Google account** | **Path 2 — Drive chat-history backup** |
|---|---|---|
| Entry points | Settings ▸ Account, registration, account restore | Settings ▸ chat backup |
| LINE code | `jl0.t.a(...)` → `kl0.i` (`TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`) | `ix4.a0.b(Context, Object)` |
| Mechanism | `androidx.credentials.CredentialManager` | `AccountPicker.newChooseAccountIntent` + `GoogleAuthUtil` |
| Who chooses the provider | **the system** (framework enumerates registered credential providers) | **LINE**, via a hardcoded `setPackage` |
| Patchable | **Yes** — `fixgooglelogin`, MicroG-RE sign-in screen device-confirmed 2026-11-14 | **Yes** — `gmscoreauth`, device-confirmed working 2026-08-12 |

Both fail on a re-signed build for the same underlying reason: an Android OAuth client is keyed to
**(package name, signing certificate)**, so re-signing means no client matches and Google refuses
with `UNREGISTERED_ON_API_CONSOLE`.

Path 1's callers: `settings/account/LineUserAccountSettingsFragment`,
`registration/ui/fragment/EapLoginFragment`, `RegistrationMethodSelectionFragment`,
`registration/restore/ui/AccountRestoreEapLoginFragment`. The `kl0` package is LINE's third-party
identity login (it also carries `apple_login_code_result_key`), shared by Google and Apple.

### Path 1 through the system Credential Manager (stock)

On Android 14 and later, androidx.credentials asks the platform for a credential. `system_server`
enumerates registered providers and picks Google's `…credman.service.GoogleIdService`. MicroG-RE does
not implement `android.service.credentials.CredentialProviderService`, so it cannot be offered as a
provider there. Device log:

```
CredentialManager(1527): Provider session created for:
    com.google.android.gms/…auth.api.credentials.credman.service.GoogleIdService
Auth.Api.Credentials: [AccountReauth_flowRunner] Flow failed.
colz: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE].
```

### Path 1 is patchable — what `fixgooglelogin` does

The earlier verdict ("not patchable") missed a second provider. LINE also carries androidx.credentials'
Play Services provider (`CredentialProviderPlayServicesImpl`), which runs in LINE's process and is
used before Android 14. Its legacy branch uses the Identity Sign-In client, and MicroG-RE publishes
that service as `app.revanced.android.gms.auth.api.identity.service.signin.START`. The approach comes
from the Linimal bundle (uta-a/linimal, ADR 0004). The patch changes three sites (26.16.0 names):

| Site | Edit |
|---|---|
| Provider choice `t7.a0.a(a0, Object)` (TV and car feature strings) | For a request whose options are all `TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`, return the Play Services provider from `t7.a0.b()` (built from `CREDENTIAL_PROVIDER_KEY`). |
| `CredentialProviderPlayServicesImpl.onGetCredential` | The `isAvailableOnDevice(252400000)` result goes through a hook that returns false for that request, so the provider takes the Sign-In client branch. The hook also arms the redirect for 60 seconds. |
| Sign-In client `ds.n` (action getter holds `…identity.service.signin.START`) | A new `E()` override, and `D()` and `F()`, ask the extension. The first connection after the arm binds to `app.revanced.android.gms` with MicroG-RE's action and no dynamic lookup. Dynamic lookup ignores the package and returns Play Services. |

Passkey and password requests, and every other GMS client, keep their original values. Without
MicroG-RE every hook returns the original value. The certificate comes from the manifest entry of
`gmscoreauth`, so the patch depends on that manifest patch. The client names are `an.n` (26.14.0),
`es.n` (26.15.1) and `ds.n` (26.16.0).

**Verification (26.16.0, Android SDK 37, MicroG-RE 7.2.1, 2026-11-14).** Settings ▸ Account ▸ Google ▸
Link opened MicroG-RE's `AssistedSignInActivity` and then `AuthSignInActivity` ("Choose an account
to continue to LINE"). The log showed `clientPackageName:jp.naver.line.android`,
`idTokenRequested=true` and LINE's server client ID `4586549225-quhf8ev7…`. The Play Services
sign-in screen did not open. Back returned to Account settings with no crash. The account was not
picked, so a completed link or login is not confirmed.

A **Root Mount** install keeps the original signature and needs neither patch. On a version bump,
re-check by watching
`logcat | grep -iE "START u0|CredentialManager|Auth\.Api|SpoofUtils|UNREGISTERED_ON_API_CONSOLE"` at
the account-selection tap — the obfuscated names (`ix4.a0`, `aq.a`, `kl0.i`, `u7.*`, `l18/d`,
`hx4/i`) drift.

### Path 2 is patchable — what `gmscoreauth` does

Device-confirmed end to end (LINE 26.11.0 + MicroG-RE 6.1.4, Android 16): GmsCore's picker, a
granted `drive.appdata` token, and a completed chat-history restore.

The backup flow needs exactly two things from Play Services — an account **name** and a token for it
— but reaching GmsCore for both took **five separate string sites**, each visible only after the
previous stopped failing. Expect the same if it regresses: fix one, re-test, find the next.

| # | Site | What moves | Symptom if missed |
|---|---|---|---|
| 1 | `ix4.a0.b()` — `AccountPicker.newChooseAccountIntent` | picker action, `setPackage`, `allowableAccountTypes` | Google's picker appears; `UNREGISTERED_ON_API_CONSOLE` |
| 2 | `aq.a.b()` — `setSelectedAccountName` | account type **and** the `AccountManager` scan (see below) | account selected, then the picker immediately re-opens, no token requested |
| 3 | `vk.b.i()` — `GoogleAuthUtil.getToken` | the type it re-wraps the name with | GmsCore gets an account type it does not own |
| 4 | `vk.g.<clinit>` — `GoogleAuthUtil` allow-list | the `com.google` entry | request rejected client-side before any bind |
| 5 | `vk.g.<clinit>` — bind **ComponentName** | the *package* half only | `SecurityException: uid <gms> cannot get user data for accounts of type: app.revanced` |

**Site 2 needs code, not a string.** `setSelectedAccountName` resolves the picked name by scanning
`AccountManager.getAccountsByType(type)`, which can never work for a GmsCore account: since Android 8
an authenticator controls account **visibility**, and GmsCore grants it on its first `setAuthToken` —
that is, *after* a token, which needs the account to resolve first. The scan returns empty, both fields
stay null, and LINE re-prompts forever. The patch builds the `Account` directly from the name. The
scan only ever existed to turn a name into an `Account`.

Guard **both** null and empty string, branching into the original body: LINE calls
`setSelectedAccountName("")` when the backup screen opens before an account is chosen, and
`new Account("", type)` throws `IllegalArgumentException` — a crash the original avoided only because
a search for `""` matched nothing.

**Site 5 is the trap worth remembering.** `getToken` resolves no service by action at all —
`vk.g.<clinit>` builds an explicit `ComponentName("com.google.android.gms",
"com.google.android.gms.auth.GetToken")`, and an explicit bind ignores every action redirect. Move
**only the package half**: MicroG-RE ships `applicationId app.revanced.android.gms` but keeps
`namespace com.google.android.gms`, so its `<service android:name=".auth.GetToken">` really is the
class `com.google.android.gms.auth.GetToken` inside the `app.revanced` package. Rewriting the class
name too binds a component that does not exist — LINE shows a generic access error with **nothing in
the log**, because no service starts.

Everything else keeps talking to real Play Services — ML Kit, FCM, anything Pay touches — so the
"Play Services missing" checks a wholesale GmsCore patch must defeat never fire. Maps is the one
other surface that can be moved, by its own opt-in patch and through a different mechanism
(`DynamiteModule`, not a GMS client) — see "Location maps" below.

Also required and easy to forget: a `<queries>` entry for GmsCore (LINE is `targetSdk 30+`, so without
it every lookup fails as "package not found"), and **not** renaming LINE's package (below).

### Redirecting one GMS client without redirecting all of them

Every GMS client extends an obfuscated `BaseGmsClient` (`kl.d` in 26.11.0) with two overridable
suppliers: the start-service **action** (`D()`) and the service **package** (`E()`). `E()` is `public`,
*not* `final`, and returns `"com.google.android.gms"` — rewriting that literal would redirect every GMS
client in the app, so give the one target class its own override instead. Resolve the supplier's name
by walking supers for the method returning that literal; `E` drifts.

### Certificate handling (device-proven)

Google **will** grant `oauth2:…/auth/drive.appdata` for `jp.naver.line.android` once GmsCore presents
the original certificate — first shown by a standalone probe, then by a real restore through the
shipped patch. Probe A/B, same account and scope:

| `client_sig` sent | Google's response |
|---|---|
| Morphe re-sign cert | `IOException: Error=UNREGISTERED_ON_API_CONSOLE` |
| LINE's original `89396dc4…` | consent flow (`AskPermissionActivity` launched) — accepted |

`PackageSpoofUtils` reads `app.revanced.android.gms.SPOOFED_PACKAGE_NAME` /
`…SPOOFED_PACKAGE_SIGNATURE` off the package being looked up. It is generic — no allow-list, no
per-app table — so **no GmsCore fork is needed**.

The order is the trap. `PackageUtils.getAndCheckPackage()` spoofs the caller's **name first**, then
`AuthManager` looks the certificate up **under the spoofed name** — off whatever
`jp.naver.line.android` is installed, not off the caller's own meta-data. So:

- If the spoofed package is absent, `getPackageInfo` throws and the code falls back to
  `KNOWN_GOOGLE_PACKAGES` (Google's apps only) → `client_sig` goes out **null**. This is why the
  ReVanced flow works for YouTube/Photos and would not for LINE.
- Thus the patch **must keep LINE's package name**. The rename the YouTube/Photos GmsCore
  patches perform would silently defeat the override.
- GmsCore targets **SDK 29**, so package-visibility filtering does not apply to it. A
  `targetSdk 30+` app needs a `<queries>` entry to see it at all.

Case matters: GmsCore's digest comes from `String.format("%02x")` and is sent verbatim as
`client_sig`, so the override is **lowercase** — the opposite of `fixpushnotifications`, which
needs the same certificate UPPERCASE because LINE's `rl.h.b` uppercases for `X-Android-Cert`.

### Class/anchor map (LINE 26.14.0)

| What | Descriptor | Notes |
|---|---|---|
| Backup account picker | `ix4.a0.b(Context, Object)` | Anchored on the `drive.appdata` scope built in the same method. Strings in program order: scope, account type, picker action, target package. |
| Picker result handler | `ix4.a0.d(int, Intent)` | Reads extra `authAccount`. |
| Drive credential | `aq.a` (`GoogleAccountCredential`) | `b(String)` = `setSelectedAccountName` (scans by account type, fields `e` = Account / `d` = name); `a()` = `getToken` → `vk.b.i`. |
| GoogleAuthUtil | `vk.b.i(Context, String, String)` | `getToken(context, name, scope)`; wraps `new Account(name, type)`. |
| GoogleAuthUtil constants | `vk.g.<clinit>` | account-type allow-list + the bind `ComponentName`. `vk.g.b(Context, ComponentName, vk.f)` does the bind via `kl.k1`. |
| Backup call sites | `hx4.d.invoke(String)` → `l18.d`; `hx4.i`, `hx4.l`, `hx4.z`, `backuprestore.data.b$c`, `AutomaticChatBackupWorker` | all consume the account **name** string. |
| Auth service client | `com.google.android.gms.internal.auth.d` | `D()` → `com.google.android.gms.auth.service.START`. Inherits `E()`. |
| GMS client base | `kl.d` | `E()` = service package, `public` not `final`. **Do not rewrite its literal.** |
| Credential Manager path | `jl0.t` → `kl0.i`; wrapper `u7.*` | Path 1. The system provider cannot be intercepted, but `fixgooglelogin` moves the request to LINE's own Play Services provider (see above). |
| Classic sign-in (unused) | `al.g`, `SignInHubActivity`, `gm.n` | Present in the APK but referenced only from inside the GMS library — **no LINE code path uses them**, so there is no legacy route to force. `al.g.j()`'s `GOOGLE_SIGN_IN` intent is in-app (`setClass(SignInHubActivity)`) and must not be redirected. The Identity Sign-In client (`gm.n` in 26.11.0, `ds.n` in 26.16.0) is reachable through the Play Services provider of androidx.credentials, and MicroG-RE 7.2.1 does publish `app.revanced.android.gms.auth.api.identity.service.signin.START`: see "Path 1 is patchable". |

If anyone ports the shared `gmsCoreSupportPatch` from morphe-patches, note two fingerprints it
hard-requires do **not** resolve against LINE: `GooglePlayUtilityFingerprint` (needs a
`MetadataValueReader` string LINE does not ship) and `ServiceCheckFingerprint` (LINE's only
`"Google Play Services not available"` match is a constructor in `gl.h`). For LINE neither is
needed anyway — real Play Services is installed, so the "GMS missing" checks never trigger.

---

## Location maps & the "[Fix] Restore location maps via MicroG-RE" patch

Reported as issue #92 ("share location doesn't work"). On a re-signed build every map draws as an
empty grid. The current location is still found and can still be sent, and only the map is blank.
A Root Mount install keeps LINE's signature, so it never shows the problem.

### The third re-signing break, and the only one with no certificate fix

| | Push notifications | Google sign-in | **Location maps** |
|---|---|---|---|
| What Google checks | the Firebase API key against the cert | the OAuth client against the cert | the Maps API key against the cert |
| Who reports the cert | **LINE's own** bundled FIS code | Play Services | **Play Services** |
| Error | `FisError: BAD CONFIG` | `UNREGISTERED_ON_API_CONSOLE` | `Authorization failure` |
| Fix | rewrite the header (`fixpushnotifications`) | none | redirect the renderer |

LINE ships only the Maps SDK v2 **thin client** — six stub classes under
`com/google/android/gms/maps/` plus obfuscated delegates in `eo/` and `fo/`. The renderer is not in
the APK. `fo/p.b` asks `DynamiteModule` for the maps module and gets a remote `Context`; `fo/p.c`
then class-loads `com.google.android.gms.maps.internal.CreatorImpl` out of it. That renderer runs
**inside LINE's process**, but it binds `com.google.android.gms.maps.auth.ApiTokenService` **in the
Play Services process**, which reads LINE's real signing certificate from `PackageManager` and asks
Google's server for a tile token.

So the key is ours to change but the certificate is not: it is read in another process, and the
decision is made server-side. This is the opposite of `fixpushnotifications`, where LINE's own code
builds the `X-Android-Cert` header. **Nothing in LINE's dex reports the certificate, so no patch can
correct it.** The API key itself sits at `res/values/strings.xml` (`google_maps_key`), reached from
the manifest `com.google.android.geo.API_KEY` meta-data — a user-supplied key of their own would
also work, but it costs each user a Google Cloud project with billing enabled, so no patch does that.

### It is eight surfaces, not one

Every layout that inflates a Maps view breaks together, which is why the report said "or sent to
chat" — the message bubble looks broken too:

| Layout | Surface |
|---|---|
| `select_location_activity.xml` (`SupportMapFragment`) | the location picker; `SelectLocationActivity` |
| `location_viewer.xml` (`SupportMapFragment`) | full-screen viewer; `LocationViewerActivity` |
| `chat_ui_row_send_msg_location.xml` (`MapView`) | outgoing location bubble in a chat |
| `chat_ui_row_receive_msg_location.xml` (`MapView`) | incoming location bubble in a chat |
| `note_post_media_location.xml`, `note_home_write_location_media_layout.xml` | Notes location posts |
| `post_media_location.xml`, `timeline_write_location_media_layout.xml` | Timeline/VOOM location posts |

All eight go through the one renderer, so one redirect fixes all of them.

**Why "send current location" survives:** the fix comes from the framework
`android.location.LocationManager` (wrapper `jp/naver/line/android/service/f`). LINE bundles **no**
`FusedLocationProviderClient` — the `com.google.android.gms.location.*` classes it carries are value
types only. Tiles and coordinates are independent paths, so one can fail while the other works.

### What the patch does

MicroG-RE added a Maps renderer **for exactly this redirect** (its PR #187; PR #219 then put all
three renderers in one APK, selectable at runtime, and added a keyless **OpenFreeMap** fallback).
Its `CreatorImpl` keeps the legacy fully-qualified name on purpose, because clients look it up by
that name inside MicroG's classloader. The renderer validates no key and no signature.

`fo/p.b(Context, eo/d$a)Landroid/content/Context;` is the one choke point — all three of its callers
are inside `fo/p`. The patch prepends four instructions:

```smali
invoke-static { p0 }, Lapp/andrewliang/extension/LocationMaps;->getMapsContext(Landroid/content/Context;)Landroid/content/Context;
move-result-object v0
if-eqz v0, :original     # ExternalLabel bound to the original first instruction
return-object v0
```

The extension returns MicroG-RE's context via
`createPackageContext("app.revanced.android.gms", CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY)`,
or `null` when MicroG-RE is absent — and then the branch falls into the original body and LINE keeps
using Play Services. **So the patch is a no-op without MicroG-RE**, never a new failure.

Four things that made this safe, and are worth keeping:

- **The package alone is not enough — load the class.** MicroG-RE only got a Maps renderer in
  **7.0.0** (2026-09-01). An older build has the package but no `CreatorImpl`, and then
  `createPackageContext` succeeds while `fo/p.c`'s `loadClass` throws `ClassNotFoundException`,
  hits `:catch_2` and returns the `const/4 v0, 0x0` it set at the top — **null**. `fo/p.a` then
  does `invoke-interface {v1}, Lfo/r;->d()I` inside a try that catches only `RemoteException`, so
  the null renderer is an **uncaught NPE**: the patch would turn a blank map into a crash. This
  matters because **6.1.4 is the version `gmscoreauth` documents** for chat backup, and it is
  older than the renderer. The extension therefore loads `CreatorImpl` itself before it returns
  the context, and returns `null` when the class is missing.
- **`v0` is free at index 0.** The method is `.locals 1`, and its own first instruction is
  `sget-object v0, Lfo/p;->a`, so nothing reads what the injection leaves in `v0`.
- **The extension caches the miss as well as the hit.** LINE caches the module context in the
  static `Lfo/p;->a`, and the early return skips that store. The injection also runs *before*
  LINE's own cache check, so without a remembered miss every call would repeat a failing
  `createPackageContext` and log a stack trace.
- **`DynamiteModule` is left alone.** Its own `"com.google.android.gms"` literal
  (`DynamiteModule.smali:1887` and `:2599`) is shared by every dynamite consumer — ads, vision,
  ML Kit barcode/OCR/face, TFLite. Rewriting it would send all of them to MicroG-RE. Same trap as
  `kl.d.E()` for the GMS clients: redirect the one call site, not the shared resolver.

**Real Play Services must still be installed.** `fo/p.b` is not the first gate. `fo/p.a` — the
entry point all three `eo/*` callers use — first runs `am/j.e(Context, 0xcc77c0)` (Play Services
13.4) and throws `am.g` (`GooglePlayServicesNotAvailableException`) before `b` is ever reached.
`am/j.e` looks for the literal package `com.google.android.gms`, which MicroG-RE does not claim. So
on a de-Googled device that has only MicroG-RE, this patch does nothing and the map stays blank —
silently, because `eo/d` catches `am.g`. The patch needs **both** real Play Services and MicroG-RE
7.0.0+.

**One LINE fallback is neutered, deliberately.** `fo/p.a`'s `:catch_1` handles
`UnsatisfiedLinkError` by resetting `Lfo/p;->a` to null and calling `c(ctx, LEGACY)` to retry with
the legacy renderer. The patched `b` ignores `p1` and returns the extension's own static cache, so
that retry gets the same MicroG renderer. If MicroG's MapLibre native library ever fails to load,
there is no second chance. Accepted: the alternative is a reset hook for a case no device has shown.

**A risk that turned out to be already handled.** MicroG-RE declares a `ModuleDescriptor` for
`maps_dynamite` only, while modern clients prefer `maps_core_dynamite`. LINE's own body already
catches that failure and retries `maps_dynamite`, then falls back to `getRemoteContext` — so even a
plain package redirect would have worked. Bypassing `DynamiteModule` avoids the question entirely.

**Tradeoff:** tiles come from OpenFreeMap through MapLibre, so they do not look like Google Maps,
there is no satellite view, and Street View is stubbed. Upstream microG rates this renderer "mostly"
complete with minor glitches.

The patch is `default = true`. It only does something when MicroG-RE is installed, so most users
either get a working map or no change at all. **One group loses something:** a Root Mount user who
installed MicroG-RE for chat backup already has working Google tiles, and this patch replaces them
with OpenFreeMap ones. That user must turn the patch off.

### Values that drift on a version bump

| What | 26.14.0 | How to re-find it |
|---|---|---|
| Maps module loader | `fo/p.b(Context, eo/d$a)` | The four string anchors below all live in this method. |
| Renderer creator loader | `fo/p.c(Context, eo/d$a)` | Loads `…maps.internal.CreatorImpl` by name. |
| Renderer preference enum | `eo/d$a` (`LEGACY`) | Left unpinned by the fingerprint on purpose. |
| Anchors (stable) | `com.google.android.gms.maps_legacy_dynamite`, `…maps_core_dynamite`, `…maps_dynamite`, `Unable to load maps module, maps container context is null` | These belong to Google's bundled Maps client, not to LINE, so they survive LINE's obfuscation. |
| MicroG-RE package | `app.revanced.android.gms` | Namespace stays `com.google.android.gms`. |

**Not yet device-confirmed.** Verified statically only: the fingerprint resolves against 26.14.0,
the four injected instructions sit at index 0 with the branch bound to the original first
instruction, both `.catch` ranges rebase correctly, and a whole-APK offset sweep (648,591 methods,
14 dex files) is clean. Still to check on a device with MicroG-RE 7.1.0 installed:

1. All four visible surfaces draw tiles, and the picker pans and repositions its pin.
2. `logcat -s "Google Android Maps SDK"` no longer prints `Ensure that the following Android Key
   exists`, and `logcat -s AndrewLineMaps` prints "Loading maps from MicroG-RE."
3. **QR / barcode scanning still works** — this is what proves leaving `DynamiteModule` alone kept
   ML Kit on real Play Services. The most important regression check.
4. Without MicroG-RE installed, the build behaves exactly as an unpatched one (blank map, no crash).

package app.andrewliang.patches.line.fixgooglelogin

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal const val PLAY_SERVICES_PROVIDER =
    "Landroidx/credentials/playservices/CredentialProviderPlayServicesImpl;"

/** The Google Play Services version from which the provider uses the newer Identity Credentials API. */
internal const val PRE_U_MIN_GMS_APK_VERSION = 252400000

internal const val SIGN_IN_ACTION = "com.google.android.gms.auth.api.identity.service.signin.START"

/**
 * The androidx.credentials provider choice (`t7.a0.a(a0, Object)` in 26.16.0). It takes the
 * request and picks the system Credential Manager on Android 14 and later. Its TV and car checks
 * hold the two feature strings.
 */
internal object CredentialProviderChoiceFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    strings = listOf("android.software.leanback", "android.hardware.type.automotive"),
    custom = { method, _ ->
        method.parameterTypes.size == 2 && method.parameterTypes[1].toString() == "Ljava/lang/Object;"
    },
)

/**
 * The Play Services provider's `onGetCredential` for a get request. It asks whether Play Services
 * is at least [PRE_U_MIN_GMS_APK_VERSION]. If so, it uses the Identity Credentials API, which Play
 * Services answers. If not, it uses the Identity Sign-In client, which the patch moves to MicroG-RE.
 */
internal object PlayServicesGetCredentialFingerprint : Fingerprint(
    definingClass = PLAY_SERVICES_PROVIDER,
    name = "onGetCredential",
    returnType = "V",
    custom = { method, _ ->
        method.parameterTypes.size == 5 &&
            method.parameterTypes[0].toString() == "Landroid/content/Context;"
    },
    filters = listOf(
        literal(PRE_U_MIN_GMS_APK_VERSION),
        methodCall(name = "isAvailableOnDevice", parameters = listOf("I"), location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
)

/**
 * The Identity Sign-In client's service action getter (`ds.n.D()` in 26.16.0). Its class is the
 * client.
 */
internal object SignInClientActionFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    strings = listOf(SIGN_IN_ACTION),
)

package app.andrewliang.patches.line.callrecording

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val PREMIUMCALL_GATE = "Lcom/linecorp/voip2/feature/premiumcall/a;"

/**
 * `com.linecorp.voip2.feature.premiumcall.a.a(qr7.c, Set)` runs on every call connect. When the
 * call advertises the "REC" capability it sets up the recording extension with
 * `iv7.b.M(iv7.b$a)` (26.17.1 names). The auto-start injection goes right after that setup call.
 * The package name is not obfuscated, so this anchors on every version that has the feature
 * (26.16.0+).
 */
internal object CallRecordingGateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == PREMIUMCALL_GATE &&
            method.name == "a" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[1] == "Ljava/util/Set;"
    },
)

/** `premiumcall.a.d()` is the enable gate (`st7.e.l() != ro7.b.INACTIVE`). Forced true. */
internal object CallRecordingEnabledFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == PREMIUMCALL_GATE &&
            method.name == "d" &&
            method.returnType == "Z" &&
            method.parameterTypes.isEmpty()
    },
)

/** `premiumcall.a.e()` is the premium gate (`d() && st7.e.r()`). Forced true. */
internal object CallRecordingPremiumFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == PREMIUMCALL_GATE &&
            method.name == "e" &&
            method.returnType == "Z" &&
            method.parameterTypes.isEmpty()
    },
)

/**
 * The peer-notify emitter (`nv7.n0.emit(Object, Continuation)` in 26.17.1). On a `true` value it
 * sends the "record" FeatureShare SET (`tw7.f.H(tw7.a)`) that tells the other party. On `false` it
 * clears it (`R("record")`). The silent switch skips the send. R8 renames the class, so the match is
 * the one `emit` that casts its value to `Boolean` and holds the "record" string.
 */
internal object CallRecordingNotifyFingerprint : Fingerprint(
    name = "emit",
    filters = listOf(
        checkCast("Ljava/lang/Boolean;"),
        string("record"),
    ),
)

private const val UPLOAD_NETWORK = "Lcom/linecorp/voip2/feature/recording/upload/b\$a;"

/**
 * A method of the export router (`bu7/a` in 26.16.0), which sends each finished recording to Keep
 * Memo or to device storage. Its method `b()` picks the upload network type from the enum
 * `recording.upload.b$a`, whose package and constant names are not obfuscated. No other method
 * reads both constants. The patch finds the router class from this match.
 */
internal object CallRecordingExportNetworkFingerprint : Fingerprint(
    returnType = "Ljava/lang/Enum;",
    filters = listOf(
        fieldAccess(definingClass = UPLOAD_NETWORK, name = "UNMETERED", opcode = Opcode.SGET_OBJECT),
        fieldAccess(definingClass = UPLOAD_NETWORK, name = "CONNECTED", opcode = Opcode.SGET_OBJECT),
    ),
)

private const val MEDIA_TYPE = "Lcom/linecorp/andromeda/core/session/constant/MediaType;"

/**
 * The time-limit check of a call recording (`au7/d$d$b.emit()` in 26.16.0). On each recording
 * duration update it compares the voice time with 3600000 ms and the video time with the video
 * limit. When one is reached, it emits `MediaType.AUDIO` or `AUDIO_VIDEO`, and LINE stops the
 * recording. The literal and the `MediaType` constants are not obfuscated.
 */
internal object CallRecordingLimitFingerprint : Fingerprint(
    name = "emit",
    filters = listOf(
        literal(3600000),
        fieldAccess(definingClass = MEDIA_TYPE, name = "AUDIO", opcode = Opcode.SGET_OBJECT),
        fieldAccess(definingClass = MEDIA_TYPE, name = "AUDIO_VIDEO", opcode = Opcode.SGET_OBJECT),
    ),
)

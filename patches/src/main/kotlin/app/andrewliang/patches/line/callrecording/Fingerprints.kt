package app.andrewliang.patches.line.callrecording

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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
 * The peer-notify emitter (`nv7.n0.emit(Object, Continuation)` in 26.17.1, `qv7.o0` in 26.18.2).
 * On a `true` value it sends the "record" FeatureShare SET that tells the other party
 * (`tw7.f.H(tv7.a)` in 26.17.1, `ww7.f.W()` in 26.18.2). On `false` it clears it. The silent switch
 * skips the send. R8 renames everything here and in 26.18.2 moved the "record" string into the
 * FeatureShare class, so the match is the one `emit` that casts its value to `Boolean` and calls
 * [featureShareTypes] (see [featureShareInterfaces]).
 */
internal fun callRecordingNotifyFingerprint(featureShareTypes: Set<String>) = Fingerprint(
    name = "emit",
    filters = listOf(checkCast("Ljava/lang/Boolean;")),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.INVOKE_INTERFACE &&
                ((instruction as ReferenceInstruction).reference as MethodReference).definingClass in
                featureShareTypes
        } == true
    },
)

/**
 * The interfaces of the call FeatureShare implementation (`tw7.g` in 26.17.1, `ww7.g` in 26.18.2).
 * Its null checks keep the texts "modelImpl", "scope" and "sessionModel". One other class holds
 * the same three texts, so this returns the interfaces of both. Only the FeatureShare interface
 * is called from a `Boolean` emitter.
 */
internal fun BytecodePatchContext.featureShareInterfaces(): Set<String> {
    val anchors = setOf("modelImpl", "scope", "sessionModel")
    val interfaces = mutableSetOf<String>()
    classDefForEach { classDef ->
        val strings = classDef.methods.flatMap { method ->
            method.implementation?.instructions?.mapNotNull {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
            } ?: emptyList()
        }.toSet()
        if (strings.containsAll(anchors)) interfaces += classDef.interfaces
    }
    return interfaces
}

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

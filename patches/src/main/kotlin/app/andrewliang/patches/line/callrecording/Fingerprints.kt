package app.andrewliang.patches.line.callrecording

import app.morphe.patcher.Fingerprint

private const val PREMIUMCALL_GATE = "Lcom/linecorp/voip2/feature/premiumcall/a;"

/**
 * `com.linecorp.voip2.feature.premiumcall.a.a(aq7.d, Set)` runs on every call connect. When the
 * call advertises the "REC" capability it sets up the recording extension with
 * `st7.b.z(st7.b$a)`. The auto-start injection goes right after that `z()` call. The package name
 * is not obfuscated, so this anchors on every version that has the feature (26.16.0+).
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
 * `xt7.n0.emit(Object, Continuation)` is the peer-notify emitter. On a `true` value it sends the
 * "record" FeatureShare SET (`dv7.f.L(dv7.a)`) that tells the other party. On `false` it clears it
 * (`O("record")`). The silent switch skips the `L` send. Obfuscated name is stable on 26.16.0.
 */
internal object CallRecordingNotifyFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Lxt7/n0;" && method.name == "emit"
    },
)

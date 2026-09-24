package app.andrewliang.patches.facebook.amoledtheme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * The four resolvers, which answer the first of the four routes that a colour takes.
 *
 * Facebook has two colour systems and they share no code. Mig is the older one, and each of its
 * ~120 getters ends in one method per colour scheme. FDS is the newer one and carries most of the
 * app: `FDSColors` answers Litho, `LX/1tK` answers the view code.
 *
 * R8 keeps the owner class names but renames the methods on every release. Thus each fingerprint
 * uses the class, the return type, the parameters and the shape of the body, and never a name.
 *
 * See `docs/facebook-theme-map.md`.
 */

internal const val DARK_COLOR_SCHEME = "Lcom/facebook/mig/scheme/schemes/DarkColorScheme;"
internal const val FDS_COLORS = "Lcom/facebook/fds/core/theme/component/FDSColors;"
internal const val FDS_COLOR_SCHEME = "Lcom/facebook/mig/scheme/schemes/fds/FdsColorScheme;"

/**
 * The Mig dark scheme resolver. The return type and the interface call remove the three sibling
 * methods. The light scheme has the same shape, thus the class is part of the fingerprint.
 */
internal object DarkSchemeResolveFingerprint : Fingerprint(
    definingClass = DARK_COLOR_SCHEME,
    returnType = "I",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(opcode = Opcode.INVOKE_INTERFACE, returnType = "I"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.RETURN),
    ),
)

/**
 * The FDS colour method for Litho. It tries three sources and returns the first that answers, thus
 * it has two `return` instructions and both need a hook.
 */
internal object FdsColorsComponentFingerprint : Fingerprint(
    definingClass = FDS_COLORS,
    returnType = "I",
    parameters = listOf("L", "L"),
    filters = listOf(
        methodCall(definingClass = FDS_COLORS, returnType = "Ljava/lang/Integer;"),
        opcode(Opcode.RETURN),
        opcode(Opcode.RETURN),
    ),
)

/** The same three sources, from a plain `Context`. The parameters separate it from the sibling. */
internal object FdsColorsContextFingerprint : Fingerprint(
    definingClass = FDS_COLORS,
    returnType = "I",
    parameters = listOf("Landroid/content/Context;", "L", "L"),
    filters = listOf(
        methodCall(definingClass = FDS_COLORS, returnType = "Ljava/lang/Integer;"),
        opcode(Opcode.RETURN),
        opcode(Opcode.RETURN),
    ),
)

/**
 * The step to the resolver of the view code. That resolver has a Redex name and no literal, thus
 * the patch matches this wrapper and reads the descriptor of its `invoke-static`. The wrapper is
 * the only method that reads the one `Context` field.
 */
internal object FdsSchemeResolveFingerprint : Fingerprint(
    definingClass = FDS_COLOR_SCHEME,
    returnType = "I",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(definingClass = FDS_COLOR_SCHEME, type = "Landroid/content/Context;"),
        methodCall(opcode = Opcode.INVOKE_STATIC, returnType = "I"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.RETURN),
    ),
)

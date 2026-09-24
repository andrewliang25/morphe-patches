package app.andrewliang.patches.facebook.resignedtrust

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall

/**
 * The method that gives Facebook's security code the signers of a package.
 *
 * Facebook's trust checks read the signers through this method. It prefers the
 * `SigningInfo` of the package and falls back to the old `signatures` array. It is the only method
 * with no parameters that reads both signer lists of `SigningInfo` and the old array too. Its class
 * and its name are Redex names, so the fingerprint uses only framework references.
 */
internal object PackageSignersFingerprint : Fingerprint(
    parameters = listOf(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/pm/SigningInfo;",
            name = "getApkContentsSigners",
        ),
        methodCall(
            definingClass = "Landroid/content/pm/SigningInfo;",
            name = "getSigningCertificateHistory",
        ),
        fieldAccess(
            definingClass = "Landroid/content/pm/PackageInfo;",
            name = "signatures",
        ),
    ),
)

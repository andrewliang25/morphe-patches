package app.andrewliang.patches.line.hidegift

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * The "+" attach-menu **LINE GIFT** tile is `yi1.h`, an `yi1.p` subclass. Its constructor is the
 * only method that READS the attach-item type constant `wi1.b$b.GIFT` (`sget`); the enum's own
 * `<clinit>` merely writes it (`sput`), and the constructor's parameter list excludes that clinit.
 * From the resolved constructor we take `definingClass` (`Lyi1/h;`) and neuter the class's
 * availability predicate `j(...)`.
 */
internal object GiftAttachButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ln/c;",
        "Lh41/c;",
        "Lxi1/b;",
        "Lxh1/v1\$g;",
        "Lxh1/z0;",
    ),
    filters = listOf(
        fieldAccess(definingClass = "Lwi1/b\$b;", name = "GIFT"),
    ),
)

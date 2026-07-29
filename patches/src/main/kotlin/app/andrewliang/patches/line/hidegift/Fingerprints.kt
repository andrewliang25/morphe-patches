package app.andrewliang.patches.line.hidegift

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * The "+" attach-menu **LINE GIFT** tile is `hg1.h`, an `hg1.r` subclass. Its constructor is the
 * only method that READS the attach-item type constant `fg1.a$b.GIFT` (`sget`); the enum's own
 * `<clinit>` merely writes it (`sput`), and the constructor's parameter list excludes that clinit.
 * From the resolved constructor we take `definingClass` (`Lhg1/h;`) and neuter the class's
 * availability predicate `j(...)`.
 */
internal object GiftAttachButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ln/c;",
        "Lr11/b;",
        "Lgg1/c;",
        "Laf1/d3\$e;",
        "Laf1/c2;",
    ),
    filters = listOf(
        fieldAccess(definingClass = "Lfg1/a\$b;", name = "GIFT"),
    ),
)

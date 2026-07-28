package app.andrewliang.patches.line.hidetransfer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * The "+" attach-menu **Transfer** tile (LINE Pay money transfer) is `hg1.k`, an `hg1.r` subclass.
 * Its constructor is the only method that READS the attach-item type constant `fg1.a$b.PAY`
 * (`sget`); the enum's own `<clinit>` merely writes it (`sput`), and the constructor's parameter
 * list excludes that clinit. From the resolved constructor we take `definingClass` (`Lhg1/k;`) and
 * neuter the class's availability predicate `j(...)`.
 */
internal object TransferAttachButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ln/c;",
        "Lac3/b;",
        "Lmm3/c;",
        "Lv01/c;",
        "Lr11/b;",
        "Lgg1/c;",
        "Lna1/k;",
        "Laf1/d3\$e;",
    ),
    filters = listOf(
        fieldAccess(definingClass = "Lfg1/a\$b;", name = "PAY"),
    ),
)

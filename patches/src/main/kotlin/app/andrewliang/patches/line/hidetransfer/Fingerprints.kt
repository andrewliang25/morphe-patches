package app.andrewliang.patches.line.hidetransfer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * The "+" attach-menu **Transfer** tile (LINE Pay money transfer) is `yi1.j`, an `yi1.p` subclass.
 * Its constructor is the only method that READS the attach-item type constant `wi1.b$b.PAY`
 * (`sget`); the enum's own `<clinit>` merely writes it (`sput`), and the constructor's parameter
 * list excludes that clinit. From the resolved constructor we take `definingClass` (`Lyi1/j;`) and
 * neuter the class's availability predicate `j(...)`.
 */
internal object TransferAttachButtonFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ln/c;",
        "Lwi3/b;",
        "Lnt3/c;",
        "Ll31/c;",
        "Lh41/c;",
        "Lxi1/b;",
        "Lhd1/l;",
        "Lxh1/v1\$g;",
    ),
    filters = listOf(
        fieldAccess(definingClass = "Lwi1/b\$b;", name = "PAY"),
    ),
)

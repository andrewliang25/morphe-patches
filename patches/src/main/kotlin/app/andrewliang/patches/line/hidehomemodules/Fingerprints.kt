package app.andrewliang.patches.line.hidehomemodules

import app.morphe.patcher.Fingerprint

/**
 * `lb2.g$a.<init>(List<y82.j0>, ...)` — the constructor of the Home Compose UI state that holds
 * the rendered module list (stored into field `a`, the first ctor arg). Every feed build path
 * (the feed assemblers, and state copies) funnels through this constructor, so
 * filtering the list argument here covers the whole rendered feed in one place.
 *
 * (The earlier target `u82.c.e` built only the Friends sub-tab list — a single
 * `FriendsSubTabFriendsList` module — not the feed with the ad / recommendation sections;
 * confirmed via on-device logging.)
 */
internal object HomeStateCtorFingerprint : Fingerprint(
    definingClass = "Llb2/g\$a;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/util/List;",
        "Z", "Z", "Z", "Z", "Z",
        "Ljava/lang/String;",
        "Ljava/lang/Long;",
        "Ljava/lang/Long;",
        "I",
        "Z",
    ),
)

package app.andrewliang.patches.line.hideattachmenutools

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * The chat "+" attach menu's server-driven "services" (Poll, Schedule, Reservation, Ladder shuffle,
 * …) are all rendered by the single shared class `hg1.d` (ChatAppButtonType) — the only class
 * built from the server-fetched `r51.a` (ChatAppViewData) list, in `gg1.e`. Each item is shown only
 * when its per-item gate `f(Lgi1/b;Lfg1/a;Lhg1/a$a;)Z` returns true.
 *
 * We match `hg1.d`'s `f()` override directly: it is the only method with that signature that reads
 * `Lr51/a;->f` (the item's availableChatTypes set) — the sibling `f()` overrides in `hg1.a`/`hg1.r`
 * gate on other data, never on `Lr51/a;`. Forcing it false drops every server service at once, with
 * no dependency on any (drifting) server channel id.
 */
internal object AttachMenuServiceGateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Lgi1/b;", "Lfg1/a;", "Lhg1/a\$a;"),
    filters = listOf(
        fieldAccess(definingClass = "Lr51/a;", name = "f"),
    ),
)

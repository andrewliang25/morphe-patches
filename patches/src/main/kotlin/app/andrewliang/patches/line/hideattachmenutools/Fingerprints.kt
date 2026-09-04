package app.andrewliang.patches.line.hideattachmenutools

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess

/**
 * The chat "+" attach menu's server-driven "services" (Poll, Schedule, Reservation, Ladder shuffle,
 * …) are all rendered by the single shared class `yi1.d` (ChatAppButtonType) — the only class
 * built from the server-fetched `k81.a` (ChatAppViewData) list, in `xi1.c`. Each item is shown only
 * when its per-item gate `f(Lxk1/b;Lwi1/b;Lyi1/a$a;)Z` returns true.
 *
 * We match `yi1.d`'s `f()` override directly: it is the only method with that signature that reads
 * `Lk81/a;->f` (the item's availableChatTypes set) — the sibling `f()` overrides in `yi1.a`/`yi1.p`
 * gate on other data, never on `Lk81/a;`. Forcing it false drops every server service at once, with
 * no dependency on any (drifting) server channel id.
 */
internal object AttachMenuServiceGateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Lxk1/b;", "Lwi1/b;", "Lyi1/a\$a;"),
    filters = listOf(
        fieldAccess(definingClass = "Lk81/a;", name = "f"),
    ),
)

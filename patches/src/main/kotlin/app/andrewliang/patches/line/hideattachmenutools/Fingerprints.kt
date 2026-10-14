package app.andrewliang.patches.line.hideattachmenutools

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

/**
 * The chat "+" attach menu's server-driven "services" (Poll, Schedule, Reservation, Ladder shuffle,
 * …) are all rendered by one shared class (ChatAppButtonType), the only class built from the
 * server-fetched ChatAppViewData list. Each item is shown only when its per-item gate `f(...)Z`
 * returns true.
 *
 * That gate starts with a null check (`getClass()`), reads a boolean field, and then tests
 * whether the item's availableChatTypes set contains the chat type:
 * `iget-object <Set>` at once followed by `Set.contains`. The sibling `f()` overrides gate on other
 * data. Forcing this one false drops every server service at once, with no dependency on any
 * (drifting) server channel id.
 */
internal object AttachMenuServiceGateFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        methodCall(definingClass = "Ljava/lang/Object;", name = "getClass"),
        fieldAccess(
            type = "Z",
            opcode = Opcode.IGET_BOOLEAN,
            location = MatchAfterImmediately(),
        ),
        fieldAccess(type = "Ljava/util/Set;", opcode = Opcode.IGET_OBJECT),
        methodCall(
            definingClass = "Ljava/util/Set;",
            name = "contains",
            location = MatchAfterImmediately(),
        ),
    ),
)

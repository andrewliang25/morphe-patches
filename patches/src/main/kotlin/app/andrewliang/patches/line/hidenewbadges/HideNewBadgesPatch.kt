package app.andrewliang.patches.line.hidenewbadges

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val HEADER_BUTTON = "Ljp/naver/line/android/common/view/header/HeaderButton;"

/** `View.GONE`. Written into whichever register the matched `setVisibility` call reads. */
private const val GONE = 0x8

/** Maps a code offset to an instruction index, so a branch can be resolved to its target index. */
private fun MutableMethod.offsetToIndex(): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    var offset = 0
    implementation?.instructions?.forEachIndexed { index, instruction ->
        map[offset] = index
        offset += instruction.codeUnits
    }
    return map
}

/** Every instruction index that some branch in this method jumps to. */
private fun MutableMethod.branchTargets(): Set<Int> {
    val instructions = implementation?.instructions?.toList() ?: return emptySet()
    val offsets = offsetToIndex()
    val byIndex = offsets.entries.associate { (offset, index) -> index to offset }
    return instructions.indices.mapNotNull { index ->
        val instruction = instructions[index] as? OffsetInstruction ?: return@mapNotNull null
        offsets[(byIndex[index] ?: return@mapNotNull null) + instruction.codeOffset]
    }.toSet()
}

/**
 * Forces the `setVisibility` call at [index] to hide its view.
 *
 * Two cases, because a branch can jump straight at the call:
 *
 * 1. The call is not a branch target. Inject `const/16 vD, GONE` in front of it. Only this call
 *    site changes, and the registers the surrounding branches rely on keep their values.
 * 2. The call **is** a branch target. Injecting in front of it would be a silent no-op:
 *    `addInstruction` gives the new instruction its own `MethodLocation` and moves the existing
 *    one, which owns the label, to the next index. The branch then jumps past the injection. This
 *    is the shape LINE uses for a view that it shows or hides:
 *
 *        if-ne vState, vBadgeKind, :hide
 *        move vD, vZero              # show
 *        goto :call
 *        :hide
 *        const/16 vD, 0x8
 *        :call
 *        invoke-virtual {vC, vD}, Landroid/view/View;->setVisibility(I)V
 *
 *    The fix rewrites the producer on the show path: the instruction in front of the `goto` that
 *    jumps to the call. Both paths then load GONE. `replaceInstruction` moves no label and shifts
 *    no index.
 */
private fun MutableMethod.forceGoneAt(index: Int) {
    val instructions = implementation?.instructions?.toList()
        ?: throw PatchException("badges: no implementation for $definingClass")

    val call = instructions.getOrNull(index)
        ?: throw PatchException("badges: no instruction at $index in $definingClass")
    val reference = (call as? ReferenceInstruction)?.reference as? MethodReference
    if (reference?.name != "setVisibility") {
        throw PatchException("badges: instruction $index in $definingClass is not setVisibility")
    }
    // setVisibility is always invoked as 35c. A /range form would carry its registers elsewhere,
    // and silently reading the wrong operand is worse than failing here.
    val visibilityRegister = (call as? FiveRegisterInstruction)?.registerD
        ?: throw PatchException("badges: setVisibility at $index in $definingClass is not 35c")

    if (index !in branchTargets()) {
        addInstructions(index, "const/16 v$visibilityRegister, $GONE")
        return
    }

    val offsets = offsetToIndex()
    val byIndex = offsets.entries.associate { (offset, i) -> i to offset }
    val gotoIndex = instructions.indices.firstOrNull { i ->
        val instruction = instructions[i]
        instruction.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32) &&
            offsets[(byIndex[i] ?: -1) + (instruction as OffsetInstruction).codeOffset] == index
    } ?: throw PatchException(
        "badges: setVisibility at $index in $definingClass is a branch target with no goto",
    )

    // The instruction in front of that goto must be what loads the visible value into vD.
    val producer = instructions.getOrNull(gotoIndex - 1)
    if ((producer as? OneRegisterInstruction)?.registerA != visibilityRegister) {
        throw PatchException(
            "badges: cannot find the show-path producer for setVisibility at $index " +
                "in $definingClass",
        )
    }
    replaceInstruction(gotoIndex - 1, "const/16 v$visibilityRegister, $GONE")
}

/** Every index in this method where a `setVisibility(I)V` is invoked on a view [isBadge] accepts. */
private fun MutableMethod.visibilityCallsOn(isBadge: (FieldReference) -> Boolean): List<Int> {
    val instructions = implementation?.instructions?.toList() ?: return emptyList()

    // Receiver register -> the field it was last loaded from, tracked as we walk forward.
    val loadedFrom = mutableMapOf<Int, String>()
    val hits = mutableListOf<Int>()

    instructions.forEachIndexed { index, instruction ->
        if (instruction.opcode == Opcode.IGET_OBJECT) {
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            val destination = (instruction as TwoRegisterInstruction).registerA
            if (field != null && isBadge(field)) {
                loadedFrom[destination] = field.name
            } else {
                loadedFrom.remove(destination)
            }
            return@forEachIndexed
        }

        // Any other write to a tracked register makes the mapping stale. move-object,
        // move-result-object, sget-object, new-instance and check-cast all reuse registers, and a
        // stale entry would hide whatever view the register holds later - a whole row, not a badge.
        (instruction as? OneRegisterInstruction)?.let { loadedFrom.remove(it.registerA) }

        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        if (reference?.name != "setVisibility") return@forEachIndexed
        val receiver = (instruction as? FiveRegisterInstruction)?.registerC ?: return@forEachIndexed
        if (loadedFrom[receiver] != null) hits += index
    }
    return hits
}

@Suppress("unused")
val hideNewBadgesPatch = bytecodePatch(
    name = "Hide new item badges",
    description = "Hides the green dots and N badges that mark new items, on header buttons, " +
        "tabs, menus, lists and settings rows. Unread message counts do not change.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // ---- Core sites. These resolve strictly: they are the badges the patch exists for, and
        // every anchor below is a non-obfuscated class, method or field, so none of them drifts.

        // 1. Every header "new item" dot, in both theme colours: 9 call statements across 7
        //    feature areas funnel into HeaderButton.c(I, Z). Keep the existing b() reset call so a
        //    recycled button cannot keep a stale badge, then return before either dot is shown.
        //    `.locals 0`, so p0 is the only register needed.
        // b() hides every badge view in the button's own set. Its name is R8-assigned, so make
        // sure it exists at patch time: dexlib assembles a reference to a missing method without
        // complaint, and the failure would then be a runtime NoSuchMethodError on every badge
        // update instead of a failed patch.
        val headerButton = mutableClassDefBy(HEADER_BUTTON)
        if (headerButton.methods.none {
                it.name == "b" && it.returnType == "V" && it.parameterTypes.isEmpty()
            }
        ) {
            throw PatchException("badges: $HEADER_BUTTON->b()V not found")
        }
        HeaderDotFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, $HEADER_BUTTON->b()V
                return-void
            """,
        )

        // 2. The circled "N" on the chat-room header. Rewrite only the visibility argument of the
        //    setVisibility that follows the read of HeaderButton.g — the numeric-count branch is
        //    in this same method and must keep working.
        val nBadgeFieldReadIndex = HeaderNBadgeFingerprint.instructionMatches[1].index
        HeaderNBadgeFingerprint.method.forceGoneAt(nBadgeFieldReadIndex + 1)

        // 3-4. Settings rows. Forcing the parameter false makes each setter take its own hide
        //      branch, so no instruction shape is assumed beyond the signature.
        SettingsTextItemNewBadgeFingerprint.method.addInstructions(0, "const/4 p1, 0x0")
        SettingsSwitchItemNewMarkFingerprint.method.addInstructions(0, "const/4 p1, 0x0")

        // ---- Best-effort sites. Each anchors on a resource id or an R8-stable enum constant, but
        // the enclosing classes are obfuscated. A drifted anchor skips its own surface instead of
        // failing the whole patch, so the core badges above always ship.

        // 5-6. Chat slide-out menu rows: the green dot and the circled-N feature badge, plus the
        //      Album row's own green dot. The badge views are found by the resource id their
        //      constructor binds, so no obfuscated field letter is hardcoded.
        listOf(
            ChatMenuRowHolderFingerprint to listOf(0x7f0b18e9, 0x7f0b18f0),
            ChatMenuAlbumRowHolderFingerprint to listOf(0x7f0b18e8),
        ).forEach { (fingerprint, badgeIds) ->
            val constructor = fingerprint.methodOrNull ?: return@forEach
            val matches = fingerprint.instructionMatchesOrNull ?: return@forEach

            // Each matched literal is followed by findViewById and an iput-object into the field
            // that holds that badge view. Collect those field names.
            val constructorInstructions = constructor.implementation?.instructions?.toList()
                ?: return@forEach
            val badgeFields: Set<FieldReference> = matches.mapNotNull { match ->
                (match.index until constructorInstructions.size)
                    .firstOrNull { constructorInstructions[it].opcode == Opcode.IPUT_OBJECT }
                    ?.let {
                        (constructorInstructions[it] as ReferenceInstruction).reference
                            as? FieldReference
                    }
            }.toSet()
            if (badgeFields.size != badgeIds.size) return@forEach

            val holder = mutableClassDefBy(constructor.definingClass)
            holder.methods.forEach { method ->
                // Walk backwards so injecting does not shift the indices still to be patched.
                method.visibilityCallsOn { field ->
                    badgeFields.any {
                        field.definingClass == it.definingClass && field.name == it.name
                    }
                }
                    .reversed().forEach(method::forceGoneAt)
            }
        }

        // 7. Home service tiles, the service list and the shortcuts row — ten call sites read one
        //    `isVisible` accessor on a shared badge enum. The enum is located by its R8-stable
        //    constant names, then the accessor is selected inside it by shape.
        ServiceBadgeEnumFingerprint.methodOrNull?.let { clinit ->
            val badgeEnum = mutableClassDefBy(clinit.definingClass)
            badgeEnum.methods.firstOrNull { method ->
                method.returnType == "Z" &&
                    method.parameterTypes.isEmpty() &&
                    method.implementation?.instructions?.any {
                        ((it as? ReferenceInstruction)?.reference as? FieldReference)
                            ?.name == "isVisible"
                    } == true
            }?.addInstructions(
                0,
                """
                    const/4 p0, 0x0
                    return p0
                """,
            )
        }

        // 8. Friends and contacts rows: one shared binder draws the green dot for every friend,
        //    group, directory, Square and birthday row.
        FriendRowGreenDotFingerprint.methodOrNull?.let { method ->
            val setVisibilityIndex = FriendRowGreenDotFingerprint
                .instructionMatchesOrNull?.getOrNull(1)?.index ?: return@let
            method.forceGoneAt(setVisibilityIndex)
        }

        // 9. The dot on the chat "+" attach button.
        AttachButtonDotFingerprint.methodOrNull?.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )

        // 10. Chats-tab header green dots: empty the set of icons that get a dot by removing each
        //     `sget-object <constant>` and the `add` that follows it. Highest index first, so the
        //     earlier pairs keep their positions.
        ChatsTabGreenDotSetFingerprint.methodOrNull?.let { method ->
            // Matches alternate constant, add. Take the constant indices only, and remove each
            // pair from the highest index down so the earlier pairs keep their positions.
            ChatsTabGreenDotSetFingerprint.instructionMatchesOrNull
                ?.filterIndexed { position, _ -> position % 2 == 0 }
                ?.map { it.index }
                ?.sortedDescending()
                ?.forEach { method.removeInstructions(it, 2) }
        }

        // 11. The bottom-navigation "new" badge. Its observer is one of five near-identical
        //     one-line lambdas on this holder — the others drive the unread counts, the count text
        //     and the live badge — so it is found by data flow rather than by shape: take the field
        //     the holder binds `bnb_button_badge_new` into, then patch the one class the holder
        //     instantiates that reads that exact field. An earlier shape-only fingerprint matched
        //     an unrelated class in another feature, which is why this is derived, not guessed.
        BottomNavBadgeHolderFingerprint.methodOrNull?.let { constructor ->
            val literalIndex = BottomNavBadgeHolderFingerprint
                .instructionMatchesOrNull?.firstOrNull()?.index ?: return@let
            val instructions = constructor.implementation?.instructions?.toList() ?: return@let

            val badgeField = (literalIndex until instructions.size)
                .firstOrNull { instructions[it].opcode == Opcode.IPUT_OBJECT }
                ?.let {
                    ((instructions[it] as ReferenceInstruction).reference as? FieldReference)
                } ?: return@let

            val holder = mutableClassDefBy(constructor.definingClass)
            val observerTypes = holder.methods
                .flatMap { it.implementation?.instructions?.toList().orEmpty() }
                .filter { it.opcode == Opcode.NEW_INSTANCE }
                .mapNotNull { ((it as ReferenceInstruction).reference as? TypeReference)?.type }
                .distinct()

            observerTypes.forEach { type ->
                val observer = runCatching { mutableClassDefBy(type) }.getOrNull() ?: return@forEach
                observer.methods.forEach { method ->
                    method.visibilityCallsOn { field ->
                        field.definingClass == badgeField.definingClass &&
                            field.name == badgeField.name
                    }.reversed().forEach(method::forceGoneAt)
                }
            }
        }
    }
}

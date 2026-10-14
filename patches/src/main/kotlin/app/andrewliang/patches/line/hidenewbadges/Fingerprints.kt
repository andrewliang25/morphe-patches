package app.andrewliang.patches.line.hidenewbadges

import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

private const val HEADER_BUTTON = "Ljp/naver/line/android/common/view/header/HeaderButton;"
private const val HEADER_STATE_N = "Ljp/naver/line/android/common/view/header/a\$c;"

/**
 * `HeaderButton.c(I, Z)V` — the **only** method in the whole APK that makes a header "new item"
 * dot visible. The `Z` picks which of the two dot views to show, and it is a *theme* predicate
 * (`o96.m.k()` in 26.14.0 = "custom theme applied || dark"), not a semantic distinction: green
 * under a dark/custom theme, red otherwise. So one edit here removes the dot in both colours.
 *
 * Verified exhaustive: the dot views `header_new_badge_img` / `header_new_badge_img_green` are resolved to a view and
 * given a visibility nowhere else. Nine call statements across seven feature areas funnel here —
 * chat room (two buttons plus the hamburger), Home notification, Home settings, Album Note,
 * Official Account list and Nearby (two).
 *
 * The numeric count badge is untouched: it is a different field, written directly by the count
 * branch of the header badge renderer (`ag1.t1.i` in 26.14.0) without ever calling this method.
 *
 * Non-obfuscated class and method, so this anchor does not drift.
 */
internal object HeaderDotFingerprint : Fingerprint(
    definingClass = HEADER_BUTTON,
    name = "c",
    returnType = "V",
    parameters = listOf("I", "Z"),
)

/**
 * The chat-room header badge renderer (`ag1.t1.i` in 26.14.0), matched on its **"N" badge branch**.
 *
 * The renderer switches over a sealed state with four cases — numeric count, dot, "N", and none —
 * and only the "N" case reads `HeaderButton.g` (the `header_n_badge` view). Pairing the read of the
 * non-obfuscated singleton `header/a$c` with the read of the non-obfuscated field
 * `HeaderButton.g` lands on that method and nowhere else: `HeaderButton`'s own reads of `g` sit in
 * a colour-filter helper and its constructor, neither of which mentions `a$c`.
 *
 * Both anchors are non-obfuscated, so this does not drift.
 *
 * The patch rewrites only the visibility argument of the following `setVisibility` call. It must
 * NOT neuter the whole method — the numeric-count branch lives in the same method.
 */
internal object HeaderNBadgeFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = HEADER_STATE_N, name = "a"),
        fieldAccess(definingClass = HEADER_BUTTON, name = "g"),
    ),
)

/**
 * `LineUserSettingTextItemView.setNewBadgeVisible(Z)V` — the green "new" mark on a settings text
 * row. Forcing the parameter false makes the existing body take its own hide branch, so no
 * instruction shape is assumed beyond the signature. Non-obfuscated.
 */
internal object SettingsTextItemNewBadgeFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/settings/base/itemview/LineUserSettingTextItemView;",
    name = "setNewBadgeVisible",
    returnType = "V",
    parameters = listOf("Z"),
)

/**
 * `LineUserSettingSwitchItemView.setIsNewMarkVisible(Z)V` — the same mark on a settings switch
 * row. Same technique as [SettingsTextItemNewBadgeFingerprint]. Non-obfuscated.
 */
internal object SettingsSwitchItemNewMarkFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/settings/base/itemview/LineUserSettingSwitchItemView;",
    name = "setIsNewMarkVisible",
    returnType = "V",
    parameters = listOf("Z"),
)

/**
 * The slide-out chat-menu row view holder, matched on its **constructor** because that is where the
 * badge views are bound by resource id: `new_badge` (green dot) and
 * `new_feature_badge` (the circled "N"). Only this holder inflates both.
 *
 * Matching the constructor rather than the bind method lets the patch read the *field names* the
 * two views are stored into, instead of hardcoding obfuscated field letters. Resource ids drift
 * between LINE versions and must be re-resolved by name through `res/values/public.xml`.
 */
internal object ChatMenuRowHolderFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        literal({ resourceId("id", "new_badge") }),
        literal({ resourceId("id", "new_feature_badge") }),
    ),
)

/**
 * The chat-menu **Album row** holder, matched on its constructor's bind of `new_album_badge`
 * — a green dot on its own layout, so it needs its own site. Same field-name
 * discovery as [ChatMenuRowHolderFingerprint].
 */
internal object ChatMenuAlbumRowHolderFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
    filters = listOf(
        literal({ resourceId("id", "new_album_badge") }),
    ),
)

/**
 * The Home/service badge enum's `<clinit>`, matched on its **constant names**, which R8 keeps.
 * The enum carries a non-obfuscated `isVisible` field that ten call sites consult — Home service
 * tiles, the service list sections, the top-services carousel and the shortcuts row — so forcing
 * its accessor false clears all ten at once.
 *
 * `isVisible` alone is not unique (five classes have such a field), which is why the enum is
 * located by its constants first and the accessor is then selected inside that class.
 *
 * Accepted trade: the enum also carries `EXPIRED`, arguably a warning rather than a "new item".
 * Hiding it is the price of one edit instead of ten.
 */
internal object ServiceBadgeEnumFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    filters = listOf(
        fieldAccess(name = "NEW"),
        fieldAccess(name = "UPDATE"),
        fieldAccess(name = "EVENT"),
        fieldAccess(name = "EXPIRED"),
    ),
)

/**
 * The shared friends/contacts row binder, matched on the R8-stable enum constant
 * `GREEN_DOT_BADGE`. One binder serves every friend, group, directory and Square row plus the
 * birthday row, so a single edit clears the green dot from all of them.
 */
internal object FriendRowGreenDotFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(name = "GREEN_DOT_BADGE"),
        methodCall(definingClass = "Landroid/view/View;", name = "setVisibility"),
    ),
)

/**
 * The chat "+" attach-button dot gate. It asks for the badge of the `PLUS_MENU` slot and returns
 * true when that badge is `DOT`. The patch forces it false, which clears the dot on the input bar.
 * Many enums have a `DOT` constant, so the `PLUS_MENU` read before it pins this method.
 */
internal object AttachButtonDotFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(name = "PLUS_MENU", opcode = Opcode.SGET_OBJECT),
        fieldAccess(name = "DOT", opcode = Opcode.SGET_OBJECT),
    ),
)

/**
 * The Chats-tab header green-dot set builder. It adds one header-icon enum constant per feature
 * that has something new. The renderer then shows a green dot for each icon in the set.
 * Emptying the set removes every Chats-tab header green dot.
 *
 * The filters alternate constant and the `add` at once after it, and they take only `sget-object`
 * reads. This is what keeps the enum `<clinit>` of the icon enum out of the match. That method
 * writes the same constants with `sput-object`. A blind two-instruction removal there erases live
 * code and leaves null enum constants.
 *
 * The patch removes the pairs the same way `chatheaderbuttons` removes header buttons.
 */
internal object ChatsTabGreenDotSetFingerprint : Fingerprint(
    name = "invokeSuspend",
    filters = listOf(
        fieldAccess(name = "ALBUM", opcode = Opcode.SGET_OBJECT),
        methodCall(name = "add", location = MatchAfterImmediately()),
        fieldAccess(name = "OPEN_CHAT", opcode = Opcode.SGET_OBJECT),
        methodCall(name = "add", location = MatchAfterImmediately()),
        fieldAccess(name = "AI_FRIEND", opcode = Opcode.SGET_OBJECT),
        methodCall(name = "add", location = MatchAfterImmediately()),
    ),
)

/**
 * The bottom-navigation button view holder, matched on its **constructor's bind of
 * `bnb_button_badge_new`** — the "new" badge ImageView.
 *
 * Matching the binder rather than the observer lambda is deliberate. The five observers that drive
 * this button's badges (two unread counts, the count text, the live badge and the "new" badge) are
 * near-identical one-line lambdas of the form "unwrap an Integer visibility, apply it to a view",
 * and an earlier shape-based fingerprint for them matched an unrelated class in another feature
 * entirely. Starting from the resource id gives the holder class and the exact field, and the
 * observer is then found as the one class — instantiated by this holder — that reads that field.
 *
 * Resource ids drift between LINE versions and must be re-resolved by name through
 * `res/values/public.xml`.
 */
internal object BottomNavBadgeHolderFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("Landroid/view/ViewGroup;", "L", "L"),
    filters = listOf(
        literal({ resourceId("id", "bnb_button_badge_new") }),
    ),
)

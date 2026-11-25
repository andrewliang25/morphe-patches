# LINE patch notes

Reference notes for authoring LINE (`jp.naver.line.android`) patches. The bundle supports the
versions in [build-and-versions.md](build-and-versions.md) (see
`app/andrewliang/patches/shared/Constants.kt`).

> Most obfuscated descriptors in these files are from 26.14.0. A note gives the version when it is
> another one. Device-confirmation notes record the version each behaviour was *tested* on.

> ⚠️ **Obfuscation drift.** Names like `yi1.d`, `q11.q`, `z00.n`, `kh1.x0$c`, `wi1.b$b`, `k81.a`
> are R8-obfuscated and **change between LINE versions**. The concepts and anchoring strategies are
> durable; re-confirm every descriptor against the decompiled smali on a version bump. Prefer
> anchors obfuscation cannot touch: Kotlin **enum-constant names** (`CALENDAR`, `GIFT`), **string
> literals**, **resource ids**.

| File | What it covers |
|---|---|
| [build-and-versions.md](build-and-versions.md) | The supported LINE versions, the anchors that survive a version bump, and the offline build and apply recipe. |
| [chat-and-tabs.md](chat-and-tabs.md) | The chat attach menu, Calendar and Events, the bottom tabs, the Home modules, unsend, message tips and group readers. |
| [ads-ai-analytics.md](ads-ai-analytics.md) | Ads, AI entry points, analytics and traffic interception. |
| [pay.md](pay.md) | How an external pay URL enters LINE, and the redirect patch. |
| [resigning.md](resigning.md) | What breaks when LINE is signed with another certificate, and which patches fix or work around it. |
| [settings-fonts-themes.md](settings-fonts-themes.md) | The patch Settings row, custom fonts and themes on a sub device. |
| [calls.md](calls.md) | Call recording and call ringtones. |
| [limits-and-dead-ends.md](limits-and-dead-ends.md) | Which limits LINE decides on the phone, the photo pipeline, and the areas that are not patchable. |
| [premium.md](premium.md) | LYP Premium gating and the Disable LINE Premium patch. |

---

## Open items (rechecked on 26.18.2, 2026-11-24)

These are features that a patch can need later. Each item gives what the 26.18.2 decompile shows.
To find out whether LINE uses a string, get its id by name from `res/values/public.xml`. Then
search `smali*/` for the id. A hit means that LINE uses the string. Ids change in each version, but
names do not.

- **Scroll preview** (chat list). The strings `chat_preview_desc_scrollpreview`,
  `chat_preview_banner_scrollpreview*` and `chat_chatlist_tooltip_scrollpreview*` are now used, and
  LINE reads the server key `function.premium.common.scroll_preview`. The feature has four tiers:
  LINE Labs, LYP Lite, LYP Standard and no LYP. It is not known whether *[Premium] Disable LINE
  Premium* hides its upsell. The device test on 26.15.1 found no scroll preview entry in Settings ▸
  Chats.
- **LINE Calendar app promotion.** The strings `line_calendar_apppromotion_link_morefeatures` and
  `line_calendar_apppromotion_popupdesc_eventlocation` are now used (`gx0.d`, `gx0.s`, `uy0.d`). If
  the promotion shows in a chat, it is a sixth Calendar surface for *[Chat] Hide calendar buttons*.
  Not seen on the device.
- **Manual read receipts** (`MANUAL_READ_RECEIPT`). This was a staged enum constant in 26.15.1. In
  26.18.2 it is an LYP Premium setting with its own screen
  (`com/linecorp/line/settings/impl/lyppremium/manualreadreceipt`). Compare it with *[Chat] Keep
  chats unread* before you change that patch.
- **Labs premium intro.** `LineUserSettingLabPremiumIntroComposeView` does not read the premium
  facade, so it is not known whether *[Premium] Disable LINE Premium* hides it. Look for an LYP
  intro on the LINE Labs page of a patched build.
- **Global Home module types.** `GlobalHomeRecommendedSticker`, `GlobalHomeLoungeHoroscope` and
  `GcsGlobalHomeActivityHybridContentCard` are in no blocklist. They are on the `GLOBALHOME` tab,
  which shows only in some regions. The first two are candidates for *[Home] Hide Home modules*,
  because it already hides the `HOME` version of the sticker module. Keep the third, because its
  `HOME` version is kept. Make sure that they show on a device before you add them.
- **Badges that *[General] Hide new item badges* does not reach.** The green dots on Edit, Unsend
  and Keep Memo in the message long-press menu. With the tabs shown, the red dots on the TODAY and
  Wallet tabs, and the green dots on Wallet service tiles. The LINE Labs "N" is reached: its rows
  read the suspend predicate `hasNewLabFeature`, which the patch finds by its Kotlin debug
  annotation `LineUserMainSettingsCategory$hasNewLabFeature$2` and makes return false.

Closed since 26.14.0: the scheduled message upsell strings and two upsell dialogs
(`ScheduledMessageLypSubscriptionPopupDialogFragment`, `LypFeatureOnboardingPopupDialogFragment`)
are not in 26.18.2.

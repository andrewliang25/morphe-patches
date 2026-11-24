# LINE settings, fonts and themes

The patch Settings row, custom fonts and themes on a sub device. See [README.md](README.md) for the other LINE docs.

## Settings screen & the "[General] Andrew's Patch Setting" patch

The patch adds a row, "Andrew's Patch Setting", to LINE's main Settings list, right below "Profile".
The row opens a screen with runtime switches for 16 patches, then credits and licenses. Who it
helps: a user of the prebuilt APK from `patched-apps`, who otherwise cannot turn off one patch
without patching the APK again. Anchors are 26.14.0.

### How LINE builds the main Settings list

- `com.linecorp.line.settings.main.LineUserMainSettingsFragment` (name kept in `@Metadata`) extends
  `…settings.base.LineUserSettingItemListFragment`. It is a RecyclerView list, not Compose.
- Its rows come from the singleton `ka5/o2` (extends `m55/q1`). `ka5/o2.<clinit>` builds about 57
  rows once (`.locals 86`) and stores them in the static field `e`. `ka5/o2.a()` only returns `e`.
  **Hook `a()`, not `<clinit>`.**
- A plain row is `m55/v` (extends `m55/z`). The row key is `m55/z->a:String`. The title is an
  **`int` string id**: `m55/v.c(Context, …)` calls `context.getString(y)`. Thus the title needs a
  string resource. The patch adds `andrew_patch_settings` to `res/values/strings.xml`, and the
  extension finds its id with `getIdentifier`, because ids are assigned after the bytecode patches.
- **A row key is built at run time.** `ka5/a.a()` returns `"line-main-settings." + settingItemName`,
  so the key of "About LINE" is `"line-main-settings.about-line"`, not `"about-line"`. The first
  device build compared with the bare name, matched nothing, and added no row. The patch now gets
  each key the way LINE does: the enum constant, then that accessor.
- The template is the "About LINE" row: key `ka5/a.AboutLine.a()`, icon
  `setting_ic_version`, title `settings_about`, event `b88/e.MORETAB_SETTINGS_ABOUTLINE`. It is built
  with the 11-argument convenience constructor
  `(String key, Integer icon, int title, aj8.p, aj8.p, b88.e, aj8.l, aj8.l, m55.u0, aj8.p, int mask)`.
  The last argument is a Kotlin default mask. "About LINE" uses `0x20d0`. Bit `0x100` makes the event
  null, which other rows also use (`0x1d0`, `0x23d0`).
- `aj8.l` / `aj8.p` / `aj8.q` are LINE's renamed Kotlin `Function1` / `Function2` / `Function3`. The
  method names stay `invoke` / `invoke` / `j`.

| Argument | Field | What "About LINE" passes |
|---|---|---|
| 4th, `aj8.p` | `m55/v->z` | `ka5/o2$p`: a suspend subtitle provider. It returns the version only in a debug build (`d28.b.i`), so null in release. |
| 5th, `aj8.p` | `m55/v->A` | a shared provider |
| 7th, `aj8.l` | `m55/z->f` | `ka5/k2`: `Context → y65.d` |
| 8th, `aj8.l` | `m55/z->h` | `ka5/l2`: **the tap handler of the main list**. The view holder calls it with the list fragment (`r55/h2.onClick`). |
| 9th, `m55.u0` | `m55/z->i` | `new m55/u0$b(ka5/m2)`: the destination for settings search and navigation. `ka5/m2.j(Context, List, LineUserSettingsNavigationFragment)` opens `i55/x.ABOUT_SETTINGS`. |
| 10th, `aj8.p` | `m55/z->j` | a shared provider |

### How the patch adds the row

1. `SettingsRowsFingerprint` finds `ka5/o2.<clinit>` by its two kept enum names: `AboutLine` and
   `MORETAB_SETTINGS_ABOUTLINE`, both as `sget-object`. That leaves out the two enum initializers,
   which `sput` them.
2. From the "About LINE" construction, the patch reads the row class, the destination class and the
   function types. It reads the default mask from the literal in the last register of the call.
   The row constructor is the first `invoke-direct/range` `<init>` after `AboutLine` whose
   parameters include the event type and end with `I`. The patch finds each argument by its role,
   not by its position, because 26.15.1 added an argument:
   - The destination argument has the type of the destination base class. The click function is
     the argument before it.
   - The event argument has the event type. The patch passes null.
   - The mask is the last argument.
   - Each other argument is copied from the field where "About LINE" keeps it.

   The new row puts argument N in `v(5 + N)`. In 26.17.1 the constructor has 13 arguments, so
   argument 11 is in v16. `iget` and `const/4` take only v0 to v15. The patch reads such an argument
   into v4 and moves it up with `move-object/from16`. It creates the row (`new-instance v4`) after
   the arguments for this reason.
3. It finds the fields by following each constructor argument through register moves and through
   the call to the base constructor (`fieldOfArgument`). So no obfuscated field name is hard-coded.
   If the field has another type than the argument, the patch passes null. Such a field holds the
   default that the constructor builds when the mask marks the argument unused. "About LINE" leaves
   that argument unset. In 26.15.1 the new `vd5.o$e` argument is this case: its field holds a
   `zb5.s` function. A `check-cast` there passed the verifier and threw `ClassCastException` when
   Settings opened.
4. It adds a new static method `ka5/o2.andrewRows(List)List`. The method finds the "About LINE"
   row and builds a new `m55/v` with that row's providers, a null event, our key, title and icon.
   A second pass finds the "Profile" row (`ka5/a.Profile`, row class `m55/r0`, so the pass compares
   keys on the base class `m55/z`). `LineSettings.insertRow` puts the new row below it, or first if
   "Profile" is missing. If the title resource is missing, the method returns the list unchanged.
   The loops are in the new method, not in an existing one.
5. `a()` gets a branchless `invoke-static` + `move-result-object` before its `return-object`.

**The tap handler must be a real `Serializable` class.** `m55/u0$b` is `Parcelable`, and its
`writeToParcel` casts its `aj8.q` to `java.io.Serializable`. A `java.lang.reflect.Proxy` is
fragile there. So the extension class `LineSettingsClick` implements `Serializable` and declares
`invoke(Object)` and `invoke(Object, Object, Object)`. That `Function3` method is `j` in 26.14.0,
`g` in 26.15.1 and `k` in 26.16.0. At patch time, the patch adds the resolved `aj8.l` and `aj8.q`
to its `interfaces` list and adds a method with the resolved `Function3` name that forwards to
`invoke(Object, Object, Object)`.

**The tap handler gets a Fragment, not a Context.** LINE renames the androidx classes
(`androidx/fragment/app/v` is `Fragment`) but keeps their method names, so
`fragment.getClass().getMethod("getContext")` works.

**The screen is a full-screen framework `Dialog` drawn with LINE's own resources.** It has no
manifest entry and no fragment. It inflates LINE's settings layouts and uses LINE's styles, found by
resource name, which R8 keeps.

**Colors come from the page under the dialog, not from resources.** LINE has two color layers, and
resources are only the lower one:

- Resource colors such as `@color/primaryBackground` follow the night bit of the context. LINE sets
  that bit from "Use Black theme when in dark mode" (Settings ▸ Themes), not from the system.
- LINE's theme engine then paints the chosen theme (Black, or a shop theme) over its own pages at
  run time. `settings_root` then has a `StateListDrawable` background, not the resource color.

Device-confirmed: the test had that switch off, the system in dark mode, and Black picked by hand.
A probe read `viewNight=16` (light) for the settings fragment and `appNight=32` for the
application. `primaryBackground` resolved to white. LINE's pages were black, but a page drawn from
resources was white.

Thus `LineSettingsScreen.Look` copies these values from the Settings page under the dialog:

- The background of `settings_root`.
- The text colors of a `setting_title` and a `setting_description`. A group header gives the
  description color when no description shows.
- The text colors of a `LineUserSettingGroupHeaderItemView` and of the header title.

The back arrow takes the color of the header title. The engine colors the arrow on its view, so a
copy of the drawable keeps the resource color. If a view is not found, its resource color stays.
The device test covered the switch on and off, with Black picked by hand.

**The page under the dialog is not always the Settings list.** LINE's settings search also finds
the row, and a tap there calls the `aj8.q` handler over the search results page. That page has no
`settings_root`, `setting_title` or group header, so the first build fell back to resource colors
and was white under Black. Each value now also tries the views of the search page: `item_title`
(or `empty_title_text`), `item_path` (or `empty_description_text`), and the search box
`input_text` for the header title. The background is the first opaque one under
`android.R.id.content`. Device-confirmed: both entry points are black under Black, and the white
and other themes also match.

**The press effect needs the same treatment.** LINE's row background
`line_user_settings_button_bg_semantic` has a light pressed state, which the engine repaints only on
LINE's own rows. On these rows it flashed white under Black. Each row now gets a `RippleDrawable` in
the copied title color at about 12% opacity. Text rows take their click on the outer view, so their
`setting_item_container` uses `setDuplicateParentStateEnabled(true)` to show the press.

| Part | LINE resource |
|---|---|
| Background | `@color/primaryBackground` (the `line_user_settings_fragment` background) |
| Header | `header_ic_back_semantic` (back arrow, closes the dialog) + `text_header_title_new_design`, height `@dimen/header_height` |
| Section title | `line_user_settings_group_header_item_text_semantic`, with the padding `LineUserSettingGroupHeaderItemView` sets (`…item_container_padding_horizontal`, `…item_header_small_padding_top`, `…item_header_padding_bottom`) |
| Switch row | layout `line_user_settings_switch_item_view` (`setting_title`, `setting_description`, `setting_switch` `CheckedTextView`, `setting_item_container`). Its `setting_divider` is hidden, as on LINE's own switch pages. |
| Tappable row | layout `line_user_settings_text_item_view`. `setting_inlined_value` must stay visible (empty): the arrow is attached to it, and hiding it pulls the arrow next to the title. |
| Note | layout `line_user_settings_description_item` |
| Row icon | `andrew_ic_settings`, added by the patch: the path of LINE's header gear `navi_top_setting` (38dp, black), moved into a 22dp vector and colored `@color/octonaryAltNeutralFill`, like the `setting_ic_*` row icons. LINE has no gear among its row icons. |

Device findings (Xiaomi, Android 16):

- The dialog draws behind the system bars, so the root view pads itself with the
  `WindowInsets.Type.systemBars()` insets, and sets light or dark bar icons from the background.
- The first version used `Theme_DeviceDefault_DayNight` colors. On this ROM `colorAccent` is white
  and the default text color is gray, so it looked unlike LINE. LINE's own layouts fix that.
- **Restart LINE must not end the process.** `startActivity(makeRestartActivityTask(...))` followed
  by `Runtime.exit(0)` closed LINE and started nothing. A new task alone is enough, because LINE
  rebuilds its tab list in `MainActivity.onCreate` (see "Main bottom-navigation tabs" in [chat-and-tabs.md](chat-and-tabs.md)).
- A switch is written with `commit()`, not `apply()`, because a user can close LINE right after a
  change.

### The switches

| Switch | Patch | Gate |
|---|---|---|
| Keep chats unread | `keepunread` | entry of `na3/e.b(String,String)`, `v0` |
| Keep unsent messages | `keepunsent` | before the guard's `if-eqz` in `la8/x.invoke`: `if-nez vG` (already a tombstone) and the switch both branch to it |
| Open links in external browser | `externalbrowser` | entry of `OpenUriActivity$b.a`, `v0` |
| Disable VOOM | `disablevoom` | entry of `bh8/n0.a`, and after `super.onCreate` in `LineVoomActivity`, `v0` at both. A probe showed that the handler gate runs and reads the switch on `line://home/discover`. Stock LINE refuses that link too, so the "off" result was not seen on the screen. |
| Hide LINE TODAY / Shopping / VOOM / Wallet tab | the four tab patches | each `sget`+`add` pair in `b68/g.a()`, `v2` |
| Hide AI Friends button | `chatheaderbuttons` | `hideAiFriends(Z)Z` before the `return` of the flag accessor `g45/c.t()` (`.locals 0`, so no free register) |
| Hide community button | `chatheaderbuttons` | the `OPEN_CHAT` `sget`+`add` pair in `qz1/f.<init>`, the register of the `sget` (`v1`) |
| Hide calendar buttons | `hidecalendar` | The `CALENDAR` pair in `qz1/f.<init>` (`v1`). Entry of `yi1/b.j` (`p0`). Both `CALENDAR_BUTTON` pairs in `ag1/e0.a` (`p6`). `hideCalendar(Z)Z` on `p1` at entry of `z00/l.<init>`. Entry of `kh1/x0$c.a` (`v0`). Entry of the added `filterCalendarModules` (`v0`). |
| Hide Events button | `hideevents` | `hideEvents(Z)Z` after the `iget-boolean v6, o4.l` in `ChatHistoryMenuFragment` |
| Hide LINE GIFT / Transfer button | `hidegift`, `hidetransfer` | entry of `yi1/h.j` and `yi1/j.j`, `p0` |
| Hide attach menu extra tools | `hideattachmenutools` | entry of `yi1/d.f`, `p2` |
| Hide tips under messages | `hidechattips` | `hideChatTips(Z)Z` after the `instance-of v3, sm1/g$b` in `rm1/u.invokeSuspend` |
| Hide LINE AI image editing | `hidelineaiimageedit` | `hideLineAiImageEdit(List)List` before the `return-object` of the entry point list getter (`ya5/d.e()` in 26.16.0, `.locals 0`) |
| Hide Agent i | `hideagenti` | `hideAgentI(Z)Z` (range) after the `iget-boolean` of the Home header lambda. `hideAgentI(Z)Z` before the `return` of the search bar flag accessor. Entry of both hidden-until getters, the result register. `hideAgentI(Z)Z` after the `check-cast` of the + menu switch view in `sm1/f1.invoke()`. Entry of `WalletV3GrandDesignHeaderView.o()`, `v0`. `hideAgentI(List,String)List` in the added `andrewHideAgentIRows`, called by the Settings list getter |
| Custom font | `customfont` | `customFont(Z)Z` after the sync `move v4, v12` in the Font list loader. The extension methods read `customFont()` themselves. Its "Close LINE" row ends the process, because a new task does not change LINE's Compose screens. |

- A gate is one of two shapes. A branch gate is `invoke-static LineSettings-><x>()Z` +
  `move-result` + a branch to an `ExternalLabel` on an original instruction
  (`returnFalseIfLineSetting`, `skipIfLineSetting`). It needs a register that is dead at the gate.
  A value gate is `invoke-static {vN}, LineSettings-><x>(Z)Z` + `move-result vN` on a "shown"
  boolean that LINE already holds (`hideIfLineSetting`). It needs no free register and no branch,
  so it fits `.locals 0` methods, but `vN` must be v0 to v15.
- An entry gate in a `.locals 0` method uses a parameter register that the original code writes
  before it reads, such as `p0` of `yi1/h.j`. With the switch off, that register holds the switch
  value until the original code overwrites it.
- Device test of the chat switches (26.14.0, Xiaomi, Android 16): all switches off, then on again.
  The chat toolbar, the + menu, the chat menu, the long-press menu and the tips change when you
  open a chat again, with no restart. The Chats-tab header (calendar, community) changes only
  after LINE restarts, because `qz1/f.<init>` builds it once. The test account does not get AI
  Friends (see "AI entry points" in [ads-ai-analytics.md](ads-ai-analytics.md)), so that switch was seen only to run with no crash. The Friends
  sub-tab calendar block does not show on this account, so that lever was not seen either.
- **Every switch is on by default.** A stored value counts only while the row is in the build:
  `LineSettings.rowIncluded()` is a plain `return false`, and the settings patch rewrites it to return
  true. Without that, a user who turned a switch off and then installed a build without the row
  is stuck with "off".
- Each patch rewrites its own `LineSettings.<x>Included()` to return true, so the screen lists only
  the switches of patches in the build.
- Each patch with a switch depends on the nameless `lineSettingsExtensionPatch`, which bundles the
  extension.
- The values are read from SharedPreferences `andrew_line_settings` once and then kept in memory,
  because `keepunsent` reads its switch on the database path of each unsent message.

### To add a switch

1. Add `<feature>()` and `<feature>Included()` to `LineSettings.java`, and a row to
   `LineSettingsScreen.addSwitches`.
2. In the patch, depend on `lineSettingsExtensionPatch`, put the gate around the edit with the
   helpers in `line/shared/LineSettings.kt`, and call `markLineSettingIncluded("<feature>")`. A
   value gate also needs the `<feature>(boolean)` overload in `LineSettings.java`.
3. For a branch gate, pick a register that is dead at the gate. Check it in the smali, then check
   the result with `Dumpx` and `BranchSweep`.
4. Add the Japanese and Traditional Chinese text of the new row to `LineStrings.java`, with the
   words that LINE's own `values-ja` / `values-zh-rTW` strings use. Then run
   `python3 extensions/extension/check_line_strings.py`. A missing entry does not fail the build:
   the row just shows English.

### Language

The screen text is in English, Japanese or Traditional Chinese (`LineStrings.get`). The language is
the locale of the Settings Activity. LINE's own Language setting (`settings_languages`) uses the
per-app locale (`localeConfig` + `setApplicationLocales`), so it changes that locale and the screen
follows LINE, not only the phone. `ja` gives Japanese. `zh` with the `Hant` script, or the region
TW, HK or MO, gives Traditional Chinese. Any other language stays English. The title "Andrew's
Patch Setting", the "Unsent" label and the GPL text stay English.

Device-confirmed on LINE 26.18.2 (Android API 37). The per-app locale was set with
`adb shell cmd locale set-app-locales jp.naver.line.android --locales <tag>`: `ja-JP` and `zh-TW`
showed the translated screen and license dialog, and `ko-KR` showed English. To set it back, use
`--locales ""`.

---

## Custom fonts & the "[General] Custom font" patch

Issue #186 asked for fonts from the device. The patch adds buttons to LINE's own Font page
(Settings ▸ Fonts) and serves the picked file through LINE's premium font path. It is
`default = true`. Device-confirmed on a re-signed LINE 26.15.1. On 26.14.0, only apply and
disassembly confirm it.

### How LINE applies a premium font

- The style `premium_font` sets `fontFamily` to `@font/premium_font`. That font is a downloadable
  font: authority `jp.naver.line.android.premiumfont`, query `-1`, certificates
  `@array/fonts_certs` (LINE's debug, 2011 and 2022 certificates).
- `com.linecorp.line.premiumfont.provider.PremiumFontProvider` serves it. `query` gives one row
  for weight 400 and one for weight 700. `openFile` gives the file of the selected font. If the
  user selected no font, it throws `FileNotFoundException`.
- An `ActivityLifecycleCallbacks` (`mq4.t` in 26.14.0, `yw4.s` in 26.15.1) calls
  `setTheme(premium_font)` on each Activity. LINE registers it only while a premium font is in
  use. The init task `"RegisterPremiumFontThemeApplierTask"` asks
  `ResolvePremiumFontStatusUseCaseImpl` first. With "Default font", nothing requests the font.
- A change of font restarts the app. Android keeps each loaded font in the process.
  LINE's own restart (`hr4/c`) ends the process one second after it starts the splash screen.

### Why LINE's own fonts fail on a re-signed build

androidx `FontProvider.getProvider` (`f7/e.b` in 26.14.0, `j7/e.b` in 26.15.1) compares the
signing certificates of the provider app with the certificates of the request. A re-signed LINE
matches none of `fonts_certs`, so `getProvider` returns `null`, and `query` and `openFile` never
run. A device probe showed `getProvider -> null` on each request for the premium font. The same
check passes for Google's emoji font provider.

### What the patch does

| Site | Anchor | Change |
|---|---|---|
| `PremiumFontProvider.onCreate` | class and method name (manifest, `ContentProvider`) | registers our own `setTheme(premium_font)` callback while a custom font is set |
| androidx `FontProvider.getProvider` | string `"No package found for authority: "` | at the failed check, accepts a provider of LINE's own package. A provider of another package still fails |
| `PremiumFontProvider.query` | class and method name | with a custom font, one row for weight 400, so Android makes the bold style from the font |
| `PremiumFontProvider.openFile` | class and method name | with a custom font, returns that file before LINE's body runs |
| `FontSettingFragment.onCreateView` | class name (settings navigation graph) | wraps LINE's `ComposeView` with our button bar |
| Font list loader (`er4/f.b`, `qx4/f.b`) | `"Default"` + `System.currentTimeMillis` + the flag shape | after LINE's `move v4, v12` ("sync"), a value gate sets `v4` to "no sync" while the switch is on |

The extension is `CustomFont` (hooks and button bar) and `CustomFontPicker` (file picker and
close). `CustomFontPicker` is a hidden framework `Fragment` on LINE's own Activity, so the patch
adds nothing to the manifest. The file is in `files/andrewliang_custom_font`, and the shown name is in the
preferences `andrewliang_custom_font`. `android.graphics.fonts.Font.Builder` checks the file
before it replaces the font in use.

The switch "Custom font" in "[General] Andrew's Patch Setting" controls all of it. While the
switch is off, each extension method keeps LINE's behavior, the gate keeps LINE's sync, and the
Font page has no buttons. A change of the switch shows a "Close LINE" row, not "Restart LINE".
See "A font change needs a new process" in "Device traps". The font file stays on the device. The certificate bypass applies while
the switch is on, with or without a font file. So a LINE Premium user gets LINE's own downloaded
fonts back on a re-signed build too.

### Device traps

- **Root Mount keeps the original manifest.** A Root Mount install mounts the patched
  `base.apk` over the Play Store install. Android reads the manifest of the Play Store install, so
  an Activity that a patch adds does not exist there. The first version started its own
  `CustomFontActivity` and crashed with `ActivityNotFoundException`. Use no new manifest entry.
- **A restart that ends the process fails.** On the Xiaomi test phones (HyperOS, Android 16), a
  restart that ends the process does not come back. Two forms were tried: LINE's own sequence
  (exit one second later), and Morphe's `Utils.restartApp` (exit at once, as on YouTube). Android
  attaches the new launcher Activity to the old process as a warm start. It then removes it when
  the process dies: `Force remove immediately ActivityRecord{… SplashActivity}
  state=INITIALIZING`. A new task in the same process starts, as the other "Restart LINE" rows do,
  but it does not change the Compose screens (next item). So the picker closes LINE and does not
  open it again. The style hook is always registered, and it checks for a font on each new Activity.
- **A font change needs a new process.** LINE's chat tab is Jetpack Compose
  (`ChatTabComposeFragment`), and it keeps the font of the process. After a restart in a new task,
  the bottom bar (XML views) showed the change. The chat list kept the old font, after an upload
  and after a removal. androidx also caches the typeface of `@font/premium_font` in the
  process. So upload, change and remove all close LINE (`finishAndRemoveTask` on each task, then
  `System.exit`), and the user opens LINE again.
- **The bar color.** The window under the bar is black, and LINE's Compose page draws its own
  color. The bar copies one pixel from the bottom edge of the page, so it follows LINE's theme.
  On the device, the bar matched the page with the Black theme in dark mode (`#111111`). It also
  matched with Black chosen by hand in light mode. There, LINE paints only its header black, and
  the Compose page stays white (`#ffffff`).
- **The Font page error.** On the test phone, the list sync failed for some hours with "A
  temporary error occurred". Later the same day, with the switch off, it worked again. The loader (`er4/f.a`) turns each exception into the result `Network`. It and
  the page loader (`ar4/u`) discard the exception, so the cause is not known. The sync is the
  Thrift service `/EXT/talk/asset-meta-server/thrift/fontService` (`getAccessToken`,
  `getFontMetas`). The patch skips the sync. The list then comes from the local database:
  "Default font" on a new install, plus the fonts that an earlier sync stored.

### Limits

- Text in a script that the font does not have falls back to the system font. A CJK font
  (Iansui) drew Chinese chat names, previews and bubbles on the device.
- A few labels keep the system font, for example the Chats/Friends switch and the search hint.
- LINE's preview bubbles on the Font page keep their own fonts.
- The labels of our buttons are in English. The close dialog is in English too, apart from LINE's own Cancel label.

### Values that drift on a version bump

`mq4.t`/`yw4.s` (the callback that LINE registers), `f7/e`/`j7/e` (androidx `FontProvider`),
`er4/f`/`qx4/f` (the list loader) and its registers `v4`, `v10`, `v12`. The patch finds each one
by shape or by string, not by name.

---

## Themes on a sub device & the "[General] Enable themes on sub devices" patch

A sub device is a second phone or a tablet that logs in to an account whose main device is
another phone (LINE's `SECONDARY` device type). On a sub device LINE hides Settings ▸ Themes,
keeps the app in light mode and blocks the Theme Shop (issue #221).

### The sub-device flag

The flag is an interface method that LINE gets from its service locator. The patch reads it
through this sequence in every version: `sget-object key`, `invoke-static lookup(Context, key)`,
`move-result-object`, `check-cast`, `invoke-interface …()Z`.

| | 26.16.0 | 26.17.1 |
|---|---|---|
| Flag | `fa0/d0.q()` | `ca0/d0.o()` |
| Dark-mode check | `gk8/o.b()Z` | `wl8/q.b()Z` |
| Night-mode setter (called by `LineApplication`) | `z60/a.a(Z)` | not mapped |
| Settings builder (Themes row) | `eh5/t2` | `ji5/n2` |
| Row visibility → always-true | `ec5/k0.u` → `ec5/k0.m` | → `jd5/h0.m` |
| My Themes adapter (Theme Shop footer) | `si7/j` | `hk7/k` |
| Shared shop opener | `dl7/a.d(…, yf7/f, …)V` | not mapped |

Do not hook the flag itself. It has about 95 callers, and it also sets the app type that LINE
sends to the server (`ANDROIDSECONDARY`), E2EE key handling, FullSync and backup restore.

### The theme gates

- **Dark mode.** At start, `LineApplication` calls the dark-mode check and passes the result to
  the night-mode setter. The check returns false at once on a sub device. Otherwise it reads the
  "Use Black theme when in dark mode" setting (`THEME_AUTO_DARK_MODE`).
- **Settings ▸ Themes.** The row constructor takes a visibility predicate. For Themes it is
  `!isSubDevice` (the static field `u` of the base row class). The field `m` of the same class
  always gives `true`.
- **Theme Shop.** The "Theme Shop" button under My Themes calls the shared shop opener
  `dl7/a.d`. On a sub device it shows `secondary_invalidscheme` ("Your device doesn't support this
  type of URL"). The opener also serves `STICKER_SHOP`, `STICKER_PREMIUM_*` and `STICON_SHOP`.
  `dl7/a.e`, `dl7/a.g`, the theme LIFF opener `dg7/j.g` and seven deep-link handlers (for example
  `nr8/l1.d`, `line://shop/theme/…`) check the flag too.

### Theme downloads are not gated

A theme downloads and applies on a sub device. The "To use this, please update the app to the
latest version" dialog (`shop_need_update_application`) is a version check, not a sub-device
check. My Themes (`qi7/u0` on 26.16.0) sets `isCurrentAppVersionSupported` from the theme's
`appVersionRange` (`gk8/o.a`). On 2026-11-14 the server gave Black, Cony and Brown a minimum of
26.17.0, so a main device on 26.16.0 gets the same dialog. The dark-mode path does not check the
range, so Black still downloads.

### What the patch does

1. Clears the flag after its `move-result` in the dark-mode check. The check then goes on to the
   user's setting.
2. Adds `isSubDevice(Context)Z` to the class of the dark-mode check. It copies the flag sequence
   from that check.
3. Passes the always-true predicate (`m`) to the Themes row constructor.
4. In the My Themes adapter, after the null check of the `shop_button` TextView, calls
   `isSubDevice` and `SubDeviceThemes.hideThemeShop(Z, View)`. On a sub device the button is
   `GONE`. A main device keeps the button.

The patch does not open the shop. On a main device the patch changes nothing, because the flag is
already false there.

### Verification (26.16.0, sub device, 2026-11-14)

A probe logged `isSecondary=true`, then `result=true` and `MODE_NIGHT_YES` with the check forced.
With the patch, Settings ▸ Themes showed My Themes, Purchase history, Gift Box and the Black
switch. With Original selected and the switch on, a cold start in system dark mode showed Black,
and in system light mode Original. My Themes had no Theme Shop button. Cony downloaded and applied
(probe with the version check forced). Applying a theme by hand in dark mode turns the Black switch
off. This is LINE's own behavior. No crash and no `VerifyError`. Nobody tested a main device with
the patch.

### Values that drift on a version bump

Everything in the table above. The patch finds the dark-mode check by `THEME_AUTO_DARK_MODE` and
return type `Z`, the Settings builder by `MORETAB_SETTINGS_THEMES` and `settings_theme_title`, and
the adapter by the footer layout, the `shop_button` id and a `check-cast` to `TextView`.

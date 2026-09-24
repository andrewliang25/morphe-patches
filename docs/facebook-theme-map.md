# Facebook theme & colour map

Reference for the "[General] AMOLED black theme" patch, from a decompile of **577.0.0.50.72**. The
variant and versionCode warning in [`facebook-ads-map.md`](facebook-ads-map.md) applies here too.

> ⚠️ **Drift.** `LX/1s9;`, `LX/1tK;`, `LX/G4l;`, `LX/DkV;`, `LX/Djq;` and the method names `EYJ`,
> `A0A`, `A0B`, `A00` are Redex names. They change on every release, about every two weeks. No
> fingerprint uses one — see [Anchoring](#anchoring).

Credit to [SapitoSucio/FroggoMorphePatches](https://github.com/SapitoSucio/FroggoMorphePatches)
(GPLv3) for the idea of theming Facebook from the outside.

---

## Who decides

Facebook resolves each colour **in its own process**. Thus it is a client decision and patchable.
A Bloks screen gets its colour from the server, but as text that the app parses in its own
process. Thus that colour is patchable too (route four).

---

## The four routes

A colour reaches the screen four ways. The patch covers all four with one rule. The first three took
three device rounds to find, and the fourth came from issue #108. Covering only the first leaves
most of the app grey.

### Route 1 — a component asks the design system

Two systems, and they share no code.

**Mig.** Each of the ~120 getters on `BaseMigColorScheme` reads a token and calls
`MigColorScheme.EYJ`, so one method per scheme decides the whole palette:

```
BaseMigColorScheme.<~120 getters> -> MigColorScheme.EYJ(LX/G4l;)I
  -> DarkColorScheme.EYJ  -> token.BHA()I   (dark)
  -> LightColorScheme.EYJ -> token.BdZ()I   (light)
       -> LX/Dir;->A00(LX/Dim;, Z)I -> ColorData.A00:I (dark) / A01:I (light)
```

A colour reaches `DarkColorScheme.EYJ` only because the dark scheme was asked, so the patch needs no
test for dark mode.

**FDS**, which carries most of the app:

```
FDSColors.A0B(token, componentContext)I   1473 callers  (Litho)
FDSColors.A0A(context, token, palette)I     14 callers
    three sources, first hit wins: A05 override, then LX/27K;->A0E (DSP), then LX/1tN;->A03 (attr)
LX/1tK;->A01(Context, LX/1s9;)I            893 callers  (view code) -> LX/1tN;->A03
```

`FDSColors` and `LX/1tK` never call each other, and neither passes through Mig.
`FdsColorScheme.A00` is 1 of the 894 callers of `LX/1tK;->A01`, so the patch hooks the resolver and
not the wrapper. `A0A` and `A0B` each return from two places, and both need a hook.

**Six sites, four methods:**

| Method | Returns | Reach |
|---|---|---|
| `mig/scheme/schemes/DarkColorScheme;->EYJ` | 1 | the Mig dark palette |
| `fds/core/theme/component/FDSColors;->A0B` | 2 | 1473 Litho callers |
| `fds/core/theme/component/FDSColors;->A0A` | 2 | 14 callers |
| `LX/1tK;->A01` | 1 | 893 view callers |

None calls another, so no colour is transformed twice. Each hook appends `invoke-static` and
`move-result` before a `return`. Nothing is replaced and no branch is added.

### Route 2 — a view reads a colour resource

Some views never ask a resolver. They read a colour by id through `LX/1tK;->A02(Context, LX/1s9;)I`.
That method returns a **resource id** and not a colour, so `Resources.getColor` fetches it and no
int passes a hook. `#101011` is the resource `0x7f06014c`, and it is what the night table points to.
This is why the top bar, the composer and the story tray stayed grey after route 1 alone.

A `resourcePatch` rewrites `res/values/colors.xml` and `res/values-night/colors.xml`. On
577.0.0.50.72 it changes **39 entries**, among them `0x7f06014c` (the wash), `0x7f060002` night
(`#242526`) and `0x7f060452` night (`#252728`). Night text is far above the threshold and is
untouched.

### Route 3 — code writes the colour itself

The palette tables, the top bar, the system bars and each Litho component that draws its own chrome
hold the colour as a literal. Neither a resolver nor a resource reaches them. This is why the Reels
bar stayed `#252728` after routes 1 and 2.

The patch sweeps every class with `classDefForEach` and rewrites each dark grey it writes, which
costs about 30 seconds. Two narrower anchors were tried first and both fell short: the name-kept
chrome classes, and the methods that return `ColorData`.

### Route 4 — the server sends the colour as text

Server-driven screens get their colours as strings, such as `"#FF252728"`, and the app parses them
with `Color.parseColor`. No resolver, resource or literal holds these colours. This is why the
profile cards and Settings stayed `#252728` after routes 1 to 3 (issue #108).

The patch replaces each call to `Color.parseColor` in the app with a call to
`AmoledTheme.parseColor`, which has the same signature. That method parses the text and applies the
rule to the result. On 577.0.0.50.72 the sweep changes **289 calls in 163 classes**. It skips the
classes of the extension, because the replacement calls the original.

How the source was found, so that nobody does it again:

- A probe logged each dark grey that the route-1 hooks returned unchanged. For these screens it
  logged nothing, so the grey does not reach a hook.
- A second probe blackened each grey `ColorData` at construction. The screens stayed grey, and no
  grey `ColorData` was built, so the DSP palette is not the source.
- Every card-grey resource was already black in the patched APK. A scan of the dex found no card
  grey as an array element, a wide constant or a string.
- A third probe sent `Color.parseColor` through the extension. The log showed `"#FF252728"` parsed
  at `X.438.BAN` and `X.4fl.A03`, and both screens turned black.

### Seams rejected

- **`LX/Dir;->A00`** — downstream of `DarkColorScheme.EYJ` with no extra reach (22 callers), on an
  obfuscated class. Its shape is not unique: `LX/1xZ;->A01`, `LX/XBU;->A00` and `LX/ebV;->A01` are
  identical instruction for instruction.
- **`LX/1tN;->A03` / `A00`** — deeper, but they return from inside `monitor-enter` regions with
  `move-exception` handlers and cache branches near the return.

---

## The rule

One rule for all four routes and for the extension. A colour becomes black when it is **opaque**
and every channel is at or below `MAX_CHANNEL = 0x2A`, and when it is **near-neutral**: the channels
are within `MAX_SPREAD = 8` of each other.

`MAX_CHANNEL` comes from measurement:

| Colour | Highest channel | What it is |
|---|---|---|
| `#101011` | `0x11` | page (`WASH`, measured) |
| `#242526` | `0x26` | card |
| `#252728` | `0x28` | raised card (measured) |
| **`0x2A`** | | **the threshold** |
| `#3A3B3C` | `0x3C` | divider / border |

`MAX_SPREAD` keeps a dark green or dark brown banner in its own colour. Only a grey is a background.

Route 1 also tests the **token**, because a colour alone cannot tell a card from a dark divider. The
opaque and dark test is what keeps light mode correct with no mode check: a light-mode card is white
and falls through. Route 4 has no token, so it uses the colour alone, like routes 2 and 3.

---

## Anchoring

R8 keeps three things. The class names `DarkColorScheme`, `LightColorScheme`, `BaseMigColorScheme`,
`DelegatingMigColorScheme`, `fds/FdsColorScheme`, `fds/FdsDarkColorScheme`,
`fds/core/theme/component/FDSColors` and `dsp/core/ColorData`. Every **enum constant name**. The
field names `LX/1s9;->attr`, `lightModeFallBackColorInt`, `lightModeFallBackColorRes` and
`LX/DkZ;->dspUsageColor`.

R8 renames **every method**, so no fingerprint uses a method name. Routes 2, 3 and 4 use no
Facebook name at all. Routes 2 and 3 match on the value, and route 4 matches on the framework call
`Color.parseColor`. That is what makes them survive both the renaming and the stripped resource
names.

`DarkColorScheme->BJX()I` returns the dark theme style id and `LightColorScheme->BJX()I` the light
one (`0x7f1f039e` / `0x7f1f039f` here). Read them from those methods instead of hard-coding them.

---

## Tokens

The token that route 1 gets is an enum constant with a semantic name.

**Mig** — ten enums implement `LX/G4l;`. The surface one is `LX/DkV;`: `WASH` `SURFACE` `CARD`
`ELEVATION` `BANNER` `FILL` `LINE` `SCRIM` `MESSAGE_BUBBLE` `REPLY_BUBBLE` `PRIMARY_UI` `SELECTED`
`HOVER_OVERLAY` `PRESSED_OVERLAY` `DISABLED` `HANDLE` `ACCENT_GREEN`. The others hold text, icons,
buttons, badges, fixed colours and shades.

Names repeat across enums. But none of the six short names the patch uses appears outside
`LX/DkV;`, so one flat set needs no test per enum.

**FDS** — `LX/1s9;` holds **323** constants that spell out where each is used, such as
`SURFACE_BACKGROUND` and `NAV_BAR_BACKGROUND` next to `DIVIDER` and `CARD_BORDER`. `LX/Djq;` is a
52-constant set with a `BACKGROUND_*` prefix.

The list the extension acts on is in `AmoledTheme.java`. Left out: borders, dividers, shadows,
buttons, switches, tooltips, text, icons, every `*_ON_MEDIA` and `*_ON_COLOR` token, and anything
translucent.

---

## The device round (2026-09-22)

Redmi `marble` / Android 17, Facebook 577.0.0.50.72 through `patched-apps`. No `VerifyError`, no
crash, no `Resources$NotFoundException`.

| Surface | Before | After |
|---|---|---|
| Status bar, top bar, composer, story tray | `#171818` | `#000000` |
| Feed and the space between posts | `#101011` | `#000000` |
| Bottom bar | `#252728` | `#000000` |
| Reels top bar | `#252728` | `#000000` |
| Comments sheet | `#101011` | `#000000` |
| Profile header card and "At a glance" rows (route 4, 2026-09-24) | `#252728` | `#000000` |
| Settings & privacy, and its Notifications page (route 4, 2026-09-24) | `#252728` | `#000000` |

After route 4, the buttons and the search field on those screens are `#191919`. They are a
translucent layer, so they darken with the page and stay visible. Light mode keeps these screens
white.

Black went from 19% of the feed to 47%.

### The probe that made it possible

A probe build logged each token and colour pair once **and** blackened every near-black colour
without the token list. That second part turned guessing into measuring. A surface that did **not**
change under it could not be reaching the hooks at all. That ruled out the token list, and sent the
search to resources and then to literals.

It also gave the real palette. Every logged token was `X.1s9`:

| Token | Measured |
|---|---|
| `WASH` | `#FF101011` |
| `SURFACE_BACKGROUND` / `NAV_BAR_BACKGROUND` | `#FF252728` |
| `PRIMARY_TEXT` | `#FFF2F4F7` |
| `SECONDARY_TEXT` / `SECONDARY_ICON` | `#FFB0B3B8` |
| `ACCENT` | `#FF0866FF` |

This is the **darker** palette (`WASH` is `#101011`, not the `#18191A` the resource table gives).
**No Mig token appeared at all**, so `DarkColorScheme.EYJ` never ran: Facebook uses the FDS scheme
and resolves Mig tokens through `LX/1tK`. The Mig hook stays because it costs nothing.

To repeat it, make `apply` log `((Enum<?>) token).name()` with the colour and return the colour
unchanged, then read `adb logcat -s AndrewFbTheme:I` while walking the app in both modes. Do not
push the probe commit.

---

## Known gaps

1. **Bloks screens that do not use `Color.parseColor`** — route 4 covers the screens that parse a
   colour string with it. A screen that parses colours another way stays grey.
2. **WebViews and the in-app browser** — the page decides.
3. **Blends and drawable assets** — a translucent layer over another colour, a gradient or a bitmap.
   The base of the "Create story" tile (`#202021`, about 2% of the feed) is the one visible case. It
   is neither a literal nor a resource.
4. **The comment input bar** (`#191919`) — left out on purpose, so the field stays visible against a
   black sheet.
5. **Images, video, gradients, avatars** — unchanged by definition.
6. **A/B tests** — Facebook ships different surfaces to different accounts, so one device is weak
   evidence.

---

## Why the value and not the id

The reference patch sets one FDS theme attribute to `#ff000000` in two dark styles
(`style.2_0x7f20022b` / `22c`, attr `0x7f040646`). This patch reaches the same surfaces by value:

1. **The ids drift.** In 573 the second style type was `0x20`; in 577 it is `0x1f`, so every
   `0x7f20xxxx` id is wrong.
2. **Facebook strips resource names.** `aapt2` prints `color/(name removed)`, and the decoded
   placeholder names differ between decoders (`APKTOOL_RENAMED_0x…` under stock apktool, `color_0x…`
   under the patcher fork). A name is not an anchor.
3. **One attribute is not enough.** The palette also lives in `assets/dsp/*.p` blobs and in code
   literals, which no resource edit reaches.

---

## Recipe

```bash
rm -rf patches/build/libs   # a stale .mpp gets applied silently
./gradlew :patches:buildAndroid --offline --no-daemon -Pgpr.user=dummy -Pgpr.key=dummy

java -jar work/morphe-desktop-1.12.0-all.jar patch -p patches/build/libs/patches-*.mpp \
  --exclusive -e "[General] AMOLED black theme" -f --unsigned \
  -o work/fb-amoled.apk work/fb-extract/base.apk
rm -rf work/verify-amoled && unzip -o -q work/fb-amoled.apk 'classes*.dex' -d work/verify-amoled

# work/Fb.java reads FBDEX. A `--dex <dir>` argument is IGNORED and it then disassembles the
# UNPATCHED apk, which looks exactly like a failed fingerprint. Always export FBDEX.
export FBDEX=work/verify-amoled
java -cp .:smali-dexlib2.jar Fb dump 'Lcom/facebook/mig/scheme/schemes/DarkColorScheme;' EYJ
java -cp .:smali-dexlib2.jar BranchSweep work/verify-amoled/classes*.dex
```

`Fb dump` does not print `invoke` operand registers, so it cannot show whether the injected call
takes the register the `return` reads. Check that separately.

**For a device build:** seed `patched-apps/temp/andrewliang25-rv/patches-<ver>.mpp` and
`temp/com.facebook.katana-<ver>-arm64-v8a.apk`. Use `work/fb-extract/base.apk` for the second one,
which keeps the original signature and passes `check_sig`. Then run `build.sh` in the `rvmm-build`
Docker image, because `utils.sh` needs bash 4.2 and cannot run on macOS directly.

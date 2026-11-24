# LINE limits and dead ends

Which limits LINE decides on the phone, the photo pipeline, and the areas that are not patchable. See [README.md](README.md) for the other LINE docs.

## Feature limits — client-side vs server-enforced (LINE)

Before patching any LINE "limit", establish **who decides**. A constraint LINE computes or self-reports **inside its own process** is patchable; one decided by a remote server or another process is not, and removing the client check just moves the failure later. **Descriptors in the table below are 26.11.0-era** for the surfaces that ship no patch (outbound photo, ringtone, video limits) — they were not re-derived for 26.14.0. Note `c81` there is the *old* video/file-size package and is unrelated to 26.14.0's `c81` (the context-menu action enum, formerly `j51`).

| Limit | Who decides | Verdict |
|---|---|---|
| Outbound photo compression | **client** — `u13.c1` resamples and JPEG-encodes locally; the server never sees the original | patchable, but **dropped**: one gate per entry point and only the chatroom flow was coverable |
| Unsend time window | **server** — `unsendMessage` carries only `(seq, messageId)`; `MESSAGE_NOT_DESTRUCTIBLE(71)` comes back | not patchable |
| Scheduled-message edit / reschedule (new in 26.14.0) | **server** — `fq1.a` is a server error-code enum: `MESSAGE_NOT_MODIFIABLE`, `MESSAGE_RESCHEDULE_UNAVAILABLE`, `INVALID_PREMIUM_STATUS`, plus three `MAX_*_EXCEEDED` quota codes | not patchable |
| Video length / file size | **server** — client checks in `c81.b.c()` mirror an OBS ceiling (`EXCEED_FILE_MAX_SIZE`) | not usefully patchable |
| LYP premium entitlements | **server** — account state; `hidepremium` only hides the upsells | not patchable |
| Call ringtone selection | **client** — `be7.c.a` can return an arbitrary `Uri`, played by LINE's own `MediaPlayer` in-process (`xx.c`) | **patchable** — investigated, deliberately not shipped |
| Ringback tone friends hear | **server** — the callee's tone reaches the *caller's* client in their connect info | not patchable |

- **Client-side config is not server enforcement.** Unsend windows and photo tiers are both pushed from server settings (`function.chatroom.message.unsend.timelimit`, `function.media.image_high`), but the photo tier is only consumed locally to decide how hard to compress whereas the unsend window pre-filters a decision the server re-makes. A server-supplied *value* is patchable; a server-made *decision* is not.
- **Look for the error code.** A dedicated Thrift/HTTP error for the exact condition (`MESSAGE_NOT_DESTRUCTIBLE`, `EXCEED_FILE_MAX_SIZE`, `EXCEED_DAILY_QUOTA`) proves the server enforces it independently; the client check just fails fast.
- **A client check with no server counterpart is the opportunity.** Nothing validates the *encoded* output of LINE's image pipeline: `c1.o()` is a single-shot `Bitmap.compress`, and the picker's gates test the **source** file.
- **Ask which process opens the resource, not just which decides.** The call ringtone is patchable because LINE's own `MediaPlayer` opens the URI in-process; Google sign-in is not, because GmsCore reads the real signature in another process.
- **"Patchable" is not "worth patching."** The ringtone came out patchable and shipped nothing: following the device ringtone is a feature nobody asked for, and an in-app picker needs a new Activity plus a settings row injected into obfuscated declarative Kotlin. Record the finding here and stop.
- **Lead a patch proposal with the value case:** who is blocked today and what they get. Then give the mechanism. If a design needs new UI or a new Activity in obfuscated code, say so first. That alone can sink it. Bank a "patchable but marginal" result in the map doc, with its anchors and why nothing shipped.

### Values that drift on a version bump

| Thing | 26.14.0 |
|---|---|
| gates in `t73.k0.b0` | `0x1400000` (20 MB), `0x5f5e100` (100 MP) — also in `k0.X(Z)V` and `m63.n0.d()`, so pin the `(Ljava/util/ArrayList;)V` signature |
| lambda anchor | `u13.y0` — the only class combining `ContentResolver.getType`, `MimeTypeMap.getMimeTypeFromExtension`, `BitmapFactory.decodeStream` and `Matrix.setRotate` |
| encoder | `c1.o(I, Bitmap, File)Z`; quality read from `dw0.b$b.b:I` |
| decode multiplier | `c1.l()` → `mul-int/lit8 … 0x4` (left alone; it is tier-agnostic) |

---

## Outbound photo pipeline (reference — no patch ships for this)

> **A "Send original photos without the quality drop" patch was built, device-confirmed (a 126.5 MP /
> 34 MB photo arrived at 24 MP instead of 1.64 MP, via five sites across two send paths plus an
> extension), and then deliberately dropped.** Recorded so nobody re-derives it. Why it was dropped:
>
> - **Coverage cannot match the promise.** It fixed the chatroom `+` / photo-strip flow only. Album is
>   unfixable without inflating every album upload (no "Original" button, below), share-to-LINE is a
>   third path never traced, and a user cannot tell which entry point they used. A patch that silently
>   applies to some sends invites bug reports.
> - **Maintenance is a re-investigation, not a fingerprint refresh.** Four fingerprints, one on a
>   synthetic Kotlin lambda (`th1.t$c$b`) that reshuffles whenever its enclosing method changes — and
>   four device rounds went to sites that disassembled perfectly on a path that never executed. Each
>   LINE bump needs a device send with an oversized photo, not just a clean build.
> - **The trigger is rare.** A 12 MP phone JPEG is 3–6 MB; crossing 20 MB needs a 50–200 MP mode, and
>   the 100 MP gate needs pixel-shift.
>
> If revived, the minimal form is **`th1.t$c$b`'s two literals alone** — one fingerprint, two
> `replaceInstruction` calls, no extension. With the flag preserved an oversized JPEG takes LINE's
> stock raw byte copy at *full* resolution, losslessly — better output than the 24 MP q80 re-encode
> the extension produced, which only existed to keep ~34 MB off the wire.

### The two paths a photo can take

`u13.c1.f(dVar, fVar, uri, rotation)` writes the local file that gets uploaded. A photo has exactly
two outcomes, with no tier in between — that is the defect.

| `cw0.f` | What `c1.f` does |
|---|---|
| `IMAGE_STANDARD` | `c1.p()` only — resample to the tier's **pixel budget** and JPEG re-encode |
| `IMAGE_ORIGINAL` | `c1.p()` *and* the `u13.y0` lambda, which raw-copies or full-res re-encodes |

`d98.m1` then uploads **one** of them (`IMAGE_ORIGINAL` when the message metadata carries
`IS_SEND_ORIGINAL_IMAGE`, else `IMAGE_STANDARD`) plus a thumbnail. OBS derives the rest by URL
path (`w78.b`): `…/r/talk/m/<id>` standard, `…/m/<id>/original`, `…/m/<id>/preview`.

### The pixel budget is a *total*, not a per-side cap

`c1.k(STANDARD_IMAGE)` returns `maxDimension²` **pixels**. `c1.m()` returns the bitmap untouched
when `width*height <= budget`, so raising `maxDimension` past a photo's pixel count disables
resampling entirely. `c1.l()` decodes to within `budget * 4` first (`mul-int/lit8 … 0x4`) and then
scales precisely — that multiplier is what avoids coarse power-of-two subsampling, so lowering it
costs quality for any source between 1x and 4x the budget.

Values come from `jp.naver.line.android.util.f1.a()` — non-obfuscated, and the best anchor into
this whole area:

| Tier | Class | Server key | Default | Selected when |
|---|---|---|---|---|
| Normal | `t88.a$b$b` | `function.media.image_medium` | 1280 / q70 → **1.64 MP** | `RESIZE_IMAGE_OPTION != 2` (incl. unset) |
| High | `t88.a$b$a` | `function.media.image_high` | 2048 / q80 → **4.19 MP** | `RESIZE_IMAGE_OPTION == 2` |

`p38.a.c` is `NORMAL(0) / SMALL(1) / LARGE(2)`, but `f1` tests for exactly `2`, so `SMALL` is dead
for uploads (it only suffixes download URLs in `r78.h`). The tiers are **separate classes**, which
is what lets a patch touch one without the other. Four consumers: `dw0.c` (chat), `gg3.j`,
`ch0.j`, `od7.d`.

### The cliff

`t73.k0.b0()` stamps `rt7.c.isOriginal` (field `B`) per item as the picker finalises. With the
"Original" toggle on it clears the flag at `>= 20 MB` (`0x1400000`) or `>= 100 MP` (`0x5f5e100`),
dropping the photo onto the standard path. One byte under the threshold a photo is copied
verbatim; one byte over it loses 6–25x its pixels, with the toggle still showing on. The only hint
is `gallery_original_guide_error` — *"Videos and some photos may be sent in standard resolution."*

Nothing validates the encoded output: `c1.o()` is a single-shot `Bitmap.compress` with no size
check, and `b0()` / `m63.n0.d()` test the **source**'s length and dimensions, never the result.

The `u13.y0` lambda branches on mime (`ww0.c.a`): `image/jpeg|png|gif|bmp` (and an unresolvable
type) → raw byte copy, then `i48.a.b` strips 38 GPS/timestamp EXIF tags (Orientation survives);
anything else (HEIC, WEBP) → full-res decode + `Matrix` rotate + JPEG at the tier quality.

### The `>= 20 MB / >= 100 MP` test exists in FIVE places

The most important fact here, and it cost four device rounds. The same literals (`0x1400000`,
`0x5f5e100`) are duplicated across two independent send paths plus Album, and **any one alone is
enough to drop the photo to the standard variant**:

| Copy | Decides | On the chatroom path? |
|---|---|---|
| `th1.t$c$b.invoke` | `Function1<w51.c, Boolean>`; `un1.f.a` calls it per item and feeds the result into `un1.k$b$c(Uri, isOriginal)` → `IS_SEND_ORIGINAL_IMAGE` | **yes — this is the one that decides** |
| `m63.n0.f()` / `d()` | media-picker toggle availability → `m63.n0.i(Z)` → `u53.e.a` | no |
| `t73.k0.b0` | media-picker per-item `rt7.c.B` stamp | no |
| `wi0.h.g` | LINE **Album**'s own compressor (`ri0.c.c`, `com.linecorp.line.album.transfer.*`) | no |
| `th1.u.invokeSuspend` | guide text only (`ORIGINAL_IMAGE_GUIDE` vs `ERROR_GUIDE`, via `th1.c0`/`d0`) | cosmetic |

Two send paths reach `u13.c1.f`, and they do **not** share the decision:

- **Chatroom `+` / photo strip** — `th1.t.h(Z)` reads the toggle (`t.f()` = the toggle view's
  `isSelected()`), launches `th1.t$c`, and passes `th1.t$c$b` to `un1.f.a`. `th1.a0` builds fresh
  `rt7.c` items with `B = t.f()`. **The media picker is never involved.**
- **Full gallery picker** — `m63.n0` + `t73.k0.b0`.

`wi0.h.g` is deliberately left alone: Album has **no "Original" button**, so its `IMAGE_ORIGINAL`
branch is unreachable and the only lever left is the always-compress branch, which would inflate
every album upload. Album's config is `ch0.j.m()` → `ei0.n(maxDimension², quality, 8192|1280)` off
the *same* `f1.a()` tier as chat, so if an Original button ever appears the fix is to swap that
config for a bounded one, not to neutralise the gate.

### What the dropped patch did (for reference)

Five sites. Site A was the one device-confirmed to decide the outcome; Sites 0 and 1 covered the
gallery-picker path and were never device-verified.

- **A.** `th1.t$c$b` — both gate literals → `0x7fffffff`, so the per-item predicate is always true.
- **0.** `m63.n0` — both literals in *every method holding both* (`f()` and `d()`). `onClick`'s lone
  `0x1400000` is a free-disk-space multiplier (`getFreeSpace() >= 20 MB * itemCount`) — never touch
  it, which is why the rule keys on "contains both" rather than on the size literal.
- **1.** `t73.k0.b0` — both gate literals → `0x7fffffff`, so `isOriginal` stays true. Rewriting the
  compared *value* rather than the branch keeps control flow byte-identical.
- **2.** `u13.y0` head — call an extension that re-encodes; `null` falls through to stock code. The
  fall-through target **must** be an `ExternalLabel`, not a label written inside the injected block.
- **3.** `u13.y0` — the tier-quality `iget` feeding `c1.o` → `const/16 0x50` (q80).

The extension re-derived the same `>= 20 MB || >= 100 MP` test the sites removed and returned `null`
otherwise, leaving every already-working case untouched — notably a 50 MP / 10 MB JPEG, which must
keep being copied byte for byte. Output was bounded to 24 MP via `inSampleSize` plus
`inScaled`/`inDensity`/`inTargetDensity`. The density scaler matters because `inSampleSize` alone
quantises a 126 MP source to ~7.9 MP. Rotation was baked into the pixels rather than written as an
EXIF tag, matching every other LINE encoder on this path.

**Rotation must come from LINE, not from EXIF.** `c1.f(dVar, fVar, uri, Integer rotation)` hands
the *same* `Integer` to `c1.p` (the standard variant, so the thumbnail and OBS's `/preview`) and to
the `y0` lambda. Both prefer it and fall back to the file's EXIF (`c1.d(uri)`, an `ExifInterface`
"Orientation" read) only when it is `null`. So the patch passed the lambda's `Ljava/lang/Integer;`
capture (`y0.c`) in with EXIF as fallback only: deriving from EXIF unconditionally leaves the original
sideways relative to the standard variant whenever the caller supplied a rotation the file lacks.

Sites 2 and 3 resolved `u13.c1`, its `Context` field and the lambda's captures (`c1`, `Uri`,
`Integer` rotation) **from the matched method's own bytecode**; only the two literals and framework
types were hardcoded. Within the matched method, the only `iget-object` whose `FieldReference.type`
is a **framework** type (`Landroid/content/Context;`) yields both the obfuscated owner's descriptor
(`u13.c1`) and the field name for free; feeding that descriptor back in finds the enclosing lambda's
synthetic captures (`definingClass == method.definingClass`).

---

## Dead ends (investigated, not patchable)

**Extend the unsend window.** The client windows (`c81.a.o` free, `.p` premium) are UX pre-filters fed
by server config (`function.chatroom.message.unsend.timelimit`, `.premium.timelimit`). `unsendMessage`
carries only `(seq, messageId)`. The server decides, with a dedicated `TalkException` code
`MESSAGE_NOT_DESTRUCTIBLE(71)` (`cb8.m9`) handled at `kh1.o2` / `kh1.b2` → *"You cannot unsend this
message as too much time has passed."* Widening the client window only re-shows the menu item and
produces that toast. (`hidepremiumunsend` narrows it for the same reason.)

**Remove video length / size limits.** `c81.b.c()` rejects `> 301000 ms` and `> 209715200` bytes, and
the picker sets `maxVideoDurationSec = 300` at every chat entry point. Both are trivial to remove, but
the OBS gateway enforces its own ceiling — `rc1.b` parses the `x-line-obs-talk-exception` header
carrying `EXCEED_FILE_MAX_SIZE` / `EXCEED_DAILY_QUOTA` / `NOT_SUPPORT_SEND_FILE`. Removing the client
checks trades a clean local toast for a mid-upload server rejection.

**Change the ringback tone your friends hear.** The *callee's* ringback is delivered to the **caller's**
client in their connect info and played there, so no local edit changes what a friend hears — same class
as the unsend window. (The ringback *you* hear while dialing out is client-side, `oy.d.f261845a` /
`be7.a$b`, but changing it helps nobody.) The incoming-call **ringtone** is different and *is* patchable
— see [Call ringtone pipeline](calls.md#call-ringtone-pipeline-investigated-deliberately-not-shipped).

**Log in with / link a Google account on a re-signed build.** The framework picks the credential
provider, so nothing in the APK names Play Services — see [the two-path section](resigning.md#google-account-access--two-independent-paths-one-patched-one-not).
The Drive **backup** picker is a different mechanism and *is* patchable.

**Note on the OBS size ceiling for *photos*.** It has never been observed directly — the 20 MB figure
is inferred from LINE's own client-side threshold (`0x1400000`, the photo pipeline's gate), not from
anything the gateway reported. Sub-20 MB originals are known to upload byte-identical, so that much is
safe; record the real limit here if a device test ever surfaces `EXCEED_FILE_MAX_SIZE`. This says
nothing about video, whose client check is the separate 200 MB one above.

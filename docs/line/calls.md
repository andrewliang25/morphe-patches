# LINE calls

Call recording and call ringtones. See [README.md](README.md) for the other LINE docs.

## Call recording & the "[Call]" patches

Four patches, all on by default, each with its own switch:

- **`[Call] Auto-record calls`** (switch `autoRecordCalls`): starts recording when a 1-to-1 call
  connects.
- **`[Call] Record calls without notifying`** (switch `silentCallRecording`): records without the
  peer banner and without the spoken announcement.
- **`[Call] Save recordings to device`** (switch `saveCallRecordingsToDevice`): saves each finished
  recording to `Movies/LINE` instead of sharing it to Keep Memo. See
  [Where recordings are saved](#where-recordings-are-saved).
- **`[Call] Remove recording time limit`** (switch `removeCallRecordingLimit`): records until the
  call ends. See [Recording time limit](#recording-time-limit).

The first two depend on an internal `callRecordingEnablePatch` that exposes the feature. LINE's own
recorder does the capture and saves the file. The patches only trigger it, remove the outbound
notice, choose where the copy goes, and remove the time limit.

**Version:** call recording is **26.16.0+ only**. The `premiumcall` / `recording` packages and
every `callrecording_*` string are absent from 26.14.0 and 26.15.1 (measured 2026-11-15). Both
supported versions have it, so the `[Call]` patches use `COMPATIBILITY_LINE` and resolve each site
with `.method`. A site that drifts fails the patch instead of skipping with no error.

LINE's call recording is a **LINE Labs** feature (`linemain_labs_desc_callrecording`
"Record voice and video calls in a one-to-one chat") that is also **LYP-Premium / eligible
LINE-Music gated** (`callrecording_callscreen_desc_nonlypmember` / `liteplanmember` /
`linemusicmember`). The other party is notified on start
(`chat_entry_desc_callrecordings` "The other party will be notified when you start recording
the call" → their screen shows `callrecording_callscreen_desc_otherpartyrecording`).

### Recording the audio ourselves is not viable (Route A, rejected)

Call audio runs entirely in native `libandromeda.so` (`com.linecorp.andromeda`). There is
**no Java `AudioRecord`/`AudioTrack`** in the call path (only an unrelated mic-test
self-check). The OS blocks every external capture route. `AudioSource.VOICE_CALL` needs
privileged `CAPTURE_AUDIO_OUTPUT` (blocked since Android 10). `AudioPlaybackCapture` cannot
capture `USAGE_VOICE_COMMUNICATION`. A second `AudioRecord` gets only your own mic. The one
Java-reachable PCM bridge (`com/linecorp/andromeda/jni/AudioJNIImpl`
`nAudioSessionDirectRead(J)[B` / `nAudioSessionDirectWrite(J,[B)`, toggled by
`nAudioControlDirectRead/WriteEnabled`) is **dormant, with zero Java callers** in 26.16.0. Its
"direct read/write **enabled**" naming implies a driver-takeover mode (Java drives the
audio device), not a passive tap. Turning it on can break live call audio.

### How the patch works (Route B)

Recording-start and peer-notify are **separate parallel consumers** of the same
recording-state flow, so cutting the notify leaves the recorder running (device-confirmed).

| Role | Anchor (26.16.0, obfuscated, re-anchor on bump) |
|---|---|
| Start/stop button controller | `dy7/d.java` `m0(kq7.a, iu7.u)` → `((rt7.a) d.i(rt7.a.class)).I()` / `.v()` |
| Start/stop impl | `xt7/r.java` (`PremiumCallSessionExtensionImpl`): `I()`/`v()` set recording flow `f399456g` |
| Record pipeline (native capture + save) | `xt7/r.java` inner class `b` (`nu7.d, vw7.k`), fed by `w0.f399525f` (`eu7.e`) |
| **Peer-notify send (the cut)** | `xt7/e0.java` collector → `xt7/n0.java`: recording → `fVar.L(du7.a.f129545b)` ("record" state), else `O("record")` |
| "record" payload | `du7/a.java` (`getName()="record"`) |
| StateShare serialize + send | `dv7/g.java` `L`/`i0()` → `gq7/d.java` `a(e,str)` → `z30.b.I0(l40.c)` = FeatureShare **SET** over Andromeda session |
| Peer receives → renders alert | inbound SET → `dv7/g.java g0()` → `ju7/g.java` ("record" present) → `ju7/h.java` `iu7.x.c(data, true)` → `FreeCallRecordingProgressViewHolder.updateRecordingProgress` |
| Enable gate | `com/linecorp/voip2/feature/premiumcall/a.java` `d()` = `st7.e.l() != ro7.b.INACTIVE`. Enum `ro7/b` = `{INACTIVE, LAB, OFFICIAL}` |
| "Recordable" capability | `a.a(d, set)` releases the extension unless `set.contains("REC")`. `"STT"` adds transcription. The set comes from server connect info (`hy7/g.java` → `((xo7.b)…).f397475s`) |
| Connected ("picked up") state | `hy7.f.f187605k.f144905s == com.linecorp.andromeda.a.b.CONNECTED`. Recording is set up on connect in `hy7/g.java invokeSuspend()` |

The peer alert exists **only because our client sends the SET**. Not sending it is a
client-side cut, and is patchable. (This corrects the first read that the alert was
unsuppressable. That holds for LINE's own on-screen banner, not for the outbound signal we control.)

**The alert is also a local audio announcement.** On a connected call both devices play a spoken
"開始紀錄"/"結束紀錄" (region files `recording_{tw,jp,th}_{start,end}.m4a`, chosen by SIM country in
`du7/c`). `du7/c` plays them with a **local `MediaPlayer`** on `VOICE_COMMUNICATION`, not mixed
into the call stream. It is driven entirely by the StateShare "record" activity (`du7/a$a` →
`du7/c`, and `du7/c.a()` has no other caller). A plays off its own `L()` registration, and B
plays and shows the banner off the inbound SET. So the single `L()` cut removes both.

### What the patches do

- `callRecordingEnablePatch` (internal) forces the two feature gates true:
  `premiumcall/a.d()` (Labs) and `.e()` (plan). The record button then appears without a paid plan.
  The LINE Labs call recording switch must still be on. See
  [Recording with the LINE Labs switch off](#recording-with-the-line-labs-switch-off-not-fixed).
  The server still grants the `"REC"` capability per call, so a region that withholds it is not
  unlocked. Both `[Call]` patches depend on it.
- `[Call] Auto-record calls`: after the setup call sets up the recording extension in
  `premiumcall/a.a()` (`st7.b.z(st7.b$a)` in 26.16.0, `iv7.b.M(iv7.b$a)` in 26.17.1), calls the start
  method of the recorder (`rt7.a.I()`, `hv7.a.J()`) on the same object. R8 renames all three, so
  the patch finds them by shape. The recorder is the `check-cast` type before the `instance-of`. The
  setup call is the first interface call on the `instance-of` type. The start method is the `()V`
  method of the recorder whose implementation writes `Boolean.TRUE` (stop writes `FALSE`). The setup
  argument register is dead right after the call and carries the switch value.
- `[Call] Record calls without notifying`: skips the "record" send (`dv7.f.L(dv7.a)` in 26.16.0,
  `tw7.f.H(tw7.a)` in 26.17.1, `ww7.f.W()` in 26.18.2) in the notify emitter while the switch is
  on. The emitter (`xt7/n0`, `nv7/n0`, `qv7/o0`) is the one `emit` that casts its value to
  `Boolean` and calls the FeatureShare interface. That interface belongs to the class with the
  texts "modelImpl", "scope" and "sessionModel" (`dv7/g`, `tw7/g`, `ww7/g`). One other class has
  the same texts, but no `Boolean` emitter calls its interfaces. The send is the first call on the
  interface. The stop branch (`O("record")`, `R("record")`, `O()`) is kept. That one send drives both the peer
  banner and the local announcement on both devices, so skipping it removes all of them.

Both patches are on by default, and each switch is on by default. The legal warning is in each
patch description.

Device-confirmed (26.16.0, 2026-11-15):

- With auto-record on, recording starts on connect and the Keep Memo file is complete and playable.
- With silent on, there is no announcement on either device and no banner on the stock peer.
- With both off, the record button is present and manual recording announces as normal.

The capture is independent of the notify and the announcement.

### Recording with the LINE Labs switch off (not fixed)

With the LINE Labs call recording switch off, LINE makes no recording, even with the default
patch set. Settings ▸ Calls ▸ Call recordings says "The call recording feature is disabled". Every
earlier device test had the switch on. A fresh install turns it off. The `[Call] Auto-record
calls` description tells the user to turn the switch on. Measured on 26.17.1 (2026-11-20). The
names below are 26.17.1 names.

- The server is not the blocker. A probe at the start of `premiumcall/a.a(qr7.c, Set)` logged
  `set=[REC]` on a call with the switch off.
- The switch is the `isCallRecordingEnabled` flag of the server-sent `LabConfiguration` data class
  (`dc5/a1`, field `l`, getter `a()`; `ya5/z0` in 26.16.0). Its `toString()` names each flag. The
  Labs state lambda `qv7/i` returns `(c() && w() && !f()) ? LAB : INACTIVE` on `hr7.m` (`rt7/d`).
  `c()` is `LabConfiguration.isEnabled`, `w()` is `isCallRecordingEnabled`, and `f()` is the
  official-release flag of `VoIPConfiguration.premiumCall`.
- `premiumcall/a.d()` is only one reader of that state. The forced `d()` shows the record button,
  but other code reads the state through `iv7.e.k()` (`nv7/a`) or holds the `qv7/i` lambda itself:
  - `xz7/i` creates the recorder (`hv7.a`, impl `nv7/r`) only when `k() != INACTIVE`. With the
    switch off, the call has no recorder and auto-record has nothing to start.
  - The recorder loop `nv7/r.h0` first calls `iv7.e.u()` (`qv7/b`, which holds `qv7/i`). On
    `INACTIVE` it returns false, and the recorder sets `NOT_AVAILABLE` and stops.
  - Other direct readers: `er7/f`, `er7/h`, `vv7/n`, `vv7/p`, `f58/c`, `mj8/x`, `qv7/a`, `qv7/l`.
- Tried and not enough (each one a device test with the switch off, no file saved):
  1. Force `iv7.e.k()` to return `LAB`. The probe showed that the recorder was created, set up
     (`M`) and started (`J`), and `h0` ran. But `h0` stopped before `j0`, because `qv7/b` reads
     `qv7/i`, not `k()`.
  2. Force the `isCallRecordingEnabled` getter true (`dc5/a1.a()`). LINE still made no recording,
     and the settings page still said "disabled". It was not measured why. Either another flag of
     the state is false here, or the value was cached before the patch could change it.
- Next step, if this is picked up again: log `c()`, `w()`, `f()`, the `qv7/i` result and the `u()`
  result during a call with the switch off, then force the value that is wrong. Keep the `nv7/r`
  entry probe to see whether `j0` runs.

**Legal:** covert recording with the consent notice removed is unlawful in two-party-consent
jurisdictions. Each patch description carries a warning.

### Where recordings are saved

LINE keeps every recording in its private storage,
`/data/data/jp.naver.line.android/files/voip/recording/<hash>/[Voice call]<name>_<date>.mp4` (or
`[Video call]…`) (`qu7/h0`, `tq7/c`). The chat's Call recordings list plays this copy. Other apps
cannot read it, and it is lost when LINE is uninstalled or its data is cleared.

After the call, LINE also exports the recording once, without asking:

| Role | Anchor (26.16.0) |
|---|---|
| Sends the automatic export request (`ut7.b$a` "Auto") after the call | `xt7/x0.c()` |
| Export router. Auto uses field `c`, which `<init>` sets to `ut7.a.KEEP_MEMO`. Manual (`ut7.b$b`) carries its own destination | `bu7/a.a(ut7.b)` |
| Destinations still open for a recording (empty after a Keep Memo share) | `xt7/b.q(callId)` |
| Exporter: checks the destination set, then runs DeviceStorage (`nu7.h$a`) or KeepMemo (`nu7.h$b`) | `uu7/f.a(nu7.h)` |
| Device export: MediaStore save through LINE's `te6` saver, type MP4 → `Movies/LINE` | `uu7/d` (`CallRecordingDeviceStorageExporter`) |
| Keep Memo export: refuses files over 1 GiB (`maxFileSizeInBytes=1073741824`) | `ou7/g`, `uu7/g` |

LINE has no setting for the destination. Its own Call recording settings page has three rows: a
switch for Keep Memo shares on unmetered networks only, the video quality, and "Delete all call
recordings". The Keep Memo 1 GiB limit is the reason for the patch. At its 5 Mbps target bit rate,
a 720p video recording reaches 1 GiB after about 27 minutes. The real bit rate is lower and depends
on the picture: a 28-minute video call measured 129 MB.

In `bu7/a.a()`, the Auto branch is
`instance-of vX, p1, ut7/b$a` → `if-eqz` → `iget-object vX, p0, bu7/a.c` → `goto`. While the switch
is on, the patch loads `ut7.a.DEVICE_STORAGE` into `vX` instead of the `iget-object`. `vX` holds only
the dead `instance-of` result there. This is the same path as LINE's own "Save to device" menu item.
The fingerprint finds the router class from `b()`, which reads `UNMETERED` and `CONNECTED` of the
unobfuscated enum `com/linecorp/voip2/feature/recording/upload/b$a`. It finds `ut7.a` from the
constructor's `sget-object …->KEEP_MEMO`.

A probe on 26.16.0 (2026-11-15) forced `DEVICE_STORAGE` for a voice call and a video call. Each
call saved one file: `/sdcard/Movies/LINE/[Voice call]<name>_<date>.mp4` and `[Video call]…mp4`.
The export gave a MediaStore video URI and returned `true`. The exporter allowed set was
`[DEVICE_STORAGE]`.
Nothing went to Keep Memo, and there was no crash. Voice recordings are MP4 files in `Movies`, not
in `Music`.

The "!" icon in the Call recordings list is stock behaviour. An item that was saved to the device
shows "!", with the label "File not shared to Keep Memo, tap to share to Keep Memo or save to
device". The
list (`iu7/d`) treats a device save as complete only when no other destination is open. LINE shows
the same icon after its own manual "Save to device". Tapping it still shares a small file to Keep
Memo.

### Recording time limit

LINE's help pages give the limits: 60 minutes for a voice recording, and 20 or 50 minutes for a
video recording, by quality. All three are fixed numbers in the app, and a timer in LINE's own
process enforces them. The server decides only whether a call can be recorded ("REC"), not for how
long. The recording is saved on the phone, so no upload limit applies while it records.

| Role | Anchor (26.16.0) |
|---|---|
| Limit timer (`PremiumCallRecordingEndByDurationAwaiter`). `emit()` checks each duration update | `au7/d`, `au7/d$d$b.emit()` |
| Voice limit, a fixed 3600000 ms (`const v5, 0x36ee80`) | `au7/d$d$b.emit()` |
| Video limit, passed in as `au7/d$a.a`. A value under 1 means no limit | `xt7/r.q(I)` ← `st7.d.c` from `au7/g` |
| Video limits: 1200000 ms (720p, 5 Mbps) and 3000000 ms (lower quality, 2 Mbps) | `qt7/b$b` |
| Race: the timer against recording off. On a limit it gives `eu7.a.b` ExceedLimit | `xt7/r.r()`, `xt7/z` |
| Stop toast `callrecording_callscreen_toast_recordinglimitstop` | `iu7/v` |

At the target bit rates, both video limits give about 750 MB. That keeps a recording under the
1 GiB limit of Keep Memo. The target is a ceiling. A 28-minute video call measured 129 MB (about
600 kbps), so the real size depends on the picture. Use `[Call] Save recordings to device` for
long recordings. Voice recordings use AAC at 64 kbps, which
is about 29 MB an hour.

In `emit()`, the check starts at `check-cast p1, au7/d$c` (the limit state). While the switch is on,
the patch jumps from there to the method's normal end (`sget-object` of `Unit`, then `return-object`).
The check never emits, so the recording runs until the user stops it or the call ends. The switch
value goes in `v5`, which the check writes with `const` before it reads it. The fingerprint uses
the literal 3600000 and `MediaType.AUDIO` / `AUDIO_VIDEO` (`com.linecorp.andromeda`, not
obfuscated).

A probe on 26.16.0 (2026-11-15) set both limits to 20000 ms. A voice call and a video call each
stopped at 20 seconds with the stop toast, and each file was 20.16 s long. The log showed
`videoDurationInMillis=20000`, then `AUDIO_VIDEO`. So this timer is the gate.

With the patch (26.16.0, 2026-11-15), a 28-minute video call at "Regular" quality recorded in full:
the file in `Movies/LINE` is 28.43 minutes long. The stock limit for it is 20 minutes.

Not tested: transcripts stop at one hour (`callrecording_sttviewer_desc_hourlimit`), and how a
longer recording ends its transcript. Android's MP4 writer (`MediaMuxer`) can stop at 4 GB on
some versions. At the 5 Mbps target that is about 1 h 45 min of 720p video, and longer at real bit
rates.

### Values that drift on a version bump

`premiumcall/a` and the `callrecording_*` strings are stable (package not obfuscated), but the
Route B anchors are obfuscated and drift: `xt7/n0` (notify emitter), `xt7/r` (start/stop impl),
`dy7/d` (button controller), `rt7/a` (`I()`/`v()` interface), `du7/a`/`du7/c` (the "record"
StateShare activity and announcement player), `ro7/b` (enable enum). The save path drifts too:
`bu7/a` (export router), `ut7/a` (destination enum), `xt7/x0`, `uu7/d` and `uu7/f`. The time limit
drifts in `au7/d` (limit timer), `xt7/r.q()` and `qt7/b$b` (video limits). These are 26.16.0 names.
In 26.17.1 the patched sites are `nv7/n0` (notify emitter), `hv7/a` with `iv7/b` (recorder and
extension), `nv7/r` (start/stop impl), `rv7/a` (export router) and `qv7/d$d$b` (limit check).
In 26.18.2 the notify emitter is `qv7/o0` and the FeatureShare interface is `ww7/f`.
Re-anchor them and re-confirm on each LINE bump.

---

## Call ringtone pipeline (investigated, deliberately not shipped)

Freeing the incoming-call ringtone from LINE's four bundled tones / paid Melody Shop tones is
**technically patchable**: the decision is entirely client-side and the playback layer already accepts
an arbitrary `Uri`. Nothing shipped — the reason is product value, not feasibility, see
[Why nothing shipped](#why-nothing-shipped). Recorded so it is not re-derived.

### The choke point

`be7.c.a(Landroid/content/Context;Lb97/c;)Lbe7/a$a;` — `public static`, `smali_classes3/be7/c.smali` —
resolves the ringtone for **both** incoming call types, called from `c97/a.java:119` (free call) and
`c97/k.java:93` (OA call). It returns one of two shapes:

| Shape | Meaning |
|---|---|
| `be7.a$a$a(ue7.a tone, be7.q fallbackFrom)` | a bundled `res/raw` tone |
| **`be7.a$a$b(be7.q, android.net.Uri)`** | **an arbitrary URI** |

The method's own MELODY branch already builds the second from a `file://` path, so URI playback ships
today — it is not theoretical.

### Playback — LINE's own MediaPlayer, in LINE's own process

`hg7.j.a()` puts the resolved tone in the RING slot (`oy.d.f261846b`) → Andromeda
`com.linecorp.andromeda.audio.s` / `t` (which unwraps the `ce7.c` wrapper back to a plain `xx.b`) →
**`xx.c.b()`**, which switches on the URI scheme:

| Scheme | How it is opened |
|---|---|
| `android.resource` | `setDataSource(context, uri)` |
| `file` | `FileInputStream(uri.getPath()).getFD()` |
| **anything else** | `setDataSource(context, uri)` — so `content://` resolves via LINE's own `ContentResolver` |

then `setAudioAttributes(USAGE_NOTIFICATION_RINGTONE)`, `setLooping(true)`, `prepare()`.

This is what makes it patchable: the URI is opened **inside LINE's process**, never handed to the
system NotificationManager, so the cross-process permission problems that sink the Google sign-in
limitation do not apply. (Message *notification* sounds are a separate system — ordinary Android
notification channels, already customisable in system Settings.)

### What actually limits users today

`be7.r$a.a()` picks the tone provider through **two sequential gates**. First a server-pushed config
int, `k97.m.m()` → `vb7.c.m()` → `t().G0().l().e()`:

| `m()` | Provider |
|---|---|
| `0`, or anything other than `1`/`2` | `DEFAULT` |
| `1` | `EMBEDDED` |
| `2` | fall through to the region switch below |

Only when it is `2` does region matter (`s87.i.a()`), and the premium branches additionally require
`a.b()` — all three of `k97.m.s()` / `.f()` / `.r()` non-empty, that is the premium-service URLs are
configured:

| Region | Provider | Ringtone setting visible? |
|---|---|---|
| JP | `MUSIC` | yes |
| TH | `FRIEND_MELODY` if `b()`, else `EMBEDDED` | only for `FRIEND_MELODY` |
| TW | `MELODY` if `b()`, else `EMBEDDED` | only for `MELODY` |
| everywhere else | `DEFAULT` | **no** |

Only MUSIC / MELODY / FRIEND_MELODY set `exposeExternalSetting = true`, which is what makes the
"Ringtones & ringback tones" entry appear. `DEFAULT` hardcodes `ue7.a.RING_1`. `EMBEDDED` resolves
through `ge7.c.e(m87.h.RING)`, reading the *legacy* melody prefs and falling back to `RING_1` when
empty. **So outside JP/TH/TW there is no ringtone choice in the UI at all** — not even among the four
bundled tones, reachable only under JP's `MUSIC` provider (it alone reads the `cf7.m` prefs). The
config int is a server-supplied *value* consumed locally, not a server-made *decision* — same shape as
the photo tier.

Two per-call overrides sit upstream of the local choice: the **caller's** friend-melody (`m87.e` on
`VoIPFreeCallIncomingConnectInfo`) preempts it, and a per-call boolean
(`VoIPFreeCallIncomingConnectInfo.t` / `VoIPOaCallIncomingConnectInfo.v`) demotes any URI-based tone
back to `RING_1`. Head-injecting `be7.c.a` sits ahead of all of this.

### Tone inventory and storage

`ue7.a` is the bundled-tone enum. Ids take the form `android.resource:///<id>`.

| Constant | `res/raw` | Title |
|---|---|---|
| `RING_1` | `original` | Xylophone (`settings_ringtone_default1`) |
| `RING_2` | `melody` | Spring |
| `RING_3` | `voice` | LINE |
| `RING_4` | `ring` | Telephone ring |
| `RINGBACK_1` | `lineapp_ringback_16k` | — |

Two parallel storage systems, both **encrypted**, and so not readable from an extension:

- **Current** — `jp.naver.voip.ringtone` via `cf7.m` (keys `ringToneUri`, `ringToneName`,
  `ringToneResourceTypeId`, `ringToneDecodedUriFlag`; accessors `cf7.p.a` / `cf7.p.b`). Built by
  `gz6.a.b`, which is `EncryptedSharedPreferences` (androidx.security `kd.b`, Android-keystore
  backed). Read only by the `MUSIC` provider.
- **Legacy LINE MELODY** — `com.linecorp.voip.melody` via `qe7.a` (keys `ringtone_path`,
  `ringtone_title`, `ringtone_oid`, …), synced from the Talk server by `ge7.c.g()`
  (`getRingToneOnTalkServer`), which **resets to default and deletes the local files** when the
  server reports no tone.

**File-playback precedent.** The LINE MUSIC path plays a plain file from LINE's *private internal*
dir — `cf7.k.b()` → `<applicationInfo.dataDir>/ringtone/decoded_ringtone_1|2` (`j48.h.d()` is
`applicationInfo.dataDir`, not external storage) — so a file in LINE's private storage plays with
**zero runtime permissions**. Purchased-track entitlement re-verifies server-side every 14 days
(`af7.g`, pref `ringToneMusicVerifyLastTime`).

### If it is ever built

Head-inject `be7.c.a` and return `be7.a$a$b(q, uri)` early when the extension supplies a URI, else
fall through. Fingerprint on anchors obfuscation cannot touch:

```kotlin
accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC)
filters = listOf(
    instanceOf("Lcom/linecorp/voip2/access/connect/VoIPFreeCallIncomingConnectInfo;"),
    instanceOf("Lcom/linecorp/voip2/access/connect/VoIPOaCallIncomingConnectInfo;"),
    methodCall(definingClass = "Landroid/net/Uri;", name = "parse"),
)
```

Uniqueness verified against 26.14.0: only three methods in the APK carry **both** `instance-of`s —
`be7.c.a` (the target), `be7.q$d.e` (excluded by `STATIC`; it is `public final`) and
`c97.m.a(Landroid/content/Context;Lb97/c;)Z` (excluded by `Uri.parse`). Filters are in program order:
both `instance-of`s precede the MELODY branch's `Uri.parse`.

Read `be7/a$a$b` and `be7/q` from the matched method's **own** MELODY branch rather than hardcoding
them — the idiom the dropped photo patch used for `u13.c1`: the `INVOKE_DIRECT` whose `name == "<init>"`
and whose `parameterTypes` end in `Landroid/net/Uri;` gives `a$a$b` as its `definingClass` and `q` as
`parameterTypes[0]`; the `SGET_OBJECT` whose field type is that `q` gives a valid enum constant. The
method is static, `.locals 5`, two params (v0–v6, `p0` = v5), so `invoke-direct`/`new-instance` stay
inside the 4-bit operand limit and v0–v2 are dead on entry.

**Any custom URI must be probed before use.** LINE declares `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`,
`READ_MEDIA_VISUAL_USER_SELECTED` and legacy `READ_EXTERNAL_STORAGE` but **not `READ_MEDIA_AUDIO`**, so
a user-added MediaStore ringtone throws `SecurityException` on open, `xx.c` swallows it and calls `c()`,
and **the call rings silently**. Probe with `ContentResolver.openAssetFileDescriptor(uri, "r")` and
return `null` on failure so LINE keeps its own tone. Stock OEM ringtones
(`content://media/internal/audio/…`, `content://settings/system/ringtone`) open with no permission.

### Why nothing shipped

- **Follow the device ringtone** (`RingtoneManager.getActualDefaultRingtoneUri`) is one small patch,
  but nobody asked for it and it silently ignores the tone of anyone whose phone ringtone is a file
  they imported themselves (the `READ_MEDIA_AUDIO` note above).
- **A real in-app picker** needs a new Activity, an `AndroidManifest.xml` patch, SAF plus a copy into
  LINE's private dir, and a settings-row injection into obfuscated declarative Kotlin (`l15.c`'s
  `m55.b0` item list) whose host rows only exist in JP/TH/TW. Disproportionate to the payoff.

### Values that drift on a version bump

| Thing | 26.14.0 |
|---|---|
| resolver | `be7.c.a`; result types `be7.a$a$a` / `be7.a$a$b` |
| provider / region gate | `be7.q` (provider), `be7.r` (+ `exposeExternalSetting`), config via `k97.m.m()` |
| bundled tones | `ue7.a`; kinds `ue7.b` (RING / RING_BACK), sources `ue7.c` (BASIC / MUSIC) |
| prefs & files | `cf7.m` / `cf7.p` / `cf7.k`; legacy `qe7.a`; server sync `ge7.c` |
| player | `xx.c.b()`; sources `xx.b` / `xx.g`; call param `oy.d`; adapter `hg7.j` |
| **stable anchors** | `com.linecorp.voip2.access.connect.VoIPFreeCallIncomingConnectInfo` / `…VoIPOaCallIncomingConnectInfo` — **not** obfuscated |

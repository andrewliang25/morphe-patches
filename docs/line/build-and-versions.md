# LINE build and supported versions

The supported LINE versions, the anchors that survive a version bump, and the offline build and apply recipe. See [README.md](README.md) for the other LINE docs.

## Supported versions (26.16.0, 26.17.1 and 26.18.2)

26.16.0 needs Android 11 (API 30), so it is the version for Android 11 and 12. 26.17.1 needs
Android 12L (API 32), and so does 26.18.2. The versions cover the same phones that 26.14.0, 26.15.1 and 26.16.0
covered, so the bundle dropped 26.14.0 and 26.15.1 with the 26.17.1 bump. Each target in
`Constants.kt` (`COMPATIBILITY_LINE.targets`, ascending order) pins the version, its `versionCode`
and its `minSdk`. All splits of a LINE bundle share one `versionCode`, so the single-`Int` form of
`AppTarget` applies it to every ABI.

| Version | versionCode | minSdk |
|---|---|---|
| 26.16.0 | 261600106 | 30 |
| 26.17.1 | 261710173 | 32 |
| 26.18.2 | 261820153 | 32 |

Each fingerprint must match every version, so no patch has a table of names per version. Each
anchor is a name that R8 keeps:

- String literals, Kotlin `toString` text and Thrift operation names.
- Kotlin enum-constant names, read with `sget-object`.
- Resource names. Get the id at patch time with `resourceId(type, name)` in
  `shared/ResourceIds.kt`. Put it in a lazy filter, `literal({ resourceId("string", "...") })`, and
  add `resourceIdsPatch` to `dependsOn`.
- Method shape: return type, parameter count, and instruction order.
- Classes that LINE does not obfuscate (`ChatHistoryMenuFragment`, `UnsendMessageLdsDialog`,
  `HeaderButton`).

### Shared helpers

| Helper | What it finds | Used by |
|---|---|---|
| `shared/ResourceIds.kt` | A resource id from its name in `res/values/public.xml` | Nine patches |
| `line/shared/HomeState.kt` | The Home state class from its `PageData(modules=` text, the element type from the generic signature of its list field, and the module field as the one field whose type has `getType()` | Hide Home modules, Hide Home content feed, Disable LINE Premium, Hide calendar buttons |
| `line/shared/AttachTile.kt` | The availability check of a "+" menu tile: the override of the one abstract `Z` method of the tile base class that takes one argument | Hide LINE GIFT, Hide Transfer, Hide calendar buttons |

`HomeState.addModuleFilter` adds one `filterName(List)List` method and one branchless call at the
top of the state constructor. A filter with a switch returns the input list while the switch is off.

### How to check a bump

A clean apply does not prove that each site changed. Nine sites of *Hide new item badges* are
best-effort (`methodOrNull`), so a drifted anchor skips its site with no error. Compare the patched
dex with the original dex method by method:

1. List the methods that each version adds or changes.
2. Pair the two lists by role. Each version must change the same number of methods.
3. On the old version, compare the list with the list of the released bundle. It must be the same.
4. Run `BranchSweep` on each patched dex.
5. Read each site that reuses a register on the new version. The register must be dead there.

On the 26.18.2 bump, 26.17.1 and 26.18.2 each changed 105 LINE methods, and 26.16.0 changed 106. On
26.16.0 and 26.17.1 the changed methods and their edits were the same as those of the released
v3.13.0-dev.3 bundle. The traps from earlier bumps are in the `app-bump` skill.

---

## Local build & verify without GitHub Packages credentials

Building normally needs a PAT for `maven.pkg.github.com/MorpheApp/registry`. If the artifacts are
already in the Gradle cache you can work **fully offline** — the `app.morphe.patches` settings
plugin only needs the credential values to be *non-null*, not valid, when nothing is fetched:

```bash
# Compile + build the bundle offline (dummy creds satisfy the non-null credential block)
rm -rf patches/build/libs
./gradlew :patches:buildAndroid --offline --no-daemon -Pgpr.user=dummy -Pgpr.key=dummy

# List patches in the built bundle (confirm name/description/registration)
java -jar work/morphe-desktop-*.jar list-patches --patches patches/build/libs/patches-*.mpp

# Apply ONE patch exclusively against the real APK (fingerprints resolve here, not at build time)
java -jar work/morphe-desktop-*.jar patch \
  -p patches/build/libs/patches-*.mpp \
  --exclusive -e "<patch name>" \
  -o work/out.apk -t "$TMPDIR/scratch" \
  work/apkm-extract/base.apk
```

The apply log's `Writing N new classes` is **the number of classes your patch modified** — a fast
sanity check (Calendar touches 5, attach-tools 1).

**Disassembling the result.** No `baksmali` CLI ships: the Morphe/apktool jars carry only the smali
*assembler* and a shrunk `baksmali` library. `apktool d` works but is slow. Fast path — STRIP_FAST
(the default) writes every modified class into a fresh small `classes.dex`, so unzip just that and
read it with **dexlib2** (Gradle cache jar `smali-dexlib2-*.jar`):

```java
// javac -cp smali-dexlib2-*.jar:smali-util-*.jar Dump.java && java -cp .:...:... Dump
var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(),
              new BufferedInputStream(new FileInputStream("classes.dex")));
for (ClassDef c : dex.getClasses())
    for (Method m : c.getMethods()) { /* inspect m.getImplementation().getInstructions() */ }
```

Cast instructions to `OneRegisterInstruction` / `TwoRegisterInstruction` /
`NarrowLiteralInstruction` / `ReferenceInstruction` to print registers, literals, and field/method
references.

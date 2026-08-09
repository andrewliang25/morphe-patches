package app.andrewliang.patches.line.originalphoto

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** `20971520` — the source-size gate in `t73.k0.b0`. */
internal const val SIZE_GATE = 0x1400000L

/** `100000000` — the source-pixel gate in `t73.k0.b0`. */
internal const val PIXEL_GATE = 0x5F5E100L

private const val CONTEXT = "Landroid/content/Context;"
private const val URI = "Landroid/net/Uri;"
private const val INTEGER = "Ljava/lang/Integer;"
private const val BITMAP = "Landroid/graphics/Bitmap;"
private const val FILE = "Ljava/io/File;"

private const val EXTENSION = "Lapp/andrewliang/extension/OriginalPhoto;"
private const val WRITE_BOUNDED =
    "writeBounded($CONTEXT$URI$INTEGER$FILE)Ljava/lang/Boolean;"

@Suppress("unused")
val originalPhotoPatch = bytecodePatch(
    name = "Send original photos without the quality drop",
    description = "Stops LINE from silently shrinking photos to a few megapixels when " +
        "\"Original\" is selected but the photo is over 20 MB or 100 megapixels. They keep " +
        "their full resolution instead, up to 24 megapixels. Photos that already send as " +
        "originals are untouched, and sending with \"Original\" off is unchanged.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // With "Original" on, t73.k0.b0 clears rt7.c.isOriginal for anything >= 20 MB or >= 100 MP.
    // The photo then takes the ordinary standard path and is resampled to the tier's pixel budget
    // -- 4.19 MP on High, 1.64 MP on Normal. One byte under the threshold it is copied verbatim;
    // one byte over it loses 6-25x its pixels, with the toggle still showing on. LINE has no tier
    // in between, which is the entire defect.
    //
    // Both gates are plain literals, so neutralising them keeps every such photo on the *original*
    // path (Site 1). There the extension re-encodes it at native resolution, bounded to 24 MP,
    // instead of raw-copying something that would sit at the upload size limit (Site 2). Site 3
    // pins the quality LINE's own original-path re-encode uses so it matches the extension.
    //
    // Nothing outside the original path is touched: the tier config (t88.a$b$a / dw0.c.a) and the
    // standard path's own decode budget are deliberately left alone, so sending with "Original"
    // off costs exactly what it costs today.
    execute {
        // --- Site 1: stop the two bail-outs in t73.k0.b0 -------------------------------------
        // Both are const-wide/32 feeding a cmp-long. Rewriting the compared value rather than the
        // branch keeps the method's control flow byte-identical:
        //   size  gate: `if-ltz` -> always falls through to the pixel check
        //   pixel gate: `if-gez` -> always reaches `move v4, v5`, so isOriginal stays true
        // The edited-photo check earlier in the loop, and the whole "Original off" branch, are
        // untouched.
        val gateMethod = OriginalPhotoGateFingerprint.method
        OriginalPhotoGateFingerprint.instructionMatches.forEach { match ->
            val register = (match.instruction as OneRegisterInstruction).registerA
            gateMethod.replaceInstruction(match.index, "const-wide/32 v$register, 0x7fffffff")
        }

        // --- Sites 2 and 3 both live in the u13.y0 lambda ------------------------------------
        val writer = OriginalFileWriterFingerprint.method
        val instructions = writer.implementation!!.instructions.toList()

        // u13.c1 is obfuscated, but its Context field is not: the only Context-typed instance read
        // in this lambda is `c1.a`, which gives us both the class descriptor and the field name.
        val contextRead = instructions.firstNotNullOfOrNull { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            if (instruction.opcode == Opcode.IGET_OBJECT && reference?.type == CONTEXT) {
                reference
            } else {
                null
            }
        } ?: throw PatchException("original photo: Context read not found in ${writer.definingClass}")

        // The lambda's own synthetic captures: the c1 it was built from, the source Uri, and the
        // rotation the caller supplied.
        fun capturedField(type: String) = instructions.firstNotNullOfOrNull { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            if (instruction.opcode == Opcode.IGET_OBJECT &&
                reference?.definingClass == writer.definingClass &&
                reference.type == type
            ) {
                reference
            } else {
                null
            }
        }

        val outerField = capturedField(contextRead.definingClass)
            ?: throw PatchException("original photo: captured c1 not found in ${writer.definingClass}")
        val uriField = capturedField(URI)
            ?: throw PatchException("original photo: captured Uri not found in ${writer.definingClass}")

        // c1.f hands the same rotation to c1.p -- which writes the standard variant the thumbnail
        // and OBS's /preview derive from -- and to this lambda. Both prefer it over the file's EXIF
        // (c1.d) and only read EXIF when it is null, so the extension has to see it: re-deriving
        // from EXIF alone would leave the original sideways relative to the standard variant
        // whenever the caller supplied a rotation the file itself does not carry.
        val rotationField = capturedField(INTEGER)
            ?: throw PatchException(
                "original photo: captured rotation not found in ${writer.definingClass}",
            )

        // --- Site 3 first: replaceInstruction keeps indices stable, addInstructions does not ---
        // `c1.o(quality, bitmap, file)` is the JPEG encoder. Its quality argument is read from the
        // tier config (dw0.b$b.quality = 70 Normal / 80 High) a couple of instructions above. This
        // read sits *inside* the lambda, so pinning it governs only original-path re-encodes --
        // after Site 2 that means exactly the sub-20 MB HEIC/WEBP case. The standard path reads
        // quality at its own site in c1.p and is unaffected.
        val encodeIndex = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_STATIC &&
                reference?.returnType == "Z" &&
                reference.parameterTypes.toList() == listOf("I", BITMAP, FILE)
        }
        if (encodeIndex < 0) {
            throw PatchException("original photo: JPEG encode call not found in ${writer.definingClass}")
        }

        val qualityRegister = (instructions[encodeIndex] as FiveRegisterInstruction).registerC
        val qualityReadIndex = (encodeIndex - 1 downTo 0).firstOrNull { index ->
            instructions[index].opcode == Opcode.IGET &&
                (instructions[index] as TwoRegisterInstruction).registerA == qualityRegister
        } ?: throw PatchException("original photo: quality read not found in ${writer.definingClass}")

        writer.replaceInstruction(qualityReadIndex, "const/16 v$qualityRegister, 0x50")

        // --- Site 2: hand oversized photos to the extension before the mime-type branch --------
        // The lambda otherwise raw-copies jpeg/png/gif/bmp, which for a >= 20 MB source would put
        // a file at the upload size limit on the wire. The extension re-derives the same
        // ">= 20 MB || >= 100 MP" test the patch just removed and returns null for anything else,
        // so every case that works today falls through to the stock code untouched -- notably a
        // 50 MP / 10 MB JPEG, which must keep being copied byte for byte.
        //
        // Injected after `check-cast p1, File` and LINE's null check, so p1 is already typed. v0,
        // v1 and v2 are dead on entry: the original code's next acts are `const-string v0,
        // "content"`, a reload of the Uri capture into v1, and `move-result-object v2` from
        // `Uri.getScheme` -- each a write before any read.
        //
        // The fall-through target MUST be an ExternalLabel bound to the instruction already at the
        // injection index, never a label written inside the block. A label declared in the block is
        // resolved against the block's own addresses and is not rebased to where the block lands,
        // so at any non-zero index the branch ends up pointing into the middle of an earlier
        // instruction -- ART then refuses the whole class with
        // `VerifyError: target dex pc <n> is not at instruction start`. (`hideadviews` gets away
        // with an in-block label only because it injects at index 0, where the two addressings
        // coincide.) Device-confirmed on LINE 26.11.0: the in-block form crashed every original
        // send with `target dex pc 0xf`, 0xf being the block-relative address of its own label.
        val fallThrough = writer.getInstruction(2)
        writer.addInstructionsWithLabels(
            2,
            """
                iget-object v0, p0, ${outerField.definingClass}->${outerField.name}:${outerField.type}
                iget-object v0, v0, ${contextRead.definingClass}->${contextRead.name}:${contextRead.type}
                iget-object v1, p0, ${uriField.definingClass}->${uriField.name}:${uriField.type}
                iget-object v2, p0, ${rotationField.definingClass}->${rotationField.name}:${rotationField.type}
                invoke-static { v0, v1, v2, p1 }, $EXTENSION->$WRITE_BOUNDED
                move-result-object v0
                if-eqz v0, :originalphoto_stock
                return-object v0
            """,
            ExternalLabel("originalphoto_stock", fallThrough),
        )
    }
}

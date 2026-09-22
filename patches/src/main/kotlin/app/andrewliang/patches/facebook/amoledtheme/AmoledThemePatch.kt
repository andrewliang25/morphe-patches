package app.andrewliang.patches.facebook.amoledtheme

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

/** The largest value a channel can have and still count as a background. */
private const val MAX_CHANNEL = 0x2A

/**
 * The largest difference between the channels of a background. A grey has almost none. A dark green
 * or dark brown banner has much more, and it keeps its colour.
 */
private const val MAX_SPREAD = 8

/** Opaque black, as the signed int that a colour holds. */
private const val BLACK = -0x1000000

/** Gets the resolved colour and its token. Gives the colour to draw. */
private const val APPLY = "Lapp/andrewliang/extension/AmoledTheme;->apply(ILjava/lang/Object;)I"

/**
 * True for a dark grey, which is what a background uses. False for a dark colour with a hue, which
 * is a banner and keeps its colour. The extension holds the same rule for the colours it gets.
 */
private fun isDarkNeutral(red: Int, green: Int, blue: Int): Boolean {
    val high = maxOf(red, green, blue)
    return high <= MAX_CHANNEL && high - minOf(red, green, blue) <= MAX_SPREAD
}

/**
 * A colour reaches the screen by three routes, and this patch covers all three with one rule.
 *
 * The first route is a resolver: a component asks the design system, and the bytecode half hooks
 * the four methods that answer. The second is a resource: a view reads a colour by id, so no int
 * passes a hook, and the resource half below rewrites it. The third is a literal written in code,
 * which the bytecode half rewrites in place.
 *
 * Route two and route three match on the **value** alone, with no class, method or resource name.
 * Facebook strips resource names and renames its classes about every two weeks, so a name is not an
 * anchor here. A value of this exact shape is a colour and nothing else.
 */
private val amoledThemeResourcePatch = resourcePatch {
    execute {
        var changed = 0

        listOf("res/values/colors.xml", "res/values-night/colors.xml")
            .filter { get(it, false).exists() }
            .forEach { path ->
                document(path).use { document ->
                    val colors = document.getElementsByTagName("color")
                    for (index in 0 until colors.length) {
                        val color = colors.item(index) as? Element ?: continue
                        if (!isDarkBackground(color.textContent)) continue
                        color.textContent = "#ff000000"
                        changed++
                    }
                }
            }

        check(changed > 0) { "No dark colour resource found, so the theme would stay grey" }
    }
}

/**
 * True when this resource value is an opaque dark grey. A reference such as `@color/foo` gives
 * false, and so does a translucent value, which is a scrim and not a background.
 */
private fun isDarkBackground(value: String): Boolean {
    val hex = value.trim().removePrefix("#")
    if (hex.length !in setOf(3, 4, 6, 8)) return false
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return false

    // Widen #rgb and #argb to two digits for each channel.
    val wide = if (hex.length <= 4) hex.map { "$it$it" }.joinToString("") else hex
    if (wide.length == 8 && wide.take(2).toInt(16) != 0xFF) return false

    val (red, green, blue) = wide.takeLast(6).chunked(2).map { it.toInt(16) }
    return isDarkNeutral(red, green, blue)
}

@Suppress("unused")
val amoledThemePatch = bytecodePatch(
    name = "[General] AMOLED black theme",
    description = "Makes the dark mode of Facebook black instead of dark grey, which saves power " +
        "on an OLED screen. Turn on dark mode in Facebook first. Dividers, borders, text, icons " +
        "and the coloured banners do not change.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    dependsOn(amoledThemeResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        // Route one. Four methods and six returns. Each hook keeps the body of the method and
        // sends the value through the extension before the method returns it.
        DarkSchemeResolveFingerprint.method.hookColorReturns(tokenParameterIndex = 0)
        FdsColorsComponentFingerprint.method.hookColorReturns(tokenParameterIndex = 0)
        FdsColorsContextFingerprint.method.hookColorReturns(tokenParameterIndex = 1)

        // The resolver of the view code has a Redex name, thus its descriptor comes from the
        // wrapper that calls it.
        val resolverCall = FdsSchemeResolveFingerprint.instructionMatches[1].instruction
        val resolver = (resolverCall as ReferenceInstruction).reference as MethodReference

        // If Facebook moves the resolver into the wrapper, this reaches 1 caller and not 894.
        check(resolver.definingClass.toString() != FDS_COLOR_SCHEME) {
            "The FDS colour resolver is now inside FdsColorScheme. Find the seam again."
        }

        mutableClassDefBy(resolver.definingClass.toString()).methods.single {
            it.name == resolver.name &&
                it.returnType == "I" &&
                it.parameterTypes.map(CharSequence::toString) ==
                resolver.parameterTypes.map(CharSequence::toString)
        }.hookColorReturns(tokenParameterIndex = 1)

        // Route three. The palette tables, the top bar of the feed, the system bars and each Litho
        // component that draws its own chrome all write a colour instead of asking for one, so no
        // resolver and no resource reaches them. The sweep reads every class and rewrites only the
        // classes that hold one, which takes about 30 seconds.
        val owners = mutableSetOf<String>()
        classDefForEach { classDef ->
            if (classDef.methods.any { it.hasDarkColor() }) owners += classDef.type
        }

        val rewritten = owners.sumOf { type ->
            mutableClassDefByOrNull(type)?.methods?.sumOf { it.blackenDarkColors() } ?: 0
        }
        check(rewritten > 0) { "No dark colour written in code, so the chrome would stay grey" }
    }
}

/** True when this instruction writes an opaque dark grey. */
private fun Instruction.isDarkColor(): Boolean {
    if (this !is NarrowLiteralInstruction || this !is OneRegisterInstruction) return false
    if (narrowLiteral == BLACK || (narrowLiteral ushr 24) != 0xFF) return false
    return isDarkNeutral(
        (narrowLiteral shr 16) and 0xFF,
        (narrowLiteral shr 8) and 0xFF,
        narrowLiteral and 0xFF,
    )
}

/** True when this method writes a dark grey. It only reads, thus it needs no proxy of the class. */
private fun Method.hasDarkColor(): Boolean =
    implementation?.instructions?.any { it.isDarkColor() } == true

/** Replaces each dark grey that this method writes with black. Answers how many it replaced. */
private fun MutableMethod.blackenDarkColors(): Int {
    val sites = (implementation ?: return 0).instructions.withIndex()
        .filter { it.value.isDarkColor() }
        .map { it.index to (it.value as OneRegisterInstruction).registerA }

    sites.asReversed().forEach { (index, register) ->
        replaceInstruction(index, "const v$register, $BLACK")
    }
    return sites.size
}

/**
 * Sends each `int` that this method returns through the extension, with the colour token in
 * parameter [tokenParameterIndex]. The first declared parameter is index 0.
 *
 * The hooks go in from last to first, because an insert moves every later index.
 *
 * Both registers get a test. `invoke-static` has 4-bit operands, thus a register above v15
 * assembles into something else and the patch applies but does nothing.
 *
 * A `return` must not be a branch target. The new instructions go in before it, thus a jump onto
 * the `return` would miss them and lose a colour without an error.
 */
private fun MutableMethod.hookColorReturns(tokenParameterIndex: Int) {
    val implementation = checkNotNull(implementation) { "$definingClass->$name has no body" }
    val instructions = implementation.instructions.toList()

    val addresses = instructions.runningFold(0) { address, it -> address + it.codeUnits }
    val indexOfAddress = addresses.withIndex().associate { (index, address) -> address to index }
    val branchTargets = instructions.withIndex().mapNotNull { (index, instruction) ->
        if (instruction is OffsetInstruction) {
            indexOfAddress[addresses[index] + instruction.codeOffset]
        } else {
            null
        }
    }.toSet()

    val returns = instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN }
        .map { it.index to (it.value as OneRegisterInstruction).registerA }

    check(returns.isNotEmpty()) { "$definingClass->$name returns no int to recolour" }

    val parameterRegisters = parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
    val tokenRegister = implementation.registerCount - parameterRegisters + tokenParameterIndex

    check(tokenRegister < 16) {
        "$definingClass->$name: token register v$tokenRegister is out of invoke-static range"
    }

    returns.asReversed().forEach { (index, register) ->
        check(register < 16) {
            "$definingClass->$name: return register v$register is out of invoke-static range"
        }
        check(index !in branchTargets) {
            "$definingClass->$name: the return at index $index is a branch target"
        }
        addInstructions(
            index,
            """
                invoke-static { v$register, v$tokenRegister }, $APPLY
                move-result v$register
            """,
        )
    }
}

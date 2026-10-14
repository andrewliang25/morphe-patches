package app.andrewliang.patches.line.shared

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Removes the "+" attach-menu tile [tileClass]. Each built-in tile extends one obfuscated base
 * class, and the attach-menu filter shows a tile only if the tile's availability predicate
 * returns true. That predicate overrides the one abstract `Z` method of the base class that takes
 * one argument (the other abstract method is `()V`). This makes the override return false while
 * the switch [feature] is on. The switch uses p0: each override writes it before it reads it.
 */
internal fun BytecodePatchContext.hideAttachTile(tileClass: String, feature: String) {
    val tile = mutableClassDefBy(tileClass)
    val predicate = classDefBy(tile.superclass!!).methods.single {
        AccessFlags.ABSTRACT.isSet(it.accessFlags) &&
            it.returnType == "Z" && it.parameterTypes.size == 1
    }
    tile.methods.single {
        it.name == predicate.name &&
            it.parameterTypes.map(CharSequence::toString) ==
            predicate.parameterTypes.map(CharSequence::toString)
    }.returnFalseIfLineSetting(feature, "p0")
}

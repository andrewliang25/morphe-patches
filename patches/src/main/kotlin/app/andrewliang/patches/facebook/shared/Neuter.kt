package app.andrewliang.patches.facebook.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext

/**
 * Make every void method on [classDescriptor] return immediately, and report how many changed.
 * Constructors are left alone so instances still initialise their fields.
 *
 * Facebook's ad schedulers and trackers keep their real class names through Redex while their
 * method names do not, so methods are selected by shape rather than by a name that is reassigned
 * every release. Returns 0 when the class is absent.
 */
internal fun BytecodePatchContext.neuterVoidMethods(classDescriptor: String): Int {
    val classDef = mutableClassDefByOrNull(classDescriptor) ?: return 0

    return classDef.methods.count { method ->
        val neuterable = method.returnType == "V" &&
            method.name != "<init>" &&
            method.name != "<clinit>" &&
            method.implementation != null

        if (neuterable) method.addInstructions(0, "return-void")
        neuterable
    }
}

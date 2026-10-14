package app.andrewliang.patches.line.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/**
 * `toString` of the Home Compose UI state (`PageData`). The state holds the module list that
 * the Home tab shows, in field `a`, the first constructor argument. Every feed build path goes
 * to its constructor: the feed assemblers, and the state copies. One filter on the list argument
 * there thus covers the whole feed.
 *
 * R8 renames the state class and the module types on each LINE version, but it keeps the
 * Kotlin `toString` text. [resolveHomeState] gets every other name from this class.
 */
internal object HomePageDataToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("PageData(modules=")),
)

/** The Home state names on the LINE version that is patched. See [resolveHomeState]. */
internal class HomeState(
    /** The state class, `PageData`. */
    val stateClass: String,
    /** The one constructor of [stateClass]. p1 is the module list. */
    val constructor: MutableMethod,
    /** The type of one list element. */
    val element: String,
    /** The field of [element] that holds the typed module. */
    val moduleField: String,
    /** The module interface. Its `getType()` gives the module type string. */
    val module: String,
) {
    /**
     * Smali that reads the type string of the list element in [elementRegister] into
     * [typeRegister]. It casts [elementRegister] to [element].
     */
    fun readModuleType(elementRegister: String, typeRegister: String) = """
        check-cast $elementRegister, $element
        iget-object $typeRegister, $elementRegister, $element->$moduleField:$module
        invoke-interface {$typeRegister}, $module->getType()Ljava/lang/String;
        move-result-object $typeRegister
    """

    /**
     * Adds the static method `filterName(List)List` to [stateClass], and calls it at the top of
     * [constructor] to replace the module list (p1) before the constructor stores it.
     *
     * The filter keeps each element for which [hideCheck] leaves 0 in v3. [hideCheck] starts with
     * the module type string in v3, and it can use v4 and v5. The loop lives in a new method,
     * because a loop with a backward branch, injected inline, corrupts the layout of an existing
     * method, and ART then throws a VerifyError. The call at the top of the constructor has no
     * branch (invoke + move-result) and reuses p1.
     *
     * With [feature], the filter runs only while that switch is on. With the switch off, the
     * method returns the input list.
     *
     * Several patches add their own filter to the same constructor. Each is a pure
     * `List -> List` filter on p1, so the order does not matter.
     */
    fun BytecodePatchContext.addModuleFilter(
        filterName: String,
        hideCheck: String,
        feature: String? = null,
    ) {
        val filter = MutableMethod(
            ImmutableMethod(
                stateClass,
                filterName,
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(7),
            ),
        )
        mutableClassDefBy(stateClass).methods.add(filter)
        // p0 = input List. v0 = switch, then result ArrayList, v1 = iterator, v2 = element,
        // v3 = type/bool.
        val switch = if (feature == null) "" else readLineSetting(feature, "v0") + """
            if-nez v0, :filter
            return-object p0
            :filter
        """
        filter.addInstructions(
            0,
            switch + """
                new-instance v0, Ljava/util/ArrayList;
                invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
                invoke-interface {p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                move-result-object v1
                :loop
                invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
                move-result v2
                if-eqz v2, :done
                invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v2
            """ + readModuleType("v2", "v3") + hideCheck + """
                if-nez v3, :loop
                invoke-virtual {v0, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                goto :loop
                :done
                return-object v0
            """,
        )
        constructor.addInstructions(
            0,
            """
                invoke-static {p1}, $stateClass->$filterName(Ljava/util/List;)Ljava/util/List;
                move-result-object p1
            """,
        )
    }
}

/**
 * Finds the Home state names without one obfuscated name: the state class from its `toString`,
 * the element type from the generic signature of the list field `a`, and the module field as the
 * one element field whose type declares `getType()`.
 *
 * Call it before the first edit of a patch, because the patcher does not undo a partial execute.
 */
internal fun BytecodePatchContext.resolveHomeState(): HomeState {
    val stateClass = HomePageDataToStringFingerprint.method.definingClass
    val state = mutableClassDefBy(stateClass)
    val constructor = state.methods.single {
        it.name == "<init>" && it.parameterTypes.firstOrNull() == "Ljava/util/List;"
    }

    val listSignature = state.fields.single { it.name == "a" }.annotations
        .single { it.type == "Ldalvik/annotation/Signature;" }
        .elements.single { it.name == "value" }.value as ArrayEncodedValue
    val element = listSignature.value.joinToString("") { (it as StringEncodedValue).value }
        .substringAfter('<').substringBefore('>')

    val moduleField = classDefBy(element).fields.singleOrNull { field ->
        AccessFlags.STATIC.value and field.accessFlags == 0 &&
            classDefByOrNull(field.type)?.methods?.any {
                it.name == "getType" && it.returnType == "Ljava/lang/String;"
            } == true
    } ?: throw PatchException("No module field in the Home element $element")

    return HomeState(stateClass, constructor, element, moduleField.name, moduleField.type)
}

package app.andrewliang.patches.facebook.hidesponsoredreels

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val GRAPHQL_STORY = "Lcom/facebook/graphql/model/GraphQLStory;"
private const val COLLECTION = "Ljava/util/Collection;"
private const val LIST = "Ljava/util/List;"

private const val FILTER = "Lapp/andrewliang/extension/ReelsAdFilter;->" +
    "withoutAds(Ljava/util/Collection;Ljava/lang/String;)Ljava/util/Collection;"

private const val SECTION_FILTER = "Lapp/andrewliang/extension/ReelsAdFilter;->" +
    "withoutAdSections(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;"

@Suppress("unused")
val hideSponsoredReelsPatch = bytecodePatch(
    name = "[Reels] Hide sponsored reels",
    description = "Removes ads from Reels and Watch, so scrolling only shows videos from " +
        "creators. Ads that play inside a video, such as mid-rolls, are not covered.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)
    extendWith("extensions/extension.mpe")

    execute {
        // Facebook does not splice ads into Reels on the device. A logging build showed items
        // reaching the collection only as whole fetched pages, never one at a time, with the ad
        // already in the page beside the organic reels -- the server inlines it. So the page
        // arriving at the collection is the only place left to drop it, the same way the news feed
        // patch rejects an edge rather than trying to prevent its insertion.
        //
        // Nothing obfuscated is named. The task that inserts an SFD ad carries a trace literal, and
        // its constructor is handed both the collection and the ad item -- so one string anchor
        // yields both classes, each picked out by a shape no sibling parameter shares.
        val adTask = SfdAdInsertFingerprint.method.definingClass
        val taken = mutableClassDefBy(adTask).methods
            .single { it.name == "<init>" }
            .parameterTypes
            .map { it.toString() }

        fun pick(what: String, matches: (List<String>) -> Boolean) = taken
            .filter { parameter ->
                mutableClassDefByOrNull(parameter)
                    ?.methods
                    ?.any { matches(listOf(it.returnType) + it.parameterTypes.map(CharSequence::toString)) }
                    ?: false
            }
            .also { check(it.size == 1) { "Expected 1 $what among ${taken.joinToString()}, found $it" } }
            .single()

        // The ad item base: the only one of them that can hand back a story.
        val adBase = pick("ad item type") { shape -> shape == listOf(GRAPHQL_STORY) }

        // The item collection: the only one of them that takes a whole collection and answers.
        val collection = pick("item collection") { shape -> shape == listOf("Z", COLLECTION) }

        val collectionClass = mutableClassDefBy(collection)

        // The method that actually puts the page into the backing list: it takes the position to
        // insert at as well as the page. Its sibling only walks the page afterwards telling
        // listeners about each item, which is why filtering that one alone removed nothing.
        val insertPage = collectionClass.methods.single {
            it.returnType == "Z" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("I", COLLECTION)
        }

        // The listener walk. Filtered too, so nothing is announced that was just dropped.
        val announcePage = collectionClass.methods.single {
            it.returnType == "V" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(collection, COLLECTION)
        }

        // Replace the incoming page with one that has no ads in it. The helper hands back the very
        // same collection when it holds none, so the usual page is untouched and keeps its type.
        listOf(insertPage to "p2", announcePage to "p1").forEach { (method, page) ->
            method.addInstructions(
                0,
                """
                    const-string v0, "${adBase.toBinaryName()}"
                    invoke-static { $page, v0 }, $FILTER
                    move-result-object $page
                """,
            )
        }

        // The filter on the collection is not enough. A device round on 2026-09-19 showed why. It
        // caught an ad in a page and logged the drop. The app then showed that ad as the third
        // reel. One step earlier, the controller adds a section wrapper. The wrapper holds its own
        // list of items, and the screen reads that list. Thus the ad stayed in the wrapper, and
        // the drop count in the log stayed correct.
        //
        // So the same filter also runs one level up, over the sections that the controller gets.
        // Both levels stay. This level reaches the screen. The collection level still covers
        // anything that enters the list by another route.
        val controller = mutableClassDefBy(VideoHomeInsertAdsFingerprint.method.definingClass)

        // A page of fetched sections: one List in, boolean out.
        val addPage = controller.methods.single {
            it.returnType == "Z" && it.parameterTypes.map(CharSequence::toString) == listOf(LIST)
        }

        addPage.addInstructions(
            0,
            """
                const-string ${addPage.scratch()}, "${adBase.toBinaryName()}"
                invoke-static { p1, ${addPage.scratch()} }, $SECTION_FILTER
                move-result-object p1
            """,
        )

        // The client-side insert paths stay blocked. None of them fired in the logging run, but
        // they are what the app would use if a future release went back to inserting on the device,
        // and blocking them costs nothing.
        //
        // Only the inserts are blocked, not the requests that feed them. Stopping those would save
        // data, but belongs with the prefetch patch, and they have not been checked for organic
        // side effects.
        listOf(
            VideoHomeInsertAdsFingerprint,
            RealtimeIntentAdInsertFingerprint,
            SfdAdInsertFingerprint,
            PoeAdRenderFingerprint,
        ).forEach { it.method.addInstructions(0, "return-void") }
    }
}

/** `LX/B89;` as the runtime reports it: `X.B89`. */
private fun String.toBinaryName() = removePrefix("L").removeSuffix(";").replace('/', '.')

/**
 * A register safe to borrow at the top of the method: the one its own first instruction overwrites.
 *
 * Nothing can read that register before the original code writes it. Thus an injected call can use
 * it and change nothing downstream. The two collection-level injections borrow v0, which is sound
 * because those methods are known to hold locals. The controller page method is not, so this one
 * resolves the register instead of an assumption.
 */
private fun MutableMethod.scratch(): String {
    val first = instructions().first()
    check(first is OneRegisterInstruction) {
        "$definingClass->$name starts with ${first.opcode.name}, which writes no register to borrow"
    }

    val register = first.registerA
    check(register < 16) { "$definingClass->$name: scratch register v$register is out of range" }

    return "v$register"
}

private fun MutableMethod.instructions(): List<Instruction> =
    implementation?.instructions?.toList()
        ?: throw IllegalStateException("$definingClass->$name has no body")

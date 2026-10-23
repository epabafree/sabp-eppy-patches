package sabp.morphe.patches.smartaudiobook.ui

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element
import sabp.morphe.patches.smartaudiobook.smartAudioBookCompatibility
import sabp.morphe.patches.smartaudiobook.theme.fontAssetPatch

// The Eppy UI is a new home / library / player written as a Morphe extension
// (extensions/sabp). It does not replace SABP's engine: it drives PlayerService through the intent
// actions SABP's own notification and widgets use, reads state from SABP's media session, and calls
// four PlayerService methods through EppyBridge stubs that this patch fills in.
//
// Those methods are found from code whose names R8 has to keep, so the patch does not depend on
// obfuscated names:
//   * MyMediaBrowserService (Android Auto) builds its book lists with
//       PlayerService.<books>(BookData$BookState) -> ArrayList
//       PlayerService.<coverKeys>(ArrayList) -> ArrayList
//       k8.<loadCovers>(Context, ArrayList) -> ArrayList           (static, package-private)
//   * PlayerActivity's SeekBar callbacks call
//       PlayerService.<seekTracking>(Z)V  from onStartTrackingTouch
//       PlayerService.<seekTo>(I)V        from onProgressChanged

private const val EXT = "Lapp/morphe/extension/sabp"
private const val BRIDGE = "$EXT/EppyBridge;"
private const val SABP = "Lak/alizandro/smartaudiobookplayer"
private const val PLAYER_SERVICE = "$SABP/PlayerService;"
private const val BROWSER_SERVICE = "$SABP/MyMediaBrowserService;"
private const val PLAYER_ACTIVITY = "$SABP/PlayerActivity;"
private const val BOOK_STATE = "$SABP/BookData\$BookState;"
private const val EPPY_ACTIVITY = "app.morphe.extension.sabp.EppyActivity"

private val eppyUiResourcePatch = resourcePatch(
    description = "Registers the Eppy UI screen and bundles its font. Applied with the Eppy UI patch.",
) {
    compatibleWith(smartAudioBookCompatibility)

    val startScreen by booleanOption(
        key = "startScreen",
        default = true,
        title = "Open Eppy UI on launch",
        description = "Makes the new Home screen what the app icon opens. Turn off to keep the classic " +
            "player as the start screen and get a second \"Eppy\" launcher icon instead.",
    )
    val defaultTheme by stringOption(
        key = "defaultTheme",
        default = "system",
        values = mapOf("Follow system" to "system", "Light" to "light", "Dark" to "dark", "Black (AMOLED)" to "black"),
        title = "Default theme",
        description = "Light, dark, black or follow the system. Can be changed later in Eppy settings.",
        required = false,
    )
    val defaultAccent by stringOption(
        key = "defaultAccent",
        default = "wallpaper",
        values = mapOf("Wallpaper (Material You)" to "wallpaper", "Book cover" to "cover", "Custom colour" to "custom"),
        title = "Default accent",
        description = "Where the accent colour comes from. \"Book cover\" picks a readable colour from the " +
            "playing book's artwork. Can be changed later in Eppy settings.",
        required = false,
    )
    val customAccent by stringOption(
        key = "customAccent",
        default = "#E8735A",
        title = "Custom accent colour",
        description = "Hex colour used when the accent is \"Custom colour\", and on Android 11.",
        required = false,
    )

    dependsOn(fontAssetPatch)

    execute {
        // Patch options the extension reads at runtime.
        document("res/values/strings.xml").use { doc ->
            val resources = doc.documentElement
            fun put(name: String, value: String) {
                val existing = (0 until doc.getElementsByTagName("string").length)
                    .map { doc.getElementsByTagName("string").item(it) as Element }
                    .firstOrNull { it.getAttribute("name") == name }
                val el = existing ?: doc.createElement("string").also {
                    it.setAttribute("name", name)
                    it.setAttribute("translatable", "false")
                    resources.appendChild(it)
                }
                el.textContent = value
            }
            put("eppy_default_theme", defaultTheme ?: "system")
            put("eppy_default_accent", defaultAccent ?: "wallpaper")
            put("eppy_custom_accent", customAccent ?: "#E8735A")
        }

        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as Element
            val activities = doc.getElementsByTagName("activity")
            val player = (0 until activities.length).map { activities.item(it) as Element }
                .firstOrNull { it.getAttribute("android:name") == "ak.alizandro.smartaudiobookplayer.PlayerActivity" }
                ?: throw PatchException("Smart AudioBook Player: PlayerActivity is missing from the manifest.")

            val eppy = doc.createElement("activity").apply {
                setAttribute("android:name", EPPY_ACTIVITY)
                setAttribute("android:exported", "true")
                setAttribute("android:label", "@string/app_name")
                setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                setAttribute("android:launchMode", "singleTask")
                setAttribute("android:windowSoftInputMode", "adjustResize")
                setAttribute(
                    "android:configChanges",
                    "orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden|navigation|uiMode|density|fontScale",
                )
                setAttribute("android:resizeableActivity", "true")
            }

            // MAIN/LAUNCHER filter: move it from the classic player, or add a second icon.
            val launcherFilter = (0 until player.childNodes.length)
                .mapNotNull { player.childNodes.item(it) as? Element }
                .firstOrNull { filter ->
                    val cats = filter.getElementsByTagName("category")
                    (0 until cats.length).any {
                        (cats.item(it) as Element).getAttribute("android:name") == "android.intent.category.LAUNCHER"
                    }
                }
            if (startScreen != false) {
                if (launcherFilter != null) {
                    player.removeChild(launcherFilter)
                    eppy.appendChild(launcherFilter)
                } else {
                    eppy.appendChild(newLauncherFilter(doc))
                }
            } else {
                eppy.setAttribute("android:label", "Eppy")
                eppy.appendChild(newLauncherFilter(doc))
            }
            application.appendChild(eppy)
        }
    }
}

private fun newLauncherFilter(doc: org.w3c.dom.Document): Element =
    doc.createElement("intent-filter").apply {
        appendChild(doc.createElement("action").apply { setAttribute("android:name", "android.intent.action.MAIN") })
        appendChild(doc.createElement("category").apply { setAttribute("android:name", "android.intent.category.LAUNCHER") })
    }

@Suppress("unused")
val eppyUiPatch = bytecodePatch(
    name = "Eppy UI",
    description = "Adds a new Material 3 interface: a Home screen with Continue Listening and shelves, a " +
        "cover-grid Library with filters and search, and a Now Playing screen with a big play button, " +
        "thick seek slider and quick chips. Floating mini-player, bottom navigation on phones, navigation " +
        "rail and a two-pane player on tablets and foldables, dynamic or per-book colours, light/dark/black " +
        "themes, keyboard shortcuts, TalkBack labels, reduced-motion and haptics settings. Playback still " +
        "runs on SABP's own engine, and the classic screens stay one tap away.",
) {
    compatibleWith(smartAudioBookCompatibility)
    dependsOn(eppyUiResourcePatch)
    extendWith("extensions/sabp.mpe")

    execute {
        // 1. Let the extension see PlayerService come and go.
        val service = mutableClassDefBy(PLAYER_SERVICE)
        service.methods.single { it.name == "onCreate" && it.parameterTypes.isEmpty() }
            .addInstructions(0, "invoke-static {p0}, $BRIDGE->onPlayerServiceCreated(Landroid/app/Service;)V")
        service.methods.single { it.name == "onDestroy" && it.parameterTypes.isEmpty() }
            .addInstructions(0, "invoke-static {p0}, $BRIDGE->onPlayerServiceDestroyed(Landroid/app/Service;)V")

        // 2. Resolve the library methods from the Android Auto browser service.
        val browser = classDefBy(BROWSER_SERVICE)
        val listMethod = browser.methods.singleOrNull {
            it.parameterTypes.map(CharSequence::toString) == listOf(BOOK_STATE) && it.returnType == "Ljava/util/List;"
        } ?: throw PatchException("Smart AudioBook Player: the Android Auto book list method was not found.")
        val calls = listMethod.methodCalls()
        val books = calls.firstOrNull {
            it.definingClass == PLAYER_SERVICE && it.parameterTypes.map(CharSequence::toString) == listOf(BOOK_STATE) &&
                it.returnType == "Ljava/util/ArrayList;"
        } ?: throw PatchException("Smart AudioBook Player: PlayerService book list call not found.")
        val coverKeys = calls.firstOrNull {
            it.definingClass == PLAYER_SERVICE && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/util/ArrayList;") &&
                it.returnType == "Ljava/util/ArrayList;"
        } ?: throw PatchException("Smart AudioBook Player: PlayerService cover list call not found.")
        val loadCovers = calls.firstOrNull {
            it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;", "Ljava/util/ArrayList;") &&
                it.returnType == "Ljava/util/ArrayList;"
        } ?: throw PatchException("Smart AudioBook Player: cover loader call not found.")

        // 3. Resolve seeking from the classic player's SeekBar callbacks.
        val activity = classDefBy(PLAYER_ACTIVITY)
        val seekTracking = activity.methods.single {
            it.name == "onStartTrackingTouch" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/widget/SeekBar;")
        }.methodCalls().firstOrNull {
            it.definingClass == PLAYER_SERVICE && it.parameterTypes.map(CharSequence::toString) == listOf("Z") && it.returnType == "V"
        } ?: throw PatchException("Smart AudioBook Player: seek start call not found.")
        val seekTo = activity.methods.single {
            it.name == "onProgressChanged" && it.parameterTypes.size == 3
        }.methodCalls().firstOrNull {
            it.definingClass == PLAYER_SERVICE && it.parameterTypes.map(CharSequence::toString) == listOf("I") && it.returnType == "V"
        } ?: throw PatchException("Smart AudioBook Player: seek call not found.")

        // The cover loader is package-private; the extension lives in another package.
        listOf(books, coverKeys, loadCovers, seekTracking, seekTo).forEach { makeAccessible(it) }

        // 4. Fill in the EppyBridge stubs.
        val bridge = mutableClassDefBy(BRIDGE)
        fun stub(name: String) = bridge.methods.single { it.name == name }
        stub("booksForState").replaceBody(
            """
            check-cast p0, $PLAYER_SERVICE
            check-cast p1, $BOOK_STATE
            invoke-virtual {p0, p1}, ${books.smali()}
            move-result-object v0
            return-object v0
            """,
        )
        stub("coverKeys").replaceBody(
            """
            check-cast p0, $PLAYER_SERVICE
            invoke-virtual {p0, p1}, ${coverKeys.smali()}
            move-result-object v0
            return-object v0
            """,
        )
        stub("loadCovers").replaceBody(
            """
            invoke-static {p0, p1}, ${loadCovers.smali()}
            move-result-object v0
            return-object v0
            """,
        )
        stub("seekTracking").replaceBody(
            """
            check-cast p0, $PLAYER_SERVICE
            invoke-virtual {p0, p1}, ${seekTracking.smali()}
            return-void
            """,
        )
        stub("seekToSeconds").replaceBody(
            """
            check-cast p0, $PLAYER_SERVICE
            invoke-virtual {p0, p1}, ${seekTo.smali()}
            return-void
            """,
        )
        stub("isLinked").replaceBody(
            """
            const/4 v0, 0x1
            return v0
            """,
        )
    }
}

private fun Method.methodCalls(): List<MethodReference> =
    implementation?.instructions?.mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }.orEmpty()

private fun MethodReference.smali(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun MutableMethod.replaceBody(smali: String) {
    removeInstructions(0, instructions.size)
    addInstructions(0, smali.trimIndent())
}

/** Makes a package-private method (and its class) public. Private methods are left alone. */
private fun BytecodePatchContext.makeAccessible(ref: MethodReference) {
    val cls = mutableClassDefBy(ref.definingClass)
    if (cls.accessFlags and AccessFlags.PUBLIC.value == 0) {
        cls.accessFlags = cls.accessFlags or AccessFlags.PUBLIC.value
    }
    val method = cls.methods.firstOrNull {
        it.name == ref.name && it.returnType == ref.returnType &&
            it.parameterTypes.map(CharSequence::toString) == ref.parameterTypes.map(CharSequence::toString)
    } ?: throw PatchException("Smart AudioBook Player: ${ref.name} not found in ${ref.definingClass}.")
    val flags = method.accessFlags
    if (flags and AccessFlags.PRIVATE.value != 0) {
        throw PatchException("Smart AudioBook Player: ${ref.name} is private and cannot be called from the extension.")
    }
    if (flags and AccessFlags.PUBLIC.value == 0) {
        method.accessFlags = (flags and AccessFlags.PROTECTED.value.inv()) or AccessFlags.PUBLIC.value
    }
}

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

// eppy is a new home / library / player written as a Morphe extension
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
private const val BOOKMARK = "$SABP/Bookmark;"
private const val EPPY_ACTIVITY = "app.morphe.extension.sabp.EppyActivity"

private fun newLauncherFilter(doc: org.w3c.dom.Document): Element =
    doc.createElement("intent-filter").apply {
        appendChild(doc.createElement("action").apply { setAttribute("android:name", "android.intent.action.MAIN") })
        appendChild(doc.createElement("category").apply { setAttribute("android:name", "android.intent.category.LAUNCHER") })
    }

private val eppyUiBytecodePatch = bytecodePatch(
    description = "Connects eppy to SABP's player service. Applied with the eppy patch.",
) {
    compatibleWith(smartAudioBookCompatibility)
    dependsOn(fontAssetPatch)
    extendWith("extensions/sabp.mpe")

    execute {
        // 1. Let the extension see PlayerService come and go.
        val service = mutableClassDefBy(PLAYER_SERVICE)
        service.methods.single { it.name == "onCreate" && it.parameterTypes.isEmpty() }
            .addInstructions(0, "invoke-static {p0}, $BRIDGE->onPlayerServiceCreated(Landroid/app/Service;)V")
        service.methods.single { it.name == "onDestroy" && it.parameterTypes.isEmpty() }
            .addInstructions(0, "invoke-static {p0}, $BRIDGE->onPlayerServiceDestroyed(Landroid/app/Service;)V")

        // 1b. Crash recorder + diagnostics as early as possible.
        mutableClassDefBy("$SABP/SABPApplication;").methods.single { it.name == "onCreate" && it.parameterTypes.isEmpty() }
            .addInstructions(0, "invoke-static {p0}, $EXT/EppyDiag;->install(Landroid/app/Application;)V")

        // 1c. Central player routing. Every route into the classic player (notification content
        //     intent, widgets, the library's "open book", voice search, Android restoring the task)
        //     starts PlayerActivity, so its onCreate decides once: if the chosen player interface
        //     is eppy (and the classic player wasn't asked for explicitly), call super.onCreate,
        //     hand the intent to eppy and finish before any classic UI is built. Same pattern as
        //     Morphe's YouTube settings hook (invoke-super → extension → return-void).
        val playerActivity = mutableClassDefBy(PLAYER_ACTIVITY)
        val onCreate = playerActivity.methods.single {
            it.name == "onCreate" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/os/Bundle;")
        }
        val locals = (onCreate.implementation?.registerCount ?: 0) - 2
        if (locals < 1) throw PatchException("Smart AudioBook Player: PlayerActivity.onCreate has no free register.")
        onCreate.addInstructions(
            0,
            """
            invoke-static {p0}, $EXT/EppyRoute;->shouldRedirect(Landroid/app/Activity;)Z
            move-result v0
            if-eqz v0, :eppy_classic
            invoke-super {p0, p1}, ${playerActivity.superclass}->onCreate(Landroid/os/Bundle;)V
            invoke-static {p0}, $EXT/EppyRoute;->redirect(Landroid/app/Activity;)V
            return-void
            :eppy_classic
            nop
            """,
        )

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

        // 4. Live file position: the first int getter PlayerService's media-metadata builder calls
        //    (the method that puts "android.media.metadata.DURATION").
        val metadataMethod = service.methods.firstOrNull { m ->
            m.implementation?.instructions?.any {
                ((it as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference)
                    ?.string == "android.media.metadata.DURATION"
            } == true
        } ?: throw PatchException("Smart AudioBook Player: media metadata builder not found.")
        val filePosition = metadataMethod.methodCalls().firstOrNull {
            it.definingClass == PLAYER_SERVICE && it.parameterTypes.isEmpty() && it.returnType == "I"
        } ?: throw PatchException("Smart AudioBook Player: file position getter not found.")

        // 5. Jump to file + position: the PlayerService method SABP's bookmarks, history and chapter
        //    dialog call. It's the only (String, int, boolean) -> void method.
        val jumpCandidates = service.methods.filter {
            it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;", "I", "Z") && it.returnType == "V"
        }
        val jump = jumpCandidates.singleOrNull()
            ?: throw PatchException("Smart AudioBook Player: expected one jump method, found ${jumpCandidates.size}.")

        // 6. Bookmark file helpers on SABP's Bookmark class (static, package-private).
        val bookmark = classDefBy(BOOKMARK)
        val loadBookmarks = bookmark.methods.singleOrNull {
            AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "Ljava/util/ArrayList;" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;", "Ljava/lang/String;")
        } ?: throw PatchException("Smart AudioBook Player: bookmark loader not found.")
        val saveBookmarks = bookmark.methods.singleOrNull {
            AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "V" &&
                it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;", "Ljava/util/ArrayList;", "Ljava/lang/String;")
        } ?: throw PatchException("Smart AudioBook Player: bookmark writer not found.")

        // Package-private targets must be public: the extension lives in another package.
        listOf(books, coverKeys, loadCovers, seekTracking, seekTo, filePosition).forEach { makeAccessible(it) }
        listOf(jump, loadBookmarks, saveBookmarks).forEach { makeAccessible(it.asReference()) }

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
        stub("filePositionSeconds").replaceBody(
            """
            check-cast p0, $PLAYER_SERVICE
            invoke-virtual {p0}, ${filePosition.smali()}
            move-result v0
            return v0
            """,
        )
        stub("jumpTo").replaceBody(
            """
            check-cast p0, $PLAYER_SERVICE
            invoke-virtual {p0, p1, p2, p3}, ${jump.asReference().smali()}
            return-void
            """,
        )
        stub("loadBookmarks").replaceBody(
            """
            invoke-static {p0, p1}, ${loadBookmarks.asReference().smali()}
            move-result-object v0
            return-object v0
            """,
        )
        stub("saveBookmarks").replaceBody(
            """
            invoke-static {p0, p1, p2}, ${saveBookmarks.asReference().smali()}
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

@Suppress("unused")
val eppyUiPatch = resourcePatch(
    name = "eppy",
    description = "eppy: a new Material 3 player for SABP. A Home screen with Continue Listening and shelves, a " +
        "cover-grid Library, and Now Playing with a circular play button, Add/Remove bookmark, and a " +
        "drag-up sheet: native Chapters, Read along (pair an EPUB/PDF and follow the narration), " +
        "Bookmarks, Details and Up next. Six styles based on reference designs, a floating mini-player, " +
        "adaptive navigation, accessibility and keyboard support. Playback stays on SABP's own engine. " +
        "Best used with Hooman's SABP patch.",
) {
    compatibleWith(smartAudioBookCompatibility)
    dependsOn(eppyUiBytecodePatch)

    val startScreen by booleanOption(
        key = "startScreen",
        default = true,
        title = "Use eppy as the player",
        description = "Default player interface. On: the app icon, notification, widgets, library and voice " +
            "search all open eppy. Off: they open the classic SABP player. Either way you can switch later in " +
            "eppy Settings → Player interface (with Classic chosen, long-press the app icon → eppy).",
    )
    val defaultSkin by stringOption(
        key = "defaultSkin",
        default = "material",
        values = mapOf(
            "Material You (wallpaper colours)" to "material",
            "Ember: dark with coral, ring player" to "ember",
            "Midnight: deep purple, lilac transcript player" to "midnight",
            "Lavender: light violet glass cards" to "lavender",
            "Paper: warm cream editorial" to "paper",
            "Mono: white, black and yellow" to "mono",
        ),
        title = "Style",
        description = "The look the app starts with, based on the reference designs. Every style can also be " +
            "switched in eppy Settings → Style without re-patching.",
        required = false,
    )
    val speechMatching by booleanOption(
        key = "speechMatching",
        default = false,
        title = "Read-along speech matching",
        description = "Lets Read along match the narration to your EPUB/PDF with Android's on-device speech " +
            "recogniser (Android 13+). Adds the microphone permission, which Android's speech service requires " +
            "even though only the audiobook's own audio is fed to it. You are asked before it's used. Turn off " +
            "to keep read-along to chapter alignment and manual sync only (no microphone permission).",
    )
    val defaultTheme by stringOption(
        key = "defaultTheme",
        default = "system",
        values = mapOf("Follow system" to "system", "Light" to "light", "Dark" to "dark", "Black (AMOLED)" to "black"),
        title = "Default theme",
        description = "Light, dark, black or follow the system. Can be changed later in eppy Settings.",
        required = false,
    )
    val defaultAccent by stringOption(
        key = "defaultAccent",
        default = "wallpaper",
        values = mapOf("Wallpaper (Material You)" to "wallpaper", "Book cover" to "cover", "Custom colour" to "custom"),
        title = "Default accent",
        description = "Where the accent colour comes from. \"Book cover\" picks a readable colour from the " +
            "playing book's artwork. Can be changed later in eppy Settings.",
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
            put("eppy_default_skin", defaultSkin ?: "material")
            put("eppy_speech", if (speechMatching == true) "on" else "off")
            put("eppy_default_interface", if (startScreen != false) "eppy" else "classic")
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

            // MAIN/LAUNCHER moves to eppy's activity; the router then opens whichever player
            // interface is chosen, so the icon always respects the setting.
            val launcherFilter = (0 until player.childNodes.length)
                .mapNotNull { player.childNodes.item(it) as? Element }
                .firstOrNull { filter ->
                    val cats = filter.getElementsByTagName("category")
                    (0 until cats.length).any {
                        (cats.item(it) as Element).getAttribute("android:name") == "android.intent.category.LAUNCHER"
                    }
                }
            if (launcherFilter != null) {
                player.removeChild(launcherFilter)
                eppy.appendChild(launcherFilter)
            } else {
                eppy.appendChild(newLauncherFilter(doc))
            }
            application.appendChild(eppy)

            if (speechMatching == true) {
                val perms = doc.getElementsByTagName("uses-permission")
                val has = (0 until perms.length).any {
                    (perms.item(it) as Element).getAttribute("android:name") == "android.permission.RECORD_AUDIO"
                }
                if (!has) {
                    doc.documentElement.insertBefore(
                        doc.createElement("uses-permission").apply { setAttribute("android:name", "android.permission.RECORD_AUDIO") },
                        application,
                    )
                }
                // Lets the app find the system speech recogniser on Android 11+ (package visibility).
                val queries = doc.getElementsByTagName("queries").item(0) as? Element
                    ?: doc.createElement("queries").also { doc.documentElement.insertBefore(it, application) }
                queries.appendChild(doc.createElement("intent").apply {
                    appendChild(doc.createElement("action").apply { setAttribute("android:name", "android.speech.RecognitionService") })
                })
            }
        }
    }
}


private fun Method.methodCalls(): List<MethodReference> =
    implementation?.instructions?.mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }.orEmpty()

private fun Method.asReference(): MethodReference =
    com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference(definingClass, name, parameterTypes, returnType)

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

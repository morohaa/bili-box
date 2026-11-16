package io.github.bilibox.feature.library

import android.Manifest
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.feature.player.createSeekPreviewFixture
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Files inserted by shell belong to another owner, exercising real MediaStore consent. */
class LibraryFileConsentTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val uris = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        uris.forEach { shell("content delete --uri $it") }
    }

    @Test
    fun foreignFileRequiresConsentAndCancelPreservesFileBeforeRenameAndDelete() {
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_VIDEO)
        val video = publishForeign()
        val target = video.uri
        lateinit var actions: LibraryVideoActionsState
        var renamed: String? = null
        var removed = false
        compose.setContent {
            BiliBoxTheme(dynamicColor = false) {
                actions = rememberLibraryVideoActions(onFileChanged = { _, name ->
                    if (name == null) removed = true else renamed = name
                }, notify = {})
                Text("文件权限测试")
                LibraryVideoDialogs(actions)
            }
        }
        compose.runOnIdle {
            actions.openRename(video)
            actions.rename("renamed")
        }
        systemButton("android:id/button2")
        compose.waitUntil(5_000) { compose.runOnIdle { !actions.busy && actions.error != null } }
        assertTrue(shell("content query --uri $target --projection _display_name").contains("consent.mp4"))
        assertNull(renamed)
        compose.runOnIdle { actions.rename("renamed") }
        systemButton("android:id/button1")
        compose.waitUntil(5_000) { compose.runOnIdle { !actions.busy } }
        compose.runOnIdle {
            assertEquals(null, actions.error)
            assertEquals("renamed.mp4", renamed)
        }
        assertTrue(shell("content query --uri $target --projection _display_name").contains("renamed.mp4"))
        // The rename grant also permits deletion of that item. Use another item
        // without an existing grant to exercise the system's delete request.
        val second = publishForeign()
        compose.runOnIdle {
            actions.openDelete(second)
            actions.delete()
        }
        systemButton("android:id/button1")
        compose.waitUntil(5_000) { compose.runOnIdle { !actions.busy } }
        compose.runOnIdle {
            assertNull(actions.error)
            assertTrue(removed)
        }
        assertTrue(shell("content query --uri ${second.uri}").contains("No result found"))
        assertTrue(shell("content query --uri $target --projection _display_name").contains("renamed.mp4"))
    }

    private fun publishForeign(): LocalVideo {
        val fixture = createSeekPreviewFixture(context)
        val folder = "Movies/BiliBoxConsent-${UUID.randomUUID()}"
        shell("content insert --uri content://media/external_primary/video/media --bind _display_name:s:consent.mp4 --bind relative_path:s:$folder --bind mime_type:s:video/mp4 --bind is_pending:i:1")
        val inserted = shell("content query --uri content://media/external_primary/video/media?includePending=1 --projection _id:relative_path").lineSequence().first { it.contains(folder) }
        val id = checkNotNull(Regex("_id=(\\d+)").find(inserted)) { inserted }.groupValues[1]
        val target = Uri.parse("content://media/external_primary/video/media/$id")
        uris += target
        val pipes = automation.executeShellCommandRw("content write --uri $target")
        ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { output -> fixture.inputStream().use { it.copyTo(output) } }
        ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
        shell("content update --uri $target --bind is_pending:i:0")
        // Match the scanner's aggregate URI; production must resolve its volume.
        return LocalVideo(Uri.parse("content://media/external/video/media/$id"), "consent.mp4", 30_000, fixture.length(), relativePath = folder)
    }

    private fun systemButton(id: String) {
        var button: AccessibilityNodeInfo? = null
        compose.waitUntil(8_000) {
            button = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
            button != null
        }
        check(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
}

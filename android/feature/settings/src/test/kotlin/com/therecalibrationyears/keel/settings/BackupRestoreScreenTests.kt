package com.therecalibrationyears.keel.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import com.therecalibrationyears.keel.designsystem.KeelThemeProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class FakeBackup(private val refuse: String? = null) : BackupActions {
    override val zone: ZoneId = ZoneOffset.UTC
    val read = mutableListOf<String>()
    var restored = 0
    var discarded = 0
    override fun fileName() = "Keel-backup-2026-10-22-1045.keelbackup"
    override suspend fun exportData() = "{\"version\":1}".toByteArray()
    override suspend fun read(bytes: ByteArray): BackupPreview? {
        val text = bytes.decodeToString().also { read += it }
        return if (text.startsWith("{")) BackupPreview(12, 54, 3, Instant.parse("2026-10-20T08:15:00Z")) else null
    }
    override suspend fun restorePending(): RestoreOutcome {
        if (refuse != null) return RestoreOutcome.Refused(refuse)
        restored++
        return RestoreOutcome.Done(RestoreSummary(12, 3, 7))
    }
    override fun discardPending() { discarded++ }
}

/** Backup & Restore (iOS `BackupRestoreView`), driven through Android's own file pickers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class BackupRestoreScreenTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()

    private fun show(actions: FakeBackup) = compose.setContent { KeelThemeProvider { BackupRestoreScreen(actions, onBack = {}) } }

    /** Answer the picker Keel opened with [uri], as Android's file picker would. */
    private fun pick(expectedAction: String, uri: Uri): Intent {
        val shadow = shadowOf(compose.activity)
        val started = shadow.nextStartedActivityForResult
        assertEquals(expectedAction, started.intent.action)
        shadow.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))
        compose.waitForIdle()
        return started.intent
    }

    /** The file is read and written off the main thread, which the test clock doesn't wait for. */
    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test fun exportSavesTheFileWhereSheChooses() {
        show(FakeBackup())
        compose.onNodeWithText("Export data").performClick()
        val target = tmp.newFile("saved.keelbackup")
        val intent = pick(Intent.ACTION_CREATE_DOCUMENT, Uri.fromFile(target))
        assertEquals("Keel-backup-2026-10-22-1045.keelbackup", intent.getStringExtra(Intent.EXTRA_TITLE))
        compose.waitUntil(5_000) { target.length() > 0 }
        assertEquals("{\"version\":1}", target.readText())
    }

    @Test fun restoreAsksFirstThenReplaces() {
        val actions = FakeBackup()
        show(actions)
        compose.onNodeWithText("Restore from a file").performClick()
        pick(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(tmp.newFile("b.keelbackup").apply { writeText("{}") }))
        waitForText("Replace all current data?")
        compose.onNodeWithText("Replace all current data?").assertIsDisplayed()
        compose.onNodeWithText(
            "This restores 12 check-ins, 54 symptoms and 3 medications from a backup made", substring = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("Yes, restore").performClick()
        waitForText("Data restored")
        assertEquals(1, actions.restored)
        compose.onNodeWithText("Data restored").assertIsDisplayed()
        compose.onNodeWithText("12 check-ins · 3 meds · 7 cycle entries").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Restore from a file").assertIsDisplayed()
    }

    @Test fun cancelKeepsHerData() {
        val actions = FakeBackup()
        show(actions)
        compose.onNodeWithText("Restore from a file").performClick()
        pick(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(tmp.newFile("b.keelbackup").apply { writeText("{}") }))
        waitForText("Cancel")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, actions.restored)
        assertEquals(1, actions.discarded)
        compose.onNodeWithText("Restore from a file").assertIsDisplayed()
    }

    @Test fun aFileThatIsntABackupIsTurnedAway() {
        show(FakeBackup())
        compose.onNodeWithText("Restore from a file").performClick()
        pick(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(tmp.newFile("photo.jpg").apply { writeText("JFIF") }))
        waitForText("Couldn't complete")
        compose.onNodeWithText("Couldn't complete").assertIsDisplayed()
        compose.onNodeWithText("That file isn't a valid Keel backup.").assertIsDisplayed()
    }

    @Test fun aNewerFormatIsRefusedWithItsReason() {
        val reason = "This backup was made by a newer version of Keel (format 2). Update the app to restore it."
        val actions = FakeBackup(refuse = reason)
        show(actions)
        compose.onNodeWithText("Restore from a file").performClick()
        pick(Intent.ACTION_OPEN_DOCUMENT, Uri.fromFile(tmp.newFile("b.keelbackup").apply { writeText("{}") }))
        waitForText("Yes, restore")
        compose.onNodeWithText("Yes, restore").performClick()
        waitForText(reason)
        compose.onNodeWithText(reason).assertIsDisplayed()
        assertTrue(actions.read.isNotEmpty())
    }

    @Test fun backingOutOfThePickerChangesNothing() {
        show(FakeBackup())
        compose.onNodeWithText("Restore from a file").performClick()
        val shadow = shadowOf(compose.activity)
        val started = shadow.nextStartedActivityForResult
        shadow.receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        compose.waitForIdle()
        compose.onNodeWithText("Restore from a file").assertIsDisplayed()
        compose.onNodeWithText("Couldn't complete").assertDoesNotExist()
    }
}

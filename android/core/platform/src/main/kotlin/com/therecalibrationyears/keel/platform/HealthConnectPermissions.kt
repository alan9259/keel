package com.therecalibrationyears.keel.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.therecalibrationyears.keel.domain.HealthPermissionRequesting
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens Health Connect's permission screen (an activity result) for the app's controller. The
 * activity on screen registers itself with [attach] in `onCreate`; one instance for the app, so
 * an answer that arrives after the activity was recreated still reaches the waiting request.
 */
class HealthConnectPermissions(private val context: Context) : HealthPermissionRequesting {
    private var launcher: ActivityResultLauncher<Set<String>>? = null
    private var activity = WeakReference<ComponentActivity>(null)
    private var pending: CompletableDeferred<Set<String>>? = null

    fun attach(activity: ComponentActivity) {
        this.activity = WeakReference(activity)
        launcher = activity.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            pending?.complete(granted)
            pending = null
        }
    }

    override suspend fun request(permissions: Set<String>): Set<String>? {
        val launch = launcher ?: return null
        val answer = CompletableDeferred<Set<String>>()
        withContext(Dispatchers.Main) {
            pending?.cancel() // an earlier request that never came back
            pending = answer
            try {
                launch.launch(permissions)
            } catch (e: ActivityNotFoundException) {
                pending = null
                answer.complete(emptySet())
            }
        }
        return answer.await()
    }

    override fun openInstall() {
        val play = Intent(Intent.ACTION_VIEW, PLAY_INSTALL.toUri()).setPackage(PLAY_STORE)
            .putExtra("overlay", true).putExtra("callerId", context.packageName)
        start(play) ?: start(Intent(Intent.ACTION_VIEW, PLAY_WEB.toUri()))
    }

    /**
     * "Manage access in Health Connect": Health Connect's home, where App permissions lists
     * Keel. (Its page for one app, `ACTION_MANAGE_HEALTH_PERMISSIONS`, is for the system only:
     * Android refuses it to other apps.)
     */
    fun openManageAccess() {
        start(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }

    /**
     * Starts from the activity on screen; without one (none attached yet), from the app as a
     * new task, which Android requires outside an activity. Null when nothing can open it, or
     * Android won't let Keel open it.
     */
    private fun start(intent: Intent): Unit? {
        val from = activity.get()
        return try {
            if (from != null) from.startActivity(intent) else context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        private const val PLAY_STORE = "com.android.vending" // not-copy: package name
        private const val PLAY_INSTALL = "market://details?id=${HealthConnectReads.PROVIDER_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding" // not-copy: URL
        private const val PLAY_WEB = "https://play.google.com/store/apps/details?id=${HealthConnectReads.PROVIDER_PACKAGE}" // not-copy: URL
    }
}

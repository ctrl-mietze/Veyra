package ctrl.mietze.veyraroot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The way pairing is started, and the way it is *forced* to start again.
 *
 * The second part is what makes this an activity rather than a plain service call. A stored pairing
 * can be stale in a way nothing on this side can see: the device may have discarded this app from its
 * paired-device list, while the key and the flag are still on disk. Retrying then looks like a no-op,
 * because a service that trusts the flag has nothing to do. Asking for a re-pair clears the flag
 * first, so the next pairing starts from the device's own state rather than from the app's record of
 * it.
 */
class AdbPairingSetupActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginPairing()
        } else {
            Toast.makeText(
                this,
                getString(R.string.adb_pair_notification_permission_required),
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(EXTRA_FORCE_REPAIR, false)) {
            // The code field has to live in a notification, so the permission is what makes pairing
            // possible at all - it is cleared here and set again only when a pairing succeeds.
            AppPreferences.setAdbPaired(this, false)
        } else if (AppPreferences.adbPaired(this) && AdbCredentialStore.hasStoredKey(this)) {
            // Already paired with a key that can be tried: nothing to do, and the connection test is
            // what says whether it still works.
            finish()
            return
        }

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            beginPairing()
        } else {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * The three things a pairing needs from this side, in the order they have to happen.
     *
     * Wireless debugging first, because a pairing service only exists while it is on, and a freshly
     * set-up device has it off. [TemporaryWirelessAdb] is the same window the transport uses for a
     * run - the failsafe alarm is armed before the switch moves, and the pairing service turns it off
     * again when the transaction ends - so this cannot leave a shell port open. It runs off the main
     * thread because the root route to that setting starts a shell, and it is allowed to fail: a
     * device with neither the permission nor root gets the switch by hand, which is what the last step
     * is for.
     *
     * Developer options last, because the six-digit code only exists inside Android's own pairing
     * dialog, and that dialog only exists there. Opening it is the whole reason this is an activity
     * rather than a bare service call.
     */
    private fun beginPairing() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { TemporaryWirelessAdb.begin(this@AdbPairingSetupActivity) }
            ContextCompat.startForegroundService(this@AdbPairingSetupActivity, AdbPairingService.startIntent(this@AdbPairingSetupActivity))
            if (!DeveloperOptions.open(this@AdbPairingSetupActivity)) {
                Toast.makeText(
                    this@AdbPairingSetupActivity,
                    getString(R.string.developer_options_unavailable),
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }

    companion object {
        private const val EXTRA_FORCE_REPAIR = "force_repair"

        fun pairingIntent(context: Context, forceRepair: Boolean = false): Intent =
            Intent(context, AdbPairingSetupActivity::class.java)
                .putExtra(EXTRA_FORCE_REPAIR, forceRepair)
    }
}

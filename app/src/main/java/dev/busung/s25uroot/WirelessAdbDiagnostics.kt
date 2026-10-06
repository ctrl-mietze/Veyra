package ctrl.mietze.veyraroot

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What this app can say about its wireless-debugging authorization, and nothing more. */
internal enum class WirelessAdbAuthState {
    /** No key stored: there is nothing to authorize. */
    NoCredential,

    /** A key is stored, and nothing has asked the device whether it still accepts it. */
    SavedUnverified,

    /** A connection succeeded, so adbd answered with a shell identity. */
    Valid,

    /** adbd refused this app's certificate: it was unpaired device-side. */
    PairingRejected,

    /** No port could be found, by the property or by mDNS. */
    PortUnavailable,

    /** The app may not turn wireless debugging on, so it cannot test anything. */
    PermissionRequired,

    /** Anything else that stopped the connection. */
    ConnectionFailed,
}

internal data class WirelessAdbSnapshot(
    val credentialFlag: Boolean,
    val keyPresent: Boolean,
    val fingerprint: String?,
    val wirelessDebuggingEnabled: Boolean,
    val connectPort: Int? = null,
    val authState: WirelessAdbAuthState,
    val detail: String = "",
)

/**
 * Which state a failed connection test is, from the two things that distinguish them.
 *
 * A refused certificate and a missing port need opposite responses - pair again on the device, versus
 * turn wireless debugging on - so they are separated before anything is reported. Pure, so the
 * distinction can be tested without a device that has either problem.
 */
internal fun wirelessAdbFailureState(
    pairingRejected: Boolean,
    portUnavailable: Boolean,
): WirelessAdbAuthState = when {
    pairingRejected -> WirelessAdbAuthState.PairingRejected
    portUnavailable -> WirelessAdbAuthState.PortUnavailable
    else -> WirelessAdbAuthState.ConnectionFailed
}

/**
 * Whether the wireless transport actually works, as opposed to whether it was set up.
 *
 * A stored flag and a stored key are both records of the past: the device can forget this app at any
 * time, and nothing on this side changes when it does. So a saved credential is reported as
 * *unverified* rather than as ready, and only a connection that comes back with a shell identity is
 * reported as valid. The two are kept apart in the type ([WirelessAdbAuthState.SavedUnverified] versus
 * [WirelessAdbAuthState.Valid]) so no caller can accidentally treat one as the other.
 */
internal object WirelessAdbDiagnostics {

    fun passiveSnapshot(context: Context): WirelessAdbSnapshot {
        val keyPresent = AdbCredentialStore.hasStoredKey(context)
        val credentialFlag = AppPreferences.adbPaired(context)
        return WirelessAdbSnapshot(
            credentialFlag = credentialFlag,
            keyPresent = keyPresent,
            fingerprint = AdbCredentialStore.fingerprint(context),
            wirelessDebuggingEnabled = AdbPairing.isWirelessAdbEnabled(context),
            authState = if (keyPresent) {
                WirelessAdbAuthState.SavedUnverified
            } else {
                WirelessAdbAuthState.NoCredential
            },
            detail = when {
                credentialFlag && !keyPresent ->
                    "The recorded pairing is stale: this app's ADB key is gone, so pair again"
                !credentialFlag && keyPresent ->
                    "A key exists, but the device has not been asked whether it still accepts it"
                keyPresent -> "Saved; run the connection test to see whether the device still accepts it"
                else -> "No wireless-debugging credential is stored"
            },
        )
    }

    /**
     * Tests the transport for real, through a temporary window.
     *
     * The window is opened even when wireless debugging was off, and closed again afterwards, because
     * a test that left it on would be changing device state to answer a question.
     */
    suspend fun testConnection(context: Context): WirelessAdbSnapshot = withContext(Dispatchers.IO) {
        val initial = passiveSnapshot(context)
        if (!initial.keyPresent) {
            AppPreferences.setAdbPaired(context, false)
            return@withContext initial.copy(
                credentialFlag = false,
                authState = WirelessAdbAuthState.NoCredential,
                detail = "No wireless-debugging key is stored, so there is nothing to test",
            )
        }

        // The permission is only needed to *turn wireless debugging on*. A device that already has it
        // on can be tested - and paired, and run through - with no permission at all, which is the
        // state a user is in the moment they open the pairing dialog in Developer options. Gating the
        // test on the permission reported a working transport as unusable.
        if (!wirelessAdbUsable(
                wirelessDebuggingEnabled = AdbPairing.isWirelessAdbEnabled(context),
                permissionGranted = AdbPairing.hasWriteSecureSettings(context),
                rootAvailable = rootIsAvailable(context),
            )
        ) {
            return@withContext initial.copy(
                authState = WirelessAdbAuthState.PermissionRequired,
                detail = context.getString(
                    R.string.wireless_adb_permission_detail,
                    AdbPairing.GRANT_COMMAND,
                ),
            )
        }

        var discoveredPort: Int? = null
        try {
            TemporaryWirelessAdb.use(context, settleMillis = ENABLE_SETTLE_MILLIS) {
                val port = AdbPairing.discoverConnectPort(context, DISCOVERY_TIMEOUT_MILLIS)
                if (port <= 0) throw ConnectPortUnavailableException()
                discoveredPort = port

                val result = LocalAdbClient.shellOnce(
                    host = "127.0.0.1",
                    port = port,
                    keyManager = AdbKeyManager(context),
                    command = "id",
                )
                // The identity the run itself requires, so this screen cannot call a transport good
                // that a run would refuse: a shell that answers is not necessarily the shell a run
                // needs, and reporting the weaker "it answered" would set the paired flag on one.
                localAdbShellIdentityFailure(result)?.let { reason ->
                    throw IOException("The device's own adb shell is not usable: $reason")
                }
            }

            AppPreferences.setAdbPaired(context, true)
            passiveSnapshot(context).copy(
                credentialFlag = true,
                connectPort = discoveredPort,
                authState = WirelessAdbAuthState.Valid,
                detail = "The device accepted this app's key and answered as a shell",
            )
        } catch (error: Throwable) {
            val pairingRejected = LocalAdbClient.isPairingLostError(error) ||
                LocalAdbClient.PAIRING_LOST_MARKER in (error.message ?: "")
            // A refused certificate is the device saying it no longer knows this app, so the recorded
            // pairing is cleared: keeping it would leave a flag that contradicts the device.
            if (pairingRejected) AppPreferences.setAdbPaired(context, false)

            val state = wirelessAdbFailureState(
                pairingRejected = pairingRejected,
                portUnavailable = error is ConnectPortUnavailableException,
            )
            passiveSnapshot(context).copy(
                credentialFlag = AppPreferences.adbPaired(context),
                connectPort = discoveredPort,
                authState = state,
                detail = when (state) {
                    WirelessAdbAuthState.PairingRejected ->
                        "The device rejected this app's certificate, so it has to be paired again"
                    WirelessAdbAuthState.PortUnavailable ->
                        "No wireless-debugging port was found, by the system property or by mDNS"
                    else -> error.message ?: error.javaClass.simpleName
                },
            )
        }
    }

    /**
     * Whether root is available to change the setting, which is the app's second route to it.
     *
     * Asked rather than assumed: a device with no permission and no root genuinely cannot turn
     * wireless debugging on, and saying so is more useful than a failure further down.
     */
    private fun rootIsAvailable(context: Context): Boolean =
        runCatching { KernelSuRuntime.rootShell("id") != null }.getOrDefault(false)

    private class ConnectPortUnavailableException : IOException(
        "No wireless-debugging port was found, by the system property or by mDNS",
    )

    private const val ENABLE_SETTLE_MILLIS = 1_000L
    private const val DISCOVERY_TIMEOUT_MILLIS = 15_000L
}

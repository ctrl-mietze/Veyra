package ctrl.mietze.veyraroot

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * Finds the device's own wireless-debugging service through mDNS.
 *
 * This is the fallback, not the first choice: the port adbd is listening on is published as a system
 * property, and reading that works with Wi-Fi off. mDNS is what covers the cases where the property is
 * missing - an older adbd, or a build that does not publish it - and it only ever accepts a service
 * that is on this device, because a pairing code for this phone must not be spent on another one.
 */
class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val onPort: (Int) -> Unit,
) {
    private var registered = false
    private var running = false
    private var serviceName: String? = null
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            registered = true
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            AppLog.warn(AppLogTags.WIRELESS_ADB, "mDNS discovery could not start: $errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            registered = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            // A fresh resolve listener per call, deliberately: NsdManager refuses a listener that is
            // already resolving, and several services can be announced before the first resolve
            // finishes - which would otherwise crash the app at the moment it is trying to pair.
            nsdManager.resolveService(serviceInfo, createResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceName == serviceName) onPort(-1)
        }
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            // `isPortAvailable` is the local-device test: a port that cannot be bound on loopback is
            // a port something on this device is already listening on, and an adbd on another machine
            // would be bound nowhere here.
            if (running && isLocalAddress(serviceInfo) && isPortAvailable(serviceInfo.port)) {
                serviceName = serviceInfo.serviceName
                onPort(serviceInfo.port)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        if (!registered) {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        if (registered) {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }

    private fun isLocalAddress(info: NsdServiceInfo): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().any { network ->
            network.inetAddresses.asSequence().any { it.hostAddress == info.host?.hostAddress }
        }
    }.getOrDefault(false)

    private fun isPortAvailable(port: Int): Boolean = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (_: IOException) {
        true
    }

    companion object {

        /** The pairing service, offered only while the pairing dialog is open. */
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"

        /** The service a client connects to after pairing. */
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
    }
}

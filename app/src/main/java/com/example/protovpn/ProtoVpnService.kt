package com.example.protovpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Pluggable tunnel engine. Swap DryRunEngine for a real one
 * (e.g. tun2socks + Xray-core / libv2ray, or WireGuard's GoBackend).
 */
interface TunnelEngine {
    fun start(tun: ParcelFileDescriptor, service: VpnService)
    fun stop()
}

/** Reads packets off the TUN interface and counts them. Does NOT forward them. */
class DryRunEngine : TunnelEngine {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun start(tun: ParcelFileDescriptor, service: VpnService) {
        running.set(true)
        worker = Thread {
            val input = FileInputStream(tun.fileDescriptor)
            val buf = ByteArray(32767)
            try {
                while (running.get()) {
                    val n = input.read(buf)
                    if (n > 0) {
                        bytesSeen.addAndGet(n.toLong())
                        // TODO real engine: hand buf[0..n] to tun2socks / Xray, write replies to tun
                    }
                }
            } catch (e: Exception) {
                if (running.get()) Log.e(TAG, "read failed", e)
            }
        }.also { it.start() }
    }

    override fun stop() {
        running.set(false)
        worker?.interrupt()
    }

    companion object {
        const val TAG = "DryRunEngine"
        val bytesSeen = AtomicLong(0)
    }
}

class ProtoVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var engine: TunnelEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (tun != null) return
        val builder = Builder()
            .setSession("ProtoVPN")
            .setMtu(1500)
            .addAddress("10.8.0.2", 32)      // virtual client IP
            .addRoute("0.0.0.0", 0)          // send all IPv4 through the tunnel
            .addDnsServer("1.1.1.1")

        // Keep this app's own sockets outside the tunnel to avoid routing loops
        // (the engine's connection to the VPN server must not go through the TUN).
        builder.addDisallowedApplication(packageName)

        tun = builder.establish() ?: return
        engine = DryRunEngine().also { it.start(tun!!, this) }
        isRunning = true
    }

    private fun stopVpn() {
        engine?.stop(); engine = null
        tun?.close(); tun = null
        isRunning = false
        stopSelf()
    }

    override fun onRevoke() = stopVpn()
    override fun onDestroy() { stopVpn(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.example.protovpn.STOP"
        @Volatile var isRunning = false
    }
}

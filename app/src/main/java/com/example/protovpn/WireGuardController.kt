package com.example.protovpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress

/**
 * Real tunnel engine built on WireGuard's official Android library.
 *
 * build.gradle (app):
 *   implementation("com.wireguard.android:tunnel:1.0.20230706")
 *   // also needs: android { compileOptions { isCoreLibraryDesugaringEnabled = true } }
 *   //             coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
 *
 * GoBackend ships its OWN VpnService (declared in the library manifest), so with
 * this approach you do NOT use ProtoVpnService. Call VpnService.prepare(context)
 * for the consent dialog first (MainActivity already does), then connect().
 *
 * IMPORTANT: connect()/disconnect() block on I/O. Call them off the main thread
 * (e.g. Dispatchers.IO or a plain Thread).
 */
class WireGuardController(context: Context) {

    private val backend = GoBackend(context.applicationContext)

    private val tunnel = object : Tunnel {
        override fun getName() = "protovpn"
        override fun onStateChange(newState: Tunnel.State) { /* update UI if needed */ }
    }

    data class ServerConfig(
        val clientPrivateKey: String,   // from `wg genkey` on the phone side
        val serverPublicKey: String,    // from the server
        val endpoint: String,           // "203.0.113.10:51820"
        val clientAddress: String = "10.8.0.2/32",
        val dns: String = "1.1.1.1"
    )

    fun connect(c: ServerConfig) {
        val iface = Interface.Builder()
            .addAddress(InetNetwork.parse(c.clientAddress))
            .addDnsServer(InetAddress.getByName(c.dns))
            .parsePrivateKey(c.clientPrivateKey)
            .build()

        val peer = Peer.Builder()
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))   // full tunnel
            .addAllowedIp(InetNetwork.parse("::/0"))
            .setEndpoint(InetEndpoint.parse(c.endpoint))
            .parsePublicKey(c.serverPublicKey)
            .setPersistentKeepalive(25)
            .build()

        val config = Config.Builder().setInterface(iface).addPeer(peer).build()
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun isUp(): Boolean = backend.getState(tunnel) == Tunnel.State.UP
}

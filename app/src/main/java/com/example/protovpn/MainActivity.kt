package com.example.protovpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var timeLeft: TextView
    private lateinit var toggle: Button
    private lateinit var addTime: Button

    private var remainingMs = 30 * 60 * 1000L          // 30 free minutes
    private var timer: CountDownTimer? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) startVpn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER }
        timeLeft = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        addTime = Button(this).apply {
            text = "Add time (watch ad)"
            // TODO: show a rewarded ad here, then call grantTime() in its reward callback
            setOnClickListener { grantTime(15 * 60 * 1000L) }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status); addView(timeLeft); addView(toggle); addView(addTime)
        })
        render()
    }

    private fun onToggle() {
        if (ProtoVpnService.isRunning) {
            startService(Intent(this, ProtoVpnService::class.java)
                .setAction(ProtoVpnService.ACTION_STOP))
            timer?.cancel()
            toggle.postDelayed({ render() }, 300)
        } else {
            // Shows Android's system consent dialog the first time
            val intent = VpnService.prepare(this)
            if (intent != null) vpnPermission.launch(intent) else startVpn()
        }
    }

    private fun startVpn() {
        if (remainingMs <= 0) { status.text = "No time left"; return }
        startService(Intent(this, ProtoVpnService::class.java))
        timer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(ms: Long) { remainingMs = ms; render() }
            override fun onFinish() { remainingMs = 0; onToggle() }
        }.start()
        toggle.postDelayed({ render() }, 300)
    }

    private fun grantTime(ms: Long) {
        remainingMs += ms
        if (ProtoVpnService.isRunning) { timer?.cancel(); startVpn() } else render()
    }

    private fun render() {
        val on = ProtoVpnService.isRunning
        status.text = if (on) "Connected" else "Disconnected"
        toggle.text = if (on) "Disconnect" else "Connect"
        val s = remainingMs / 1000
        timeLeft.text = "Time left: %02d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }
}

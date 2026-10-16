package com.technicalbase.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.technicalbase.jarvis.service.ForegroundService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val status = TextView(this).apply {
            text = "Jarvis v0.1.0\n\nServer: not started"
            textSize = 16f
        }
        layout.addView(status)

        val btn = Button(this).apply {
            text = "Start Jarvis"
            setOnClickListener {
                requestNotifications()
                val i = Intent(this@MainActivity, ForegroundService::class.java)
                i.action = ForegroundService.ACTION_START
                ContextCompat.startForegroundService(this@MainActivity, i)
                status.text = "Jarvis v0.1.0\n\nServer: starting on 127.0.0.1:8766"
            }
        }
        layout.addView(btn)

        setContentView(layout)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }
}

package com.technicalbase.jarvis

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "Jarvis is here.\n\nBuild: v0.1.0 (skeleton)"
            textSize = 18f
            setPadding(48, 96, 48, 48)
        }
        setContentView(tv)
    }
}

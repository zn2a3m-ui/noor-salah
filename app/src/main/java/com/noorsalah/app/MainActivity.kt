package com.noorsalah.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(Color.rgb(8, 45, 35))
        layout.setPadding(24, 24, 24, 24)

        val title = TextView(this)
        title.text = "نور الصلاة"
        title.textSize = 34f
        title.setTextColor(Color.rgb(220, 190, 110))
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)
        subtitle.text = "رفيقك إلى الصلاة والذكر"
        subtitle.textSize = 18f
        subtitle.setTextColor(Color.WHITE)
        subtitle.gravity = Gravity.CENTER

        layout.addView(title)
        layout.addView(subtitle)

        setContentView(layout)
    }
}
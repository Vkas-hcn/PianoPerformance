package com.bdrink.poison.quench.pianoperformance

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bdrink.poison.quench.pianoperformance.databinding.ActivityGuideBinding
import com.bdrink.poison.quench.pianoperformance.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        clickListener()
    }

    private fun clickListener() {
        binding.apply {
            imgSetting.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingActivity::class.java))
            }
            tvStart.setOnClickListener {
                startActivity(Intent(this@MainActivity, PianoPlayerActivity::class.java))
            }
            atvRecord.setOnClickListener {
                startActivity(Intent(this@MainActivity, PianoHistoryActivity::class.java))
            }
        }
    }
}
package com.example.stressdetector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.ActivityMainBinding
import com.example.stressdetector.ui.AnalysisFragment
import com.example.stressdetector.ui.HistoryFragment
import com.example.stressdetector.ui.HomeFragment
import com.example.stressdetector.ui.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(this)

        setupBottomNav()

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }
    }

    private fun setupBottomNav() {
        // Hide History and Profile tabs in offline mode
        if (SessionManager.isOfflineMode()) {
            binding.bottomNav.menu.findItem(R.id.nav_history)?.isVisible = false
            binding.bottomNav.menu.findItem(R.id.nav_profile)?.isVisible = false
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_analysis -> AnalysisFragment()
                R.id.nav_history -> HistoryFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            replaceFragment(fragment)
            true
        }
    }

    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}

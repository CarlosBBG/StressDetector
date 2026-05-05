package com.example.stressdetector.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.stressdetector.BuildConfig
import com.example.stressdetector.R
import com.example.stressdetector.auth.SessionManager
import com.example.stressdetector.databinding.FragmentHomeBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAbout.setOnClickListener {
            openAboutSheet()
        }

        if (SessionManager.isOfflineMode()) {
            binding.txtGreeting.text = "Modo Offline"
            binding.txtAvatarInitial.text = "?"
            // Hide step 5 (history) in offline mode
            binding.step5Container.visibility = View.GONE
            // Show offline info banner
            binding.cardOfflineInfo.visibility = View.VISIBLE
            binding.btnOfflineLogin.setOnClickListener {
                // Clear offline mode and go to login
                SessionManager.setOfflineMode(false)
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        } else {
            val userName = SessionManager.getUserName()
            binding.txtGreeting.text = "Hola, $userName"
            binding.txtAvatarInitial.text = userName.firstOrNull()?.uppercase() ?: "U"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openAboutSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_about, null)
        dialog.setContentView(sheetView)

        (sheetView.parent as View).setBackgroundColor(Color.TRANSPARENT)

        val versionName = BuildConfig.VERSION_NAME

        sheetView.findViewById<TextView>(R.id.txtAppVersion).text = versionName
        dialog.show()
    }
}


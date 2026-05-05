package com.example.stressdetector.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stressdetector.databinding.ItemMeasurementBinding
import com.example.stressdetector.models.MeasurementSummary
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptador para la lista del historial.
 */
class MeasurementAdapter(
    private val onClick: (MeasurementSummary) -> Unit
) : ListAdapter<MeasurementSummary, MeasurementAdapter.ViewHolder>(DiffCallback) {

    private val colorBajo = Color.parseColor("#10B981")
    private val colorModeradoBajo = Color.parseColor("#3B82F6")
    private val colorModeradoAlto = Color.parseColor("#F59E0B")
    private val colorAlto = Color.parseColor("#EF4444")

    private val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val outputFormat = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale("es"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMeasurementBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder que pinta cada tarjeta de medicion.
     */
    inner class ViewHolder(
        private val binding: ItemMeasurementBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Llena la tarjeta con datos y estilos.
         */
        fun bind(item: MeasurementSummary) {
            val color = getColorForLevel(item.stressLevel)

            // Indicator dot color
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            binding.viewIndicator.background = dotBg

            // Label
            binding.txtLabel.text = item.label
            binding.txtLabel.setTextColor(color)

            // Level badge
            val badgeBg = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 12f
            }
            binding.txtLevelBadge.background = badgeBg
            binding.txtLevelBadge.text = item.stressLevel

            // Probability text
            val pct = (item.probability * 100).toInt()
            binding.txtProbability.text = "$pct%"

            // Progress bar width
            binding.viewProgressBar.post {
                val parent = binding.viewProgressBar.parent as View
                val parentWidth = parent.width
                val targetWidth = (parentWidth * item.probability).toInt()
                val params = binding.viewProgressBar.layoutParams
                params.width = targetWidth
                binding.viewProgressBar.layoutParams = params

                val barBg = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 8f
                }
                binding.viewProgressBar.background = barBg
            }

            // Date
            try {
                val date = inputFormat.parse(item.timestamp)
                binding.txtDate.text = if (date != null) outputFormat.format(date) else item.timestamp
            } catch (e: Exception) {
                binding.txtDate.text = item.timestamp
            }

            // Heart rate
            if (item.hrBpm != null && !item.hrBpm.isNaN()) {
                binding.txtHr.text = "♥ ${"%.0f".format(item.hrBpm)} BPM"
                binding.txtHr.visibility = View.VISIBLE
            } else {
                binding.txtHr.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(item) }
        }

        /**
         * Devuelve un color segun el nivel de estres.
         */
        private fun getColorForLevel(level: String): Int = when (level) {
            "Alto" -> colorAlto
            "Moderado-Alto" -> colorModeradoAlto
            "Moderado-Bajo" -> colorModeradoBajo
            else -> colorBajo
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MeasurementSummary>() {
        override fun areItemsTheSame(old: MeasurementSummary, new: MeasurementSummary) =
            old.id == new.id

        override fun areContentsTheSame(old: MeasurementSummary, new: MeasurementSummary) =
            old == new
    }
}

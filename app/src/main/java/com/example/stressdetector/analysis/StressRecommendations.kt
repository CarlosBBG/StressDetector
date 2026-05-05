package com.example.stressdetector.analysis

/**
 * Datos estructurados de recomendaciones para la UI.
 */
data class Recommendation(
    val techniques: List<String>,
    val tips: List<String>,
    val patternName: String?,
    val patternTip: String?
)

/**
 * Recomendaciones locales para reducir el nivel de estrés,
 * basadas en el nivel detectado y el patrón de estrés.
 */
object StressRecommendations {

    /**
     * Devuelve las recomendaciones segun nivel y patron detectado.
     */
    fun getRecommendation(stressLevel: String, pattern: String): Recommendation {
        val patternInfo = getPatternInfo(pattern)
        return Recommendation(
            techniques = getTechniques(stressLevel),
            tips = getTips(stressLevel),
            patternName = patternInfo?.first,
            patternTip = patternInfo?.second
        )
    }

    /**
     * Texto corto que resume el estado actual.
     */
    fun getSubtitle(stressLevel: String): String {
        return when (stressLevel) {
            "Alto" -> "Tu nivel de estrés es alto. Sigue estas recomendaciones antes de continuar."
            "Moderado-Alto" -> "Tu nivel de estrés es moderado-alto. Te sugerimos estas acciones."
            "Moderado-Bajo" -> "Tu nivel de estrés es moderado. Estas sugerencias pueden ayudarte."
            else -> "Tu nivel de estrés es bajo. Mantén tus buenos hábitos."
        }
    }

    /**
     * Lista de tecnicas sugeridas segun el nivel.
     */
    private fun getTechniques(stressLevel: String): List<String> {
        return when (stressLevel) {
            "Alto" -> listOf(
                "Respiración 4-7-8: inhala 4s, sostén 7s, exhala 8s. Repite 4 veces.",
                "Relajación muscular progresiva: tensa y relaja cada grupo muscular durante 5s.",
                "Meditación guiada de al menos 10 minutos.",
                "Técnica de grounding 5-4-3-2-1: identifica 5 cosas que ves, 4 que tocas, 3 que oyes, 2 que hueles y 1 que saboreas."
            )
            "Moderado-Alto" -> listOf(
                "Respiración diafragmática: inhala profundo por la nariz 4s, exhala por la boca 6s. Repite 5 veces.",
                "Estiramientos de cuello y hombros durante 5 minutos.",
                "Ejercicio de visualización: imagina un lugar tranquilo durante 3 minutos."
            )
            "Moderado-Bajo" -> listOf(
                "Respiración cuadrada: inhala 4s, sostén 4s, exhala 4s, sostén 4s. Repite 3 veces.",
                "Pausa activa: levántate y camina 2 minutos."
            )
            else -> listOf(
                "Mantén tu rutina de bienestar actual.",
                "Respiración consciente: 3 respiraciones profundas cuando cambies de actividad."
            )
        }
    }

    /**
     * Consejos practicos segun el nivel.
     */
    private fun getTips(stressLevel: String): List<String> {
        return when (stressLevel) {
            "Alto" -> listOf(
                "Toma un descanso de al menos 15 minutos antes de continuar cualquier actividad demandante.",
                "Hidrátate: bebe un vaso de agua.",
                "Evita cafeína y estimulantes por las próximas 2 horas.",
                "Considera hablar con alguien de confianza sobre cómo te sientes.",
                "Si el estrés alto persiste, consulta con un profesional de salud."
            )
            "Moderado-Alto" -> listOf(
                "Toma un descanso de 10 minutos.",
                "Hidrátate y come algo ligero si no lo has hecho.",
                "Reduce las distracciones y enfócate en una tarea a la vez.",
                "Intenta hacer actividad física moderada (caminar, estirar) durante 15 minutos."
            )
            "Moderado-Bajo" -> listOf(
                "Mantén una postura cómoda y ergonómica.",
                "Organiza tus tareas pendientes para reducir la carga mental.",
                "Duerme entre 7 y 8 horas esta noche."
            )
            else -> listOf(
                "Sigue con tu rutina actual, tus niveles son saludables.",
                "Mantén hábitos de sueño regulares.",
                "La actividad física regular ayuda a mantener el estrés bajo control."
            )
        }
    }

    /**
     * Informacion extra segun el patron detectado.
     */
    private fun getPatternInfo(pattern: String): Pair<String, String>? {
        return when (pattern) {
            "Estrés sostenido" -> Pair(
                "Patrón: Estrés Sostenido",
                "Se detectó estrés continuo durante toda la medición. Prioriza tomarte un descanso prolongado antes de retomar actividades demandantes."
            )
            "Picos de estrés aislados" -> Pair(
                "Patrón: Picos Aislados",
                "Se detectaron picos puntuales de estrés elevado. Intenta identificar qué situaciones o estímulos los provocaron para evitarlos o manejarlos mejor."
            )
            "Estrés intermitente" -> Pair(
                "Patrón: Estrés Intermitente",
                "Tu estrés fluctúa entre períodos de calma y tensión. Intenta identificar los momentos de mayor tensión y establece pausas preventivas."
            )
            "Alta variabilidad" -> Pair(
                "Patrón: Alta Variabilidad",
                "Tu estado varía considerablemente. Establece una rutina de pausas cada 30 minutos para mantener un nivel más estable."
            )
            else -> null
        }
    }
}

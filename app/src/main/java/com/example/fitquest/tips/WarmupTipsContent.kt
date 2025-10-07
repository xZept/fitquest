package com.example.fitquest.tips

object WarmupTipsContent {

    /**
     * @param focus "upper" | "legs" | "full" (default full if null/unknown)
     */
    fun forFocus(focus: String?): List<TipPage> = when (focus?.lowercase()) {
        "upper", "push", "pull" -> upper()
        "legs", "lower" -> legs()
        else -> fullBody()
    }

    private fun bullets(vararg lines: String) = lines.joinToString("\n") { "• $it" }

    private fun upper() = listOf(
        TipPage(
            title = "Upper — Tissue Prep (2–3 min)",
            body = bullets(
                "30–60s shoulder circles each way",
                "Band pull-aparts ×20",
                "Light cuff external rotations ×15"
            )
        ),
        TipPage(
            title = "Scap & T-spine Activation",
            body = bullets(
                "Scap push-ups ×10–15",
                "Wall slides ×10",
                "Cat-cow ×6–8"
            )
        ),
        TipPage(
            title = "Ramp-up Sets",
            body = bullets(
                "2–3 lighter sets of your first exercise",
                "Increase load gradually to working weight",
                "Keep RPE ≤ 5 while ramping"
            )
        )
    )

    private fun legs() = listOf(
        TipPage(
            title = "Lower — Dynamic (3–4 min)",
            body = bullets(
                "Leg swings (front/side) ×10/leg",
                "Walking lunges ×10/side",
                "Ankle rocks ×10"
            )
        ),
        TipPage(
            title = "Hips & Knees",
            body = bullets(
                "Hip airplanes ×6/side",
                "Cossack squats ×8/side",
                "Glute bridges ×12"
            )
        ),
        TipPage(
            title = "Ramp-up Sets",
            body = bullets(
                "2–3 lighter sets of squats/hinge",
                "Groove depth & bracing",
                "RPE ≤ 5 while ramping"
            )
        )
    )

    private fun fullBody() = listOf(
        TipPage(
            title = "Full Body — Dynamic (3–4 min)",
            body = bullets(
                "Knee hugs to lunge ×6/side",
                "Toy soldiers ×10/side",
                "Arm circles ×20"
            )
        ),
        TipPage(
            title = "Core & Posture",
            body = bullets(
                "Dead bug ×8/side",
                "Bird-dog ×8/side",
                "Scap retraction holds 2×20s"
            )
        ),
        TipPage(
            title = "Ramp-up Sets",
            body = bullets(
                "2–3 lighter sets of 1st exercise",
                "Tempo: smooth, controlled",
                "RPE ≤ 5 while ramping"
            )
        )
    )
}

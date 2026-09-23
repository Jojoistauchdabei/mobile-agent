package com.mobileagent

/** Laya-Entscheidung: typisierte Antworten, kalibrierte Confidence. */
data class RouteDecision(
    val intent: String, // chat | search | device_action | unsafe
    val needsSearch: Boolean,
    val needsAction: Boolean,
    val blocked: Boolean,
    val confidence: Double,
)

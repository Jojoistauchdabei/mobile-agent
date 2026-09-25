package com.mobileagent

data class RouteDecision(
    val intent: String,
    val needsSearch: Boolean,
    val needsAction: Boolean,
    val blocked: Boolean,
    val confidence: Double,
    val source: String = "heuristic",
)

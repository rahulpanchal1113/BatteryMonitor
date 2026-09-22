package com.example.data.model

data class ConnectionDiagnosticIssue(
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val disconnectCount: Int,
    val windowSeconds: Int,
    val isDeviceSteady: Boolean,
    val title: String,
    val summary: String,
    val details: String,
    val recommendations: List<String>,
    val isDismissed: Boolean = false
)

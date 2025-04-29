package com.example.detoxapp

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GroupData(
    val groupName: String = "",
    val members: Map<String, MemberData> = emptyMap(),
    val groupID: String = ""
)

data class MemberData(
    val name: String = "",
    val phase: Int = 0,
    val challengesCompleted: Map<String, Boolean> = emptyMap(),
    val phaseStartDate: Timestamp = Timestamp.now(),
    val etapa: String = ""
)


data class AppUsage(
    val appName: String,
    val usageTime: Long
)

data class ReductionConfig(
    val porcentajeTotal: Int,
    val porcentajeSemanal: Int
)

data class DailyUsage(
    val date: String,
    val apps: Map<String, Long>
)

data class RankingEntry(
    val userId: String,
    val name: String,
    val averageDailyUsage: Long
)

data class PhaseInfo(
    val phase: Int,
    val duration: Int,
    val description: String
)

data class Challenge(
    val title: String,
    val description: String
)

fun formatTime(millis: Long): String {
    val hours = millis / (1000 * 60 * 60)
    val minutes = (millis / (1000 * 60)) % 60
    return "${hours}h ${minutes}min"
}

fun getTodayDate(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
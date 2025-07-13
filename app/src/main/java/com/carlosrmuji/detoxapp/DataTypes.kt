package com.carlosrmuji.detoxapp

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import java.util.UUID


data class Plan(
    val id: String,
    val title: String,
    val price: String,
    val features: List<String>
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?
)

data class BlockedApp(
    val app: InstalledAppInfo,
    val restrictions: List<AppRestriction>
)

data class AppRestriction(
    val days: Set<DayOfWeek>,
    val from: LocalTime,
    val to: LocalTime
)

fun AppRestriction.includes(now: LocalDateTime): Boolean {
    val nowTime = now.toLocalTime()
    return days.contains(now.dayOfWeek) && nowTime.isAfter(from) && nowTime.isBefore(to)
}

data class AIChatMessageData(
    val text: String,
    val sender: String
)

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
    val averageDailyUsage: Long,
    val phase: Int,
    val phaseAverageUsage: Long?,
    val percentageChange: Double?,
    val photoUrl: String? = null
)

data class PhaseInfo(
    val phase: Int,
    val duration: Int,
    val description: String
)

data class Challenge(
    val title: String,
    val description: String,
    val isManuallyCheckable: Boolean
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val timestamp: String,
    val text: String,
    val name: String
)

fun formatTime(millis: Long): String {
    val hours = millis / (1000 * 60 * 60)
    val minutes = (millis / (1000 * 60)) % 60
    return "${hours}h ${minutes}min"
}

fun getTodayDate(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
package com.example.data.repository

import android.util.Log
import com.example.data.model.CampusAnnouncement
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.DirectoryOffice
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val AI_TAG = "CampusAiRepository"
private const val CAMPUS_AI_MODEL = "gemini-3.8-flash"

class CampusAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = CAMPUS_AI_MODEL,
            systemInstruction = content {
                text(
                    """
                    You are CampusBot, a concise, accurate, minimalist AI campus assistant for university students and campus administrators.
                    Use the live campus context provided in each turn (student's personal class schedule, upcoming exams, live campus announcements, and campus directory offices) to give specific, practical answers.
                    Rules:
                    - Keep answers crisp, structured, and directly actionable.
                    - Cite exact course codes, room numbers, office hours, phone numbers, or alert headlines from the provided live context when relevant.
                    - If the user asks about a campus issue that needs human intervention (e.g., Wi-Fi outage, broken classroom equipment, lost ID), answer helpfully and remind them they can file a ticket under Campus > Tickets.
                    - Never invent private student records or campus hours not present in the context; state clearly when information isn't listed yet.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamCampusReply(
        userPrompt: String,
        recentHistory: List<ChatMessage>,
        scheduleItems: List<ScheduleItem>,
        announcements: List<CampusAnnouncement>,
        directory: List<DirectoryOffice>
    ): Flow<String> = flow {
        val contextSnapshot = buildCampusContext(scheduleItems, announcements, directory)
        val historyText = recentHistory.takeLast(8).joinToString("\n") { msg ->
            val role = if (msg.parsedSender == ChatSender.USER) "Student" else "CampusBot"
            "$role: ${msg.content.take(400)}"
        }

        val fullPrompt = buildString {
            appendLine("=== LIVE CAMPUS CONTEXT ===")
            appendLine(contextSnapshot)
            if (historyText.isNotBlank()) {
                appendLine("\n=== RECENT CONVERSATION ===")
                appendLine(historyText)
            }
            appendLine("\n=== CURRENT STUDENT QUESTION ===")
            appendLine(userPrompt.trim())
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("CampusBot received an empty response. Please try again.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "CampusBot AI stream timed out", e)
            throw IllegalStateException("CampusBot timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "CampusBot AI stream failed", e)
            throw e
        }
    }

    private fun buildCampusContext(
        scheduleItems: List<ScheduleItem>,
        announcements: List<CampusAnnouncement>,
        directory: List<DirectoryOffice>
    ): String {
        val classes = scheduleItems.filter { it.parsedType == ScheduleItemType.CLASS }
        val exams = scheduleItems.filter { it.parsedType == ScheduleItemType.EXAM }
            .sortedBy { it.examEpochMillis }

        return buildString {
            appendLine("Weekly Classes (${classes.size}):")
            if (classes.isEmpty()) {
                appendLine("- No classes added yet.")
            } else {
                classes.take(15).forEach { c ->
                    appendLine(
                        "- ${c.courseCode} ${c.title}: ${ScheduleFormatter.dayFull(c.dayOfWeek)} " +
                            "${ScheduleFormatter.formatMinutes(c.startTimeMinutes)}-${ScheduleFormatter.formatMinutes(c.endTimeMinutes)}" +
                            (if (c.location.isNotBlank()) " @ ${c.location}" else "") +
                            (if (c.instructor.isNotBlank()) " (${c.instructor})" else "")
                    )
                }
            }

            appendLine("\nUpcoming Exams & Deadlines (${exams.size}):")
            if (exams.isEmpty()) {
                appendLine("- No exams added yet.")
            } else {
                exams.take(10).forEach { ex ->
                    appendLine(
                        "- ${ex.courseCode} ${ex.title}: ${ScheduleFormatter.formatExamDate(ex.examEpochMillis)} " +
                            "(${ScheduleFormatter.countdownLabel(ex.examEpochMillis)})" +
                            (if (ex.location.isNotBlank()) " @ ${ex.location}" else "")
                    )
                }
            }

            appendLine("\nRecent Campus Announcements (${announcements.size}):")
            if (announcements.isEmpty()) {
                appendLine("- No active campus announcements.")
            } else {
                announcements.take(6).forEach { a ->
                    appendLine("- [${a.severity}] ${a.title} (${a.department}): ${a.body}")
                }
            }

            appendLine("\nCampus Directory Offices (${directory.size}):")
            if (directory.isEmpty()) {
                appendLine("- Directory currently empty.")
            } else {
                directory.take(12).forEach { d ->
                    appendLine(
                        "- ${d.name} [${d.parsedCategory.label}]: ${d.building} ${d.room} | Hours: ${d.hours}" +
                            (if (d.phone.isNotBlank()) " | Phone: ${d.phone}" else "") +
                            (if (d.email.isNotBlank()) " | Email: ${d.email}" else "")
                    )
                }
            }
        }
    }
}

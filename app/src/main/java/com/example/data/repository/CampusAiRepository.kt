package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.CampusAnnouncement
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.ClassroomAssignment
import com.example.data.model.DirectoryOffice
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val AI_TAG = "CampusAiRepository"
// Primary high-throughput free-tier Gemini 3.5 Flash-Lite model with automatic Gemini 3.8 Flash fallback
private const val FREE_TIER_FLASH_MODEL = "gemini-3.5-flash-lite"
private const val FALLBACK_FLASH_MODEL = "gemini-3.8-flash"
private const val MAX_INLINE_BYTES = 18 * 1024 * 1024 // Keep safely under 20MB inline limit
private const val MAX_RAW_IMAGE_BYTES = 10 * 1024 * 1024 // Send crisp untouched JPEG/PNG/WebP directly up to 10MB
private const val MAX_SCALED_IMAGE_DIM = 3072

class CampusAiRepository(
    private val knowledgeBase: CampusKnowledgeBase
) {

    private val chatSystemInstruction by lazy {
        content {
            text(
                """
                You are CampusBot, a polished, concise campus assistant grounded on official university data (faculty, B1-B3 rooms, sections/mentors, IoT 2B timetable) plus the student's schedule, exams, alerts, and offices.
                Style Rules:
                - Write clearly and professionally. Use short section titles and "Label: Value" lines for rooms, faculty, or contacts.
                - For schedules or lists, use clean numbered lines (1., 2., 3.) with "·" separators (e.g. "09:30–10:15 · Technical Skill Development · Room B3 LG 4.6 (MNC)").
                - Avoid nested bullets, repeated dashes, or raw asterisk clutter.
                - Use exact room codes, blocks, floors, phones, emails, and times from the context. Never invent missing details.
                """.trimIndent()
            )
        }
    }

    private val chatGenerationConfig by lazy {
        generationConfig {
            temperature = 0.2f
            maxOutputTokens = 550
        }
    }

    private val primaryChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FREE_TIER_FLASH_MODEL,
            generationConfig = chatGenerationConfig,
            systemInstruction = chatSystemInstruction
        )
    }

    private val fallbackChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_FLASH_MODEL,
            generationConfig = chatGenerationConfig,
            systemInstruction = chatSystemInstruction
        )
    }

    fun prewarm() {
        // Trigger lazy model creation off the UI thread so first prompt starts immediately
        primaryChatModel
    }

    private val scheduleExtractionConfig by lazy {
        // Compact keys reduce token usage by >50% so dense 45-slot weekly grids never truncate
        val itemSchema = Schema.obj(
            properties = mapOf(
                "t" to Schema.enumeration(
                    values = listOf("C", "E"),
                    description = "C for recurring weekly class/lab/tutorial, E for dated exam/midterm/final/deadline."
                ),
                "c" to Schema.string(
                    description = "Short 2-10 char course code if printed, else empty string."
                ),
                "n" to Schema.string(
                    description = "Subject, lab, training, or exam title."
                ),
                "d" to Schema.integer(
                    description = "Day of week integer: 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday."
                ),
                "s" to Schema.string(
                    description = "Start time in 24-hour HH:mm format, e.g. 09:30 or 13:15."
                ),
                "e" to Schema.string(
                    description = "End time in 24-hour HH:mm format, e.g. 10:15 or 14:00."
                ),
                "dt" to Schema.string(
                    description = "For E (exam) items only: ISO date YYYY-MM-DD. Empty string for C items."
                ),
                "r" to Schema.string(
                    description = "Room, hall, or lab if shown (empty string if '-' or blank)."
                ),
                "f" to Schema.string(
                    description = "Faculty name or initials if shown (empty string if '-' or blank)."
                )
            ),
            optionalProperties = listOf("c", "dt", "r", "f")
        )

        val rootSchema = Schema.obj(
            properties = mapOf(
                "items" to Schema.array(
                    items = itemSchema,
                    description = "Every non-break class slot and exam entry found in the schedule."
                )
            )
        )

        generationConfig {
            temperature = 0.1f
            maxOutputTokens = 8192
            responseMimeType = "application/json"
            responseSchema = rootSchema
        }
    }

    private val primaryExtractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FREE_TIER_FLASH_MODEL,
            generationConfig = scheduleExtractionConfig
        )
    }

    private val fallbackExtractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_FLASH_MODEL,
            generationConfig = scheduleExtractionConfig
        )
    }

    fun streamCampusReply(
        userPrompt: String,
        recentHistory: List<ChatMessage>,
        scheduleItems: List<ScheduleItem>,
        announcements: List<CampusAnnouncement>,
        directory: List<DirectoryOffice>,
        pendingAssignments: List<ClassroomAssignment> = emptyList()
    ): Flow<String> = flow {
        val recentUserTurns = recentHistory
            .filter { it.parsedSender == ChatSender.USER }
            .takeLast(2)
            .map { it.content }

        val relevantContext = withContext(Dispatchers.Default) {
            knowledgeBase.buildRelevantContextForQuery(userPrompt, recentUserTurns)
        }
        val liveContext = buildLiveFirestoreContext(
            userPrompt = userPrompt,
            scheduleItems = scheduleItems,
            announcements = announcements,
            directory = directory,
            pendingAssignments = pendingAssignments
        )
        val historyText = recentHistory.takeLast(3).joinToString("\n") { msg ->
            val role = if (msg.parsedSender == ChatSender.USER) "U" else "A"
            "$role: ${msg.content.replace("\n", " ").take(160)}"
        }

        val fullPrompt = buildString {
            appendLine(relevantContext)
            if (liveContext.isNotBlank()) {
                appendLine()
                appendLine("USER DATA:")
                appendLine(liveContext)
            }
            if (historyText.isNotBlank()) {
                appendLine("\nCHAT:")
                appendLine(historyText)
            }
            appendLine("\nQ: ${userPrompt.trim()}")
        }

        val candidates: List<Pair<String, GenerativeModel>> = listOf(
            FREE_TIER_FLASH_MODEL to primaryChatModel,
            FALLBACK_FLASH_MODEL to fallbackChatModel,
            "$FREE_TIER_FLASH_MODEL (retry)" to primaryChatModel
        )

        var lastError: Throwable? = null
        for ((index, pair) in candidates.withIndex()) {
            val (modelLabel, activeModel) = pair
            if (index == 2) {
                delay(700L)
            }
            val accumulated = StringBuilder()
            try {
                withTimeout(45_000L) {
                    activeModel.generateContentStream(fullPrompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(accumulated.toString())
                        }
                    }
                }
                if (accumulated.isNotBlank()) {
                    return@flow
                }
                lastError = IllegalStateException("Model returned an empty response.")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(AI_TAG, "Model $modelLabel timed out", e)
                    lastError = IllegalStateException("CampusBot timed out. Please try again.", e)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(AI_TAG, "Model $modelLabel failed: ${e.message}", e)
                lastError = e
                if (accumulated.isNotBlank()) {
                    return@flow
                }
                if (!isTransientDemandError(e) && index >= 1) {
                    throw e
                }
            }
        }

        Log.e(AI_TAG, "All CampusBot models failed", lastError)
        throw (lastError ?: IllegalStateException("CampusBot could not generate a reply right now. Please try again."))
    }

    suspend fun extractScheduleFromUri(
        appContext: Context,
        uri: Uri,
        defaultLeadMinutes: Int
    ): List<ScheduleItem> {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val extractionPrompt = """
            Read the attached college class timetable or exam schedule (image or PDF) and extract EVERY class slot and exam entry as JSON.
            Today's date is $todayIso.

            How to read timetable layouts accurately:
            1. Horizontal multi-day sub-table grid (common college format): Each day section (Monday, Tuesday, Wednesday — even if partially cropped like "Wednesda", Thursday, Friday, Saturday) has its own table. The top header row lists period numbers and time ranges (e.g. "1st 9:30-10:15", "5th 13:15-14:00", "9th 16:15-17:00"), and the rows below are labeled "Subject", "Faculty", and "Room". For EVERY day and EVERY non-break period column, align vertically to extract Subject -> n, Faculty -> f, Room -> r, header start/end times -> s/e, and day -> d (1=Mon..7=Sun).
            2. Standard row-per-day or column-per-day timetables: Extract every non-empty class/lab/tutorial cell with its day, start time, end time, subject, room, and faculty.
            3. Exam routines / date sheets / deadlines: Set t="E", dt="YYYY-MM-DD" (infer nearest upcoming year if year is omitted), d matching that date (1=Mon..7=Sun), plus s, e, n, r.

            Strict rules:
            - Set t="C" for recurring weekly classes/labs/tutorials/training/EAA, and t="E" for dated exams.
            - Skip BREAK, LUNCH, RECESS, or blank cells.
            - If Faculty or Room is "-" or blank, output "" (empty string).
            - Format all times as 24-hour "HH:mm" with leading zeros (e.g. "9:30" -> "09:30", "1:15 PM" -> "13:15", "16:15" -> "16:15").
            - Extract ALL days visible in the image/PDF — do not stop after the first day.
            - Return ONLY entries genuinely present in the file. If no schedule exists, return {"items":[]}.
        """.trimIndent()

        val mediaPayload = withContext(Dispatchers.IO) {
            prepareMediaPayload(appContext, uri)
        }

        val inputContent = content {
            inlineData(mediaPayload.bytes, mediaPayload.mimeType)
            text(extractionPrompt)
        }

        val candidates: List<Pair<String, GenerativeModel>> = listOf(
            FREE_TIER_FLASH_MODEL to primaryExtractionModel,
            FALLBACK_FLASH_MODEL to fallbackExtractionModel,
            "$FREE_TIER_FLASH_MODEL (retry)" to primaryExtractionModel
        )

        var lastError: Throwable? = null
        var sawEmptyValidResponse = false

        for ((index, pair) in candidates.withIndex()) {
            val (modelLabel, activeModel) = pair
            if (index == 2) delay(1_200L)
            try {
                val response = withTimeout(90_000L) {
                    activeModel.generateContent(inputContent)
                }
                val text = response.text?.trim()
                if (text.isNullOrBlank()) {
                    lastError = IllegalStateException("The AI service returned an empty response.")
                    continue
                }
                val parsed = parseExtractedScheduleJson(text, defaultLeadMinutes)
                if (parsed.isNotEmpty()) {
                    return parsed
                }
                sawEmptyValidResponse = true
                Log.w(AI_TAG, "Model $modelLabel parsed 0 items; trying next candidate if available")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(AI_TAG, "Schedule extraction timed out on $modelLabel", e)
                    lastError = IllegalStateException("Schedule scan timed out. Please try again with a clearer file.", e)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(AI_TAG, "Schedule extraction failed on $modelLabel: ${e.message}", e)
                lastError = e
            }
        }

        if (sawEmptyValidResponse) {
            return emptyList()
        }

        throw (lastError
            ?: IllegalStateException("Could not extract schedule from file. Please try again."))
    }

    private data class ExtractedMediaPayload(
        val bytes: ByteArray,
        val mimeType: String
    )

    private fun prepareMediaPayload(context: Context, uri: Uri): ExtractedMediaPayload {
        val resolver = context.contentResolver
        val rawBytes = readAllBytesSinglePass(resolver, uri)
            ?: throw IllegalStateException("Could not open the selected file. Try picking it again from Gallery or Files.")

        if (rawBytes.isEmpty()) {
            throw IllegalStateException("The selected file is empty.")
        }

        val detectedMime = detectMimeType(rawBytes, resolver.getType(uri), uri.toString())

        if (detectedMime == "application/pdf") {
            if (rawBytes.size > MAX_INLINE_BYTES) {
                throw IllegalStateException("PDF is larger than 18 MB. Please select a smaller schedule file.")
            }
            return ExtractedMediaPayload(rawBytes, "application/pdf")
        }

        // Send original crisp JPEG/PNG/WebP bytes untouched when <= 10 MB so small timetable text stays 100% sharp
        val directlySupportedImage = detectedMime == "image/jpeg" ||
            detectedMime == "image/png" ||
            detectedMime == "image/webp"
        if (directlySupportedImage && rawBytes.size <= MAX_RAW_IMAGE_BYTES) {
            return ExtractedMediaPayload(rawBytes, detectedMime)
        }

        // Otherwise decode from the already-read byte array (never re-opening the content URI) and gently scale
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Unsupported file format. Please select a JPG, PNG, WebP image or a PDF.")
        }

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_SCALED_IMAGE_DIM ||
            bounds.outHeight / (sampleSize * 2) >= MAX_SCALED_IMAGE_DIM
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw IllegalStateException("Could not decode the selected schedule image.")

        val maxDim = maxOf(decoded.width, decoded.height)
        val finalBitmap = if (maxDim > MAX_SCALED_IMAGE_DIM) {
            val scale = MAX_SCALED_IMAGE_DIM.toFloat() / maxDim.toFloat()
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val outStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
        finalBitmap.recycle()
        val jpegBytes = outStream.toByteArray()

        if (jpegBytes.size > MAX_INLINE_BYTES) {
            throw IllegalStateException("Image is too large even after compression. Please crop or use a smaller image.")
        }
        return ExtractedMediaPayload(jpegBytes, "image/jpeg")
    }

    private fun readAllBytesSinglePass(
        resolver: android.content.ContentResolver,
        uri: Uri
    ): ByteArray? {
        runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { if (it.isNotEmpty()) return it }

        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.createInputStream().use { it.readBytes() }
            }
        }.getOrNull()?.let { if (it.isNotEmpty()) return it }

        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            }
        }.getOrNull()?.let { if (it.isNotEmpty()) return it }

        return null
    }

    private fun detectMimeType(bytes: ByteArray, resolverMime: String?, uriString: String): String {
        if (bytes.size >= 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF

            // %PDF
            if (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46) return "application/pdf"
            // JPEG FF D8 FF
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "image/jpeg"
            // PNG 89 50 4E 47
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "image/png"
            // WebP RIFF....WEBP
            if (bytes.size >= 12 &&
                b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                bytes[8].toInt() == 0x57 && bytes[9].toInt() == 0x45 &&
                bytes[10].toInt() == 0x42 && bytes[11].toInt() == 0x50
            ) {
                return "image/webp"
            }
        }
        val cleanResolver = resolverMime?.lowercase()?.trim().orEmpty()
        if (cleanResolver == "application/pdf" || uriString.lowercase().endsWith(".pdf")) {
            return "application/pdf"
        }
        if (cleanResolver.startsWith("image/")) {
            return cleanResolver
        }
        return "image/jpeg"
    }

    private fun parseExtractedScheduleJson(
        jsonString: String,
        defaultLeadMinutes: Int
    ): List<ScheduleItem> {
        val cleaned = stripMarkdownCodeFences(jsonString)
        val itemObjects: List<JSONObject> = try {
            val root = JSONObject(cleaned)
            val arr = root.optJSONArray("items")
            if (arr != null) {
                List(arr.length()) { idx -> arr.optJSONObject(idx) }.filterNotNull()
            } else {
                emptyList()
            }
        } catch (e: JSONException) {
            Log.w(AI_TAG, "Primary JSON parse failed, attempting truncated-object recovery", e)
            recoverJsonItemObjects(cleaned)
        }

        val safeLead = defaultLeadMinutes.coerceIn(5, 1440)

        return buildList {
            for (obj in itemObjects) {
                val title = (obj.optString("n", "").ifBlank { obj.optString("title", "") })
                    .trim()
                    .take(100)
                if (title.isEmpty()) continue
                if (title.equals("BREAK", ignoreCase = true) ||
                    title.equals("LUNCH", ignoreCase = true) ||
                    title.equals("RECESS", ignoreCase = true) ||
                    title == "-" || title == "--"
                ) continue

                val rawType = (obj.optString("t", "").ifBlank { obj.optString("itemType", "C") })
                    .trim()
                    .uppercase()
                val itemType = if (rawType == "E" || rawType == "EXAM") {
                    ScheduleItemType.EXAM
                } else {
                    ScheduleItemType.CLASS
                }

                val rawCode = (obj.optString("c", "").ifBlank { obj.optString("courseCode", "") }).trim()
                val courseCode = sanitizeOptionalField(rawCode).ifBlank { deriveShortCode(title) }.take(24)

                val rawStart = obj.optString("s", "").ifBlank { obj.optString("startTime24h", "") }
                val rawEnd = obj.optString("e", "").ifBlank { obj.optString("endTime24h", "") }
                val startMin = parse24hMinutes(rawStart) ?: 540
                val parsedEnd = parse24hMinutes(rawEnd)
                val endMin = when {
                    parsedEnd != null && parsedEnd > startMin -> parsedEnd
                    else -> (startMin + 45).coerceAtMost(1439)
                }

                val rawDay = if (obj.has("d")) obj.optInt("d", 1) else obj.optInt("dayOfWeek", 1)
                var dayOfWeek = rawDay.coerceIn(1, 7)
                var examEpoch = 0L

                if (itemType == ScheduleItemType.EXAM) {
                    val iso = (obj.optString("dt", "").ifBlank { obj.optString("examDateIso", "") }).trim()
                    val parsedCal = parseExamIsoDate(iso, startMin)
                    if (parsedCal != null) {
                        examEpoch = parsedCal.timeInMillis
                        dayOfWeek = calendarDayToMondayBased(parsedCal.get(Calendar.DAY_OF_WEEK))
                    } else {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 3)
                            set(Calendar.HOUR_OF_DAY, startMin / 60)
                            set(Calendar.MINUTE, startMin % 60)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        examEpoch = cal.timeInMillis
                    }
                }

                val room = sanitizeOptionalField(
                    obj.optString("r", "").ifBlank { obj.optString("location", "") }
                ).take(100)
                val faculty = sanitizeOptionalField(
                    obj.optString("f", "").ifBlank { obj.optString("instructor", "") }
                ).take(80)

                add(
                    ScheduleItem(
                        itemType = itemType.name,
                        courseCode = courseCode,
                        title = title,
                        dayOfWeek = dayOfWeek,
                        startTimeMinutes = startMin,
                        endTimeMinutes = endMin,
                        examEpochMillis = examEpoch,
                        location = room,
                        instructor = faculty,
                        reminderEnabled = true,
                        reminderLeadMinutes = safeLead
                    )
                )
            }
        }
    }

    private fun stripMarkdownCodeFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val withoutStart = if (firstNewline >= 0) trimmed.substring(firstNewline + 1) else trimmed.removePrefix("```")
        return withoutStart.removeSuffix("```").trim()
    }

    private fun recoverJsonItemObjects(raw: String): List<JSONObject> {
        val itemsIdx = raw.indexOf("\"items\"")
        val arrayStart = if (itemsIdx >= 0) raw.indexOf('[', itemsIdx) else raw.indexOf('[')
        if (arrayStart < 0) return emptyList()

        val recovered = mutableListOf<JSONObject>()
        var depth = 0
        var inString = false
        var escaped = false
        var objStart = -1

        for (i in (arrayStart + 1) until raw.length) {
            val ch = raw[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        val candidate = raw.substring(objStart, i + 1)
                        runCatching { JSONObject(candidate) }.getOrNull()?.let { recovered.add(it) }
                        objStart = -1
                    }
                }
            }
        }
        return recovered
    }

    private fun sanitizeOptionalField(raw: String): String {
        val cleaned = raw.trim()
        return when (cleaned.lowercase()) {
            "", "-", "--", "—", "n/a", "na", "nil", "none", "null" -> ""
            else -> cleaned
        }
    }

    private fun parse24hMinutes(raw: String): Int? {
        val trimmed = raw.trim().uppercase()
        if (trimmed.isEmpty()) return null
        val isPm = trimmed.contains("PM")
        val isAm = trimmed.contains("AM")
        val cleaned = trimmed.replace("AM", "").replace("PM", "").trim()
        val parts = cleaned.split(":", ".")
        var h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.trim()?.take(2)?.toIntOrNull() ?: 0
        if (isPm && h in 1..11) h += 12
        if (isAm && h == 12) h = 0
        return (h * 60 + m).coerceIn(0, 1439)
    }

    private fun parseExamIsoDate(iso: String, startMinutes: Int): Calendar? {
        val parts = iso.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, startMinutes / 60)
            set(Calendar.MINUTE, startMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun calendarDayToMondayBased(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    private fun deriveShortCode(title: String): String {
        val words = title.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size == 1) {
            words[0].take(8).uppercase()
        } else {
            words.take(4).map { it.first().uppercaseChar() }.joinToString("")
        }
    }

    private fun isTransientDemandError(error: Throwable): Boolean {
        val msg = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("unavailable") ||
            msg.contains("503") ||
            msg.contains("429") ||
            msg.contains("resource exhausted") ||
            msg.contains("temporarily")
    }

    private fun buildLiveFirestoreContext(
        userPrompt: String,
        scheduleItems: List<ScheduleItem>,
        announcements: List<CampusAnnouncement>,
        directory: List<DirectoryOffice>,
        pendingAssignments: List<ClassroomAssignment>
    ): String {
        val q = userPrompt.trim().lowercase()
        val mentionedDay = knowledgeBase.detectDayName(q)
        val wantsSchedule = mentionedDay != null || listOf(
            "my", "schedule", "class", "timetable", "next", "today", "tomorrow", "period", "lab", "reminder"
        ).any { q.contains(it) }
        val wantsExams = listOf(
            "exam", "midterm", "final", "quiz", "test", "deadline", "upcoming", "schedule", "today", "tomorrow", "next"
        ).any { q.contains(it) }
        val wantsAssignments = listOf(
            "assignment", "homework", "pending", "classroom", "submit", "submission", "due", "overdue", "task", "deadline", "today", "tomorrow"
        ).any { q.contains(it) }
        val wantsAlerts = listOf(
            "alert", "notice", "announcement", "update", "news", "holiday", "urgent", "today", "campus"
        ).any { q.contains(it) }
        val wantsDirectory = listOf(
            "office", "admin", "registrar", "fee", "finance", "admission", "helpdesk", "phone", "hours", "contact", "where"
        ).any { q.contains(it) }

        val classes = scheduleItems.filter { it.parsedType == ScheduleItemType.CLASS }
        val exams = scheduleItems.filter { it.parsedType == ScheduleItemType.EXAM }
            .sortedBy { it.examEpochMillis }

        val selectedClasses = if (wantsSchedule && classes.isNotEmpty()) {
            val dayFiltered = if (mentionedDay != null) {
                classes.filter { ScheduleFormatter.dayFull(it.dayOfWeek).equals(mentionedDay, ignoreCase = true) }
            } else {
                emptyList()
            }
            (if (dayFiltered.isNotEmpty()) dayFiltered else classes)
                .sortedWith(compareBy<ScheduleItem> { it.dayOfWeek }.thenBy { it.startTimeMinutes })
                .take(12)
        } else {
            emptyList()
        }

        val selectedExams = if (wantsExams && exams.isNotEmpty()) exams.take(8) else emptyList()
        val selectedAssignments = if (wantsAssignments && pendingAssignments.isNotEmpty()) {
            pendingAssignments.take(10)
        } else {
            emptyList()
        }

        val selectedAlerts = if (wantsAlerts && announcements.isNotEmpty()) {
            announcements.take(4)
        } else {
            // Surface only high-severity urgent alerts passively (at most 1)
            announcements.filter { it.severity.equals("URGENT", ignoreCase = true) }.take(1)
        }

        val selectedOffices = if (wantsDirectory && directory.isNotEmpty()) {
            val qTokens = q.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            val matched = directory.filter { d ->
                val hay = "${d.name} ${d.building} ${d.room} ${d.description}".lowercase()
                qTokens.any { hay.contains(it) }
            }
            (if (matched.isNotEmpty()) matched else directory).take(6)
        } else {
            emptyList()
        }

        if (selectedClasses.isEmpty() && selectedExams.isEmpty() && selectedAssignments.isEmpty() && selectedAlerts.isEmpty() && selectedOffices.isEmpty()) {
            return ""
        }

        return buildString {
            if (selectedAssignments.isNotEmpty()) {
                appendLine("PendingClassroomAssignments(${pendingAssignments.size}):")
                selectedAssignments.forEach { a ->
                    appendLine("${a.courseName} | ${a.title} | ${a.formattedDueLabel()} (${a.statusLabel})")
                }
            }
            if (selectedClasses.isNotEmpty()) {
                appendLine("SavedClasses:")
                selectedClasses.forEach { c ->
                    appendLine(
                        "${ScheduleFormatter.dayShort(c.dayOfWeek)} ${ScheduleFormatter.formatMinutes(c.startTimeMinutes)}-${ScheduleFormatter.formatMinutes(c.endTimeMinutes)} | ${c.title}" +
                            (if (c.location.isNotBlank()) " @ ${c.location}" else "") +
                            (if (c.instructor.isNotBlank()) " (${c.instructor})" else "")
                    )
                }
            }
            if (selectedExams.isNotEmpty()) {
                appendLine("Exams:")
                selectedExams.forEach { ex ->
                    appendLine(
                        "${ex.courseCode} ${ex.title} | ${ScheduleFormatter.formatExamDate(ex.examEpochMillis)} (${ScheduleFormatter.countdownLabel(ex.examEpochMillis)})" +
                            (if (ex.location.isNotBlank()) " @ ${ex.location}" else "")
                    )
                }
            }
            if (selectedAlerts.isNotEmpty()) {
                appendLine("Alerts:")
                selectedAlerts.forEach { a ->
                    appendLine("[${a.severity}] ${a.title} (${a.department}): ${a.body.take(120)}")
                }
            }
            if (selectedOffices.isNotEmpty()) {
                appendLine("Offices:")
                selectedOffices.forEach { d ->
                    appendLine(
                        "${d.name}: ${d.building} ${d.room} | ${d.hours}" +
                            (if (d.phone.isNotBlank()) " | ${d.phone}" else "")
                    )
                }
            }
        }.trim()
    }
}

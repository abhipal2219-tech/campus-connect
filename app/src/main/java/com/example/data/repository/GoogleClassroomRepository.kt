package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.model.ClassroomAssignment
import com.example.data.model.ClassroomSyncResult
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val CLASSROOM_TAG = "GoogleClassroomRepo"
private const val CLASSROOM_API_BASE = "https://classroom.googleapis.com/v1"

const val SCOPE_CLASSROOM_COURSES_READONLY = "https://www.googleapis.com/auth/classroom.courses.readonly"
const val SCOPE_CLASSROOM_COURSEWORK_ME_READONLY = "https://www.googleapis.com/auth/classroom.coursework.me.readonly"

class ClassroomAuthException(
    message: String,
    val needsReconnect: Boolean = true
) : IllegalStateException(message)

class GoogleClassroomRepository(
    private val appContext: Context
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val requestedScopes = listOf(
        Scope(SCOPE_CLASSROOM_COURSES_READONLY),
        Scope(SCOPE_CLASSROOM_COURSEWORK_ME_READONLY)
    )

    suspend fun authorizeClassroomAccess(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()
        return Identity.getAuthorizationClient(appContext)
            .authorize(request)
            .await()
    }

    fun extractAuthorizationResult(data: Intent?): AuthorizationResult {
        return Identity.getAuthorizationClient(appContext)
            .getAuthorizationResultFromIntent(data)
    }

    suspend fun fetchPendingAssignments(accessToken: String): ClassroomSyncResult =
        withContext(Dispatchers.IO) {
            val cleanToken = accessToken.trim()
            if (cleanToken.isEmpty()) {
                throw ClassroomAuthException("Google Classroom access token is missing. Please connect again.")
            }

            val courses = fetchActiveCourses(cleanToken)
            if (courses.isEmpty()) {
                return@withContext ClassroomSyncResult(
                    assignments = emptyList(),
                    activeCourseCount = 0
                )
            }

            val pending = mutableListOf<ClassroomAssignment>()
            // Fetch coursework + student submissions concurrently in polite batches of 5 courses
            courses.chunked(5).forEach { batch ->
                val batchItems = coroutineScope {
                    batch.map { course ->
                        async { fetchPendingForCourse(cleanToken, course) }
                    }.awaitAll()
                }
                batchItems.forEach { pending.addAll(it) }
            }

            // Sort: overdue & soonest due dates first, then assignments without a due date
            val sorted = pending.sortedWith(
                compareBy<ClassroomAssignment>(
                    { it.dueEpochMillis == null },
                    { it.dueEpochMillis ?: Long.MAX_VALUE },
                    { it.courseName.lowercase() },
                    { it.title.lowercase() }
                )
            )

            ClassroomSyncResult(
                assignments = sorted,
                activeCourseCount = courses.size,
                syncedAtMillis = System.currentTimeMillis()
            )
        }

    private data class ActiveCourse(
        val id: String,
        val name: String,
        val section: String
    )

    private fun fetchActiveCourses(accessToken: String): List<ActiveCourse> {
        val json = getJson(
            url = "$CLASSROOM_API_BASE/courses?courseStates=ACTIVE&studentId=me&pageSize=30",
            accessToken = accessToken
        )
        val arr = json.optJSONArray("courses") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val name = obj.optString("name", "").trim()
                if (id.isNotEmpty() && name.isNotEmpty()) {
                    add(
                        ActiveCourse(
                            id = id,
                            name = name,
                            section = obj.optString("section", "").trim()
                        )
                    )
                }
            }
        }
    }

    private fun fetchPendingForCourse(
        accessToken: String,
        course: ActiveCourse
    ): List<ClassroomAssignment> {
        val courseWorkJson = runCatching {
            getJson(
                url = "$CLASSROOM_API_BASE/courses/${course.id}/courseWork?courseWorkStates=PUBLISHED&pageSize=50",
                accessToken = accessToken
            )
        }.onFailure { err ->
            Log.w(CLASSROOM_TAG, "Skipping coursework for ${course.name}: ${err.message}")
        }.getOrNull() ?: return emptyList()

        val workArray = courseWorkJson.optJSONArray("courseWork") ?: return emptyList()
        if (workArray.length() == 0) return emptyList()

        // Fetch all of the student's submissions for this course in a single wildcard API call
        val submissionMap = HashMap<String, Pair<String, Boolean>>()
        runCatching {
            val subJson = getJson(
                url = "$CLASSROOM_API_BASE/courses/${course.id}/courseWork/-/studentSubmissions?userId=me&pageSize=100",
                accessToken = accessToken
            )
            val subArray = subJson.optJSONArray("studentSubmissions")
            if (subArray != null) {
                for (i in 0 until subArray.length()) {
                    val sub = subArray.optJSONObject(i) ?: continue
                    val cwId = sub.optString("courseWorkId", "").trim()
                    val state = sub.optString("state", "NEW").trim().uppercase()
                    val late = sub.optBoolean("late", false)
                    if (cwId.isNotEmpty()) {
                        submissionMap[cwId] = state to late
                    }
                }
            }
        }.onFailure { err ->
            Log.w(CLASSROOM_TAG, "Failed loading submissions for ${course.name}: ${err.message}")
        }

        val completedStates = setOf("TURNED_IN", "RETURNED")

        return buildList {
            for (i in 0 until workArray.length()) {
                val cw = workArray.optJSONObject(i) ?: continue
                val cwId = cw.optString("id", "").trim()
                val title = cw.optString("title", "").trim()
                if (cwId.isEmpty() || title.isEmpty()) continue

                val submissionInfo = submissionMap[cwId]
                val state = submissionInfo?.first ?: "NEW"
                val isLateFlag = submissionInfo?.second ?: false

                // Exclude already turned-in or graded/returned coursework
                if (state in completedStates) continue

                val dueMillis = parseClassroomDueMillis(
                    dueDate = cw.optJSONObject("dueDate"),
                    dueTime = cw.optJSONObject("dueTime")
                )
                val maxPoints = if (cw.has("maxPoints") && !cw.isNull("maxPoints")) {
                    cw.optDouble("maxPoints", Double.NaN).takeIf { !it.isNaN() }
                } else null

                add(
                    ClassroomAssignment(
                        id = "${course.id}_$cwId",
                        courseId = course.id,
                        courseName = course.name,
                        courseSection = course.section,
                        title = title,
                        description = cw.optString("description", "").trim().take(240),
                        dueEpochMillis = dueMillis,
                        submissionState = state,
                        isLate = isLateFlag,
                        maxPoints = maxPoints,
                        alternateLink = cw.optString("alternateLink", "").trim()
                    )
                )
            }
        }
    }

    private fun parseClassroomDueMillis(
        dueDate: JSONObject?,
        dueTime: JSONObject?
    ): Long? {
        if (dueDate == null) return null
        val year = dueDate.optInt("year", -1)
        val month = dueDate.optInt("month", -1)
        val day = dueDate.optInt("day", -1)
        if (year <= 0 || month !in 1..12 || day !in 1..31) return null

        // Google Classroom stores dueDate + dueTime in UTC; default to 23:59 UTC if dueTime is omitted
        val hours = dueTime?.optInt("hours", 23)?.coerceIn(0, 23) ?: 23
        val minutes = dueTime?.optInt("minutes", 59)?.coerceIn(0, 59) ?: 59

        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return utcCal.timeInMillis
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw ClassroomAuthException(
                    message = "Your Google Classroom session expired. Tap Reconnect to refresh access.",
                    needsReconnect = true
                )
            }
            if (response.code == 403) {
                val detail = extractApiErrorMessage(bodyStr)
                throw ClassroomAuthException(
                    message = detail.ifBlank {
                        "Google Classroom access was denied. Ensure the Google Classroom API is enabled and grant Classroom permissions."
                    },
                    needsReconnect = true
                )
            }
            if (!response.isSuccessful) {
                val detail = extractApiErrorMessage(bodyStr)
                throw IOException(
                    detail.ifBlank { "Google Classroom request failed (HTTP ${response.code})." }
                )
            }
            return if (bodyStr.isBlank()) JSONObject() else JSONObject(bodyStr)
        }
    }

    private fun extractApiErrorMessage(body: String): String {
        return runCatching {
            val errObj = JSONObject(body).optJSONObject("error")
            errObj?.optString("message", "")?.trim().orEmpty()
        }.getOrDefault("")
    }
}

package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import org.json.JSONArray
import org.json.JSONObject

data class FacultyMember(
    val name: String,
    val code: String,
    val dept: String,
    val room: String
)

data class CampusRoom(
    val room: String,
    val use: String,
    val bld: String,
    val floor: String
)

data class CampusSection(
    val section: String,
    val block: String,
    val floor: String,
    val room: String,
    val teacherName: String,
    val teacherPhone: String,
    val teacherEmail: String,
    val mentors: List<String>
)

data class TimetableSlot(
    val className: String,
    val day: String,
    val periodLabel: String,
    val timeRange: String,
    val subject: String,
    val facultyCodes: List<String>,
    val room: String
)

data class CampusKnowledgeData(
    val faculty: List<FacultyMember> = emptyList(),
    val rooms: List<CampusRoom> = emptyList(),
    val sections: List<CampusSection> = emptyList(),
    val iot2BSlots: List<TimetableSlot> = emptyList(),
    val rawFacultyCompact: String = "[]",
    val rawRoomsCompact: String = "[]",
    val rawSectionsCompact: String = "[]",
    val rawIot2BCompact: String = "{}"
)

class CampusKnowledgeBase(private val appContext: Context) {

    @Volatile
    private var cached: CampusKnowledgeData? = null

    fun load(): CampusKnowledgeData {
        cached?.let { return it }
        val loaded = runCatching {
            val facultyRaw = readAsset("campus_data/faculty.json")
            val roomsRaw = readAsset("campus_data/rooms.json")
            val sectionsRaw = readAsset("campus_data/sections.json")
            val iotRaw = readAsset("campus_data/iot_2b_schedule.json")

            val facultyArray = JSONArray(facultyRaw)
            val faculty = buildList {
                for (i in 0 until facultyArray.length()) {
                    val obj = facultyArray.getJSONObject(i)
                    add(
                        FacultyMember(
                            name = obj.optString("name", "").trim(),
                            code = obj.optString("code", "").trim(),
                            dept = obj.optString("dept", "").trim(),
                            room = obj.optString("room", "").trim()
                        )
                    )
                }
            }

            val roomsArray = JSONArray(roomsRaw)
            val rooms = buildList {
                for (i in 0 until roomsArray.length()) {
                    val obj = roomsArray.getJSONObject(i)
                    add(
                        CampusRoom(
                            room = obj.optString("room", "").trim(),
                            use = obj.optString("use", "").trim(),
                            bld = obj.optString("bld", "").trim(),
                            floor = obj.optString("floor", "").trim()
                        )
                    )
                }
            }

            val sectionsArray = JSONArray(sectionsRaw)
            val sections = buildList {
                for (i in 0 until sectionsArray.length()) {
                    val obj = sectionsArray.getJSONObject(i)
                    val teacher = obj.optJSONObject("classTeacher")
                    val mentorsJson = obj.optJSONArray("mentors")
                    val mentorsList = buildList {
                        if (mentorsJson != null) {
                            for (j in 0 until mentorsJson.length()) {
                                val m = mentorsJson.optString(j, "").trim()
                                if (m.isNotEmpty()) add(m)
                            }
                        }
                    }
                    add(
                        CampusSection(
                            section = obj.optString("section", "").trim(),
                            block = obj.optString("block", "").trim(),
                            floor = obj.optString("floor", "").trim(),
                            room = obj.optString("room", "").trim(),
                            teacherName = teacher?.optString("name", "")?.trim().orEmpty(),
                            teacherPhone = teacher?.optString("phone", "")?.trim().orEmpty(),
                            teacherEmail = teacher?.optString("email", "")?.trim().orEmpty(),
                            mentors = mentorsList
                        )
                    )
                }
            }

            val iotObj = JSONObject(iotRaw)
            val defaultClassName = iotObj.optString("class", "IoT 2B")
            val scheduleArr = iotObj.optJSONArray("schedule") ?: JSONArray()
            val iotSlots = buildList {
                for (i in 0 until scheduleArr.length()) {
                    val obj = scheduleArr.getJSONObject(i)
                    val facArr = obj.optJSONArray("faculty")
                    val facList = buildList {
                        if (facArr != null) {
                            for (j in 0 until facArr.length()) {
                                val code = facArr.optString(j, "").trim()
                                if (code.isNotEmpty()) add(code)
                            }
                        }
                    }
                    val roomStr = if (obj.isNull("room")) "" else obj.optString("room", "").trim()
                    add(
                        TimetableSlot(
                            className = obj.optString("class", defaultClassName).trim(),
                            day = obj.optString("day", "").trim(),
                            periodLabel = obj.opt("period")?.toString()?.trim().orEmpty(),
                            timeRange = obj.optString("time", "").trim(),
                            subject = obj.optString("subject", "").trim(),
                            facultyCodes = facList,
                            room = roomStr
                        )
                    )
                }
            }

            CampusKnowledgeData(
                faculty = faculty,
                rooms = rooms,
                sections = sections,
                iot2BSlots = iotSlots,
                rawFacultyCompact = facultyArray.toString(),
                rawRoomsCompact = roomsArray.toString(),
                rawSectionsCompact = sectionsArray.toString(),
                rawIot2BCompact = iotObj.toString()
            )
        }.onFailure { e ->
            Log.e("CampusKnowledgeBase", "Failed loading bundled campus JSON assets", e)
        }.getOrDefault(CampusKnowledgeData())

        cached = loaded
        return loaded
    }

    fun buildRelevantContextForQuery(query: String): String {
        val data = load()
        val q = query.trim().lowercase()
        val rawTokens = q.split(Regex("[^a-z0-9.]+")).filter { it.isNotBlank() }
        val stopWords = setOf(
            "what", "is", "the", "in", "at", "of", "and", "for", "where", "who", "to", "on",
            "my", "me", "tell", "can", "you", "a", "an", "do", "does", "are", "be", "please",
            "show", "give", "find", "about", "with", "from", "or", "their", "his", "her",
            "this", "that", "how", "which", "when", "dr", "prof", "sir", "maam", "mam", "mr",
            "mrs", "ms", "room", "rooms", "class", "classes"
        )
        val tokens = rawTokens.filter { it !in stopWords && it.length >= 2 }
        val singleLetterSectionTokens = Regex("\\b(?:sec|section)\\s*[-:]?\\s*([a-z0-9]+)\\b")
            .findAll(q)
            .map { it.groupValues[1].lowercase() }
            .toSet()

        val mentionedDay = detectDayName(q)
        val asksSchedule = listOf("iot", "2b", "schedule", "timetable", "routine", "period", "lecture", "lab", "tomorrow", "today")
            .any { q.contains(it) } || mentionedDay != null
        val asksSections = listOf("section", "sec ", "mentor", "class teacher", "coordinator")
            .any { q.contains(it) } || singleLetterSectionTokens.isNotEmpty()
        val asksAllFaculty = q.contains("all faculty") || q.contains("list faculty") || q.contains("professors")
        val asksAllRooms = q.contains("all room") || q.contains("list room")

        // 1. Match IoT 2B Timetable slots
        val matchedSlots = if (asksSchedule) {
            val nonBreak = data.iot2BSlots.filter {
                !it.subject.equals("BREAK", ignoreCase = true) && !it.periodLabel.equals("BREAK", ignoreCase = true)
            }
            val dayFiltered = if (mentionedDay != null) {
                nonBreak.filter { it.day.equals(mentionedDay, ignoreCase = true) }
            } else {
                emptyList()
            }
            when {
                dayFiltered.isNotEmpty() -> dayFiltered
                q.contains("iot") || q.contains("2b") || q.contains("timetable") || q.contains("schedule") -> nonBreak
                else -> nonBreak.filter { slot ->
                    val hay = "${slot.day} ${slot.subject} ${slot.room} ${slot.facultyCodes.joinToString(" ")}".lowercase()
                    tokens.any { hay.contains(it) }
                }.take(25)
            }
        } else {
            data.iot2BSlots.filter { slot ->
                !slot.subject.equals("BREAK", ignoreCase = true) &&
                    tokens.any { tok ->
                        slot.subject.lowercase().contains(tok) ||
                            slot.room.lowercase().contains(tok) ||
                            slot.facultyCodes.any { it.equals(tok, ignoreCase = true) }
                    }
            }.take(16)
        }

        // 2. Match Sections
        val matchedSections = when {
            singleLetterSectionTokens.isNotEmpty() -> {
                data.sections.filter { it.section.lowercase() in singleLetterSectionTokens }
                    .ifEmpty { data.sections }
            }
            asksSections -> {
                val specific = data.sections.filter { s ->
                    val hay = "${s.section} ${s.block} ${s.room} ${s.teacherName} ${s.mentors.joinToString(" ")}".lowercase()
                    tokens.any { hay.contains(it) }
                }
                specific.ifEmpty { data.sections }
            }
            else -> {
                data.sections.filter { s ->
                    val hay = "${s.room} ${s.teacherName} ${s.mentors.joinToString(" ")}".lowercase()
                    tokens.any { tok -> tok.length >= 3 && hay.contains(tok) }
                }.take(8)
            }
        }

        // Cross-referenced faculty codes/names & rooms from matched slots/sections
        val refFacultyCodes = matchedSlots.flatMap { it.facultyCodes }.map { it.lowercase() }.toSet()
        val refNames = matchedSections.flatMap { listOf(it.teacherName) + it.mentors }
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
        val refRooms = (matchedSlots.map { it.room } + matchedSections.map { it.room })
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        // 3. Score & match Faculty
        val scoredFaculty = data.faculty.mapNotNull { f ->
            var score = 0
            val nameLow = f.name.lowercase()
            val codeLow = f.code.lowercase()
            val deptLow = f.dept.lowercase()
            val roomLow = f.room.lowercase()

            if (codeLow.isNotEmpty() && (codeLow in rawTokens || codeLow in refFacultyCodes)) score += 10
            if (refNames.any { ref -> nameLow.contains(ref) || ref.contains(nameLow) }) score += 8
            tokens.forEach { tok ->
                if (nameLow.contains(tok)) score += 6
                if (codeLow == tok) score += 8
                if (deptLow.contains(tok)) score += 3
                if (roomLow.isNotEmpty() && roomLow.contains(tok)) score += 5
            }
            if (score > 0) f to score else null
        }.sortedByDescending { it.second }.map { it.first }
            .take(if (asksAllFaculty) 40 else 18)

        val finalFaculty = if (asksAllFaculty && scoredFaculty.size < 10) {
            data.faculty.take(40)
        } else {
            scoredFaculty
        }

        val facultyRooms = finalFaculty.map { it.room.lowercase() }.filter { it.isNotBlank() }.toSet()
        val allRefRooms = refRooms + facultyRooms

        // 4. Score & match Rooms
        val blockTokens = setOf("b1", "b2", "b3").filter { it in rawTokens }.toSet()
        val scoredRooms = data.rooms.mapNotNull { r ->
            var score = 0
            val roomLow = r.room.lowercase()
            val useLow = r.use.lowercase()
            val bldLow = r.bld.lowercase()
            val floorLow = r.floor.lowercase()

            if (roomLow.isNotEmpty() && (roomLow in rawTokens || roomLow in allRefRooms)) score += 10
            if (bldLow in blockTokens) score += 3
            tokens.forEach { tok ->
                if (roomLow.contains(tok)) score += 7
                if (useLow.contains(tok)) score += 6
                if (floorLow.contains(tok)) score += 2
            }
            if (score > 0) r to score else null
        }.sortedByDescending { it.second }.map { it.first }
            .take(if (asksAllRooms) 40 else 22)

        val totalMatched = matchedSlots.size + matchedSections.size + finalFaculty.size + scoredRooms.size

        return buildString {
            appendLine("=== CAMPUS KB (MATCHED FROM 4 JSON FILES: ${data.faculty.size} faculty, ${data.rooms.size} rooms, ${data.sections.size} sections, ${data.iot2BSlots.size} IoT2B slots) ===")

            if (matchedSlots.isNotEmpty()) {
                appendLine("IoT 2B TIMETABLE (Day|Period|Time|Subject|Faculty|Room):")
                matchedSlots.forEach { t ->
                    appendLine("${t.day}|${t.periodLabel}|${t.timeRange}|${t.subject}|${t.facultyCodes.joinToString(",")}|${t.room}")
                }
            }
            if (matchedSections.isNotEmpty()) {
                appendLine("SECTIONS (Sec|Bld|Floor|Room|Teacher|Phone|Email|Mentors):")
                matchedSections.forEach { s ->
                    appendLine("${s.section}|${s.block}|${s.floor}|${s.room}|${s.teacherName}|${s.teacherPhone}|${s.teacherEmail}|${s.mentors.joinToString(",")}")
                }
            }
            if (finalFaculty.isNotEmpty()) {
                appendLine("FACULTY (Name|Code|Dept|Room):")
                finalFaculty.forEach { f ->
                    appendLine("${f.name}|${f.code}|${f.dept}|${f.room}")
                }
            }
            if (scoredRooms.isNotEmpty()) {
                appendLine("ROOMS (Room|Use|Bld|Floor):")
                scoredRooms.forEach { r ->
                    appendLine("${r.room}|${r.use}|${r.bld}|${r.floor}")
                }
            }

            // General overview fallback when user asks a broad/open question with few direct keyword hits
            if (totalMatched < 3) {
                val blocks = data.rooms.map { it.bld }.filter { it.isNotBlank() }.distinct().sorted()
                val depts = data.faculty.map { it.dept }.filter { it.isNotBlank() }.distinct().sorted()
                appendLine("CAMPUS OVERVIEW:")
                appendLine("Blocks: ${blocks.joinToString(", ")}")
                appendLine("Departments: ${depts.joinToString(", ")}")
                appendLine("Sections: ${data.sections.joinToString(", ") { "${it.section}(${it.room})" }}")
                val keyFacilities = data.rooms.filter { r ->
                    val u = r.use.lowercase()
                    u.contains("auditorium") || u.contains("medical") || u.contains("library") ||
                        u.contains("canteen") || u.contains("office") || u.contains("admission") ||
                        u.contains("exam") || u.contains("principal") || u.contains("hod")
                }.take(16)
                if (keyFacilities.isNotEmpty()) {
                    appendLine("KEY FACILITIES (Room|Use|Bld|Floor):")
                    keyFacilities.forEach { r ->
                        appendLine("${r.room}|${r.use}|${r.bld}|${r.floor}")
                    }
                }
            }
        }
    }

    private fun detectDayName(q: String): String? {
        return when {
            q.contains("monday") || Regex("\\bmon\\b").containsMatchIn(q) -> "Monday"
            q.contains("tuesday") || Regex("\\btue\\b").containsMatchIn(q) -> "Tuesday"
            q.contains("wednesday") || Regex("\\bwed\\b").containsMatchIn(q) -> "Wednesday"
            q.contains("thursday") || Regex("\\bthu\\b").containsMatchIn(q) -> "Thursday"
            q.contains("friday") || Regex("\\bfri\\b").containsMatchIn(q) -> "Friday"
            q.contains("saturday") || Regex("\\bsat\\b").containsMatchIn(q) -> "Saturday"
            q.contains("sunday") || Regex("\\bsun\\b").containsMatchIn(q) -> "Sunday"
            q.contains("today") -> dayNameForOffset(0)
            q.contains("tomorrow") -> dayNameForOffset(1)
            else -> null
        }
    }

    private fun dayNameForOffset(daysOffset: Int): String {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, daysOffset) }
        return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "Monday"
            java.util.Calendar.TUESDAY -> "Tuesday"
            java.util.Calendar.WEDNESDAY -> "Wednesday"
            java.util.Calendar.THURSDAY -> "Thursday"
            java.util.Calendar.FRIDAY -> "Friday"
            java.util.Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
        }
    }

    fun buildFullJsonContextForAi(): String {
        val data = load()
        return buildString {
            appendLine("=== OFFICIAL CAMPUS KNOWLEDGE BASE (4 JSON DATASETS) ===")
            appendLine("1. FACULTY (faculty.json — ${data.faculty.size} entries | Name|Code|Dept|Room):")
            data.faculty.forEach { f ->
                appendLine("${f.name}|${f.code}|${f.dept}|${f.room}")
            }
            appendLine()
            appendLine("2. CAMPUS ROOMS (rooms.json — ${data.rooms.size} entries | Room|Use|Bld|Floor):")
            data.rooms.forEach { r ->
                appendLine("${r.room}|${r.use}|${r.bld}|${r.floor}")
            }
            appendLine()
            appendLine("3. SECTIONS (sections.json — ${data.sections.size} entries | Sec|Bld|Floor|Room|Teacher|Phone|Email|Mentors):")
            data.sections.forEach { s ->
                appendLine(
                    "${s.section}|${s.block}|${s.floor}|${s.room}|${s.teacherName}|${s.teacherPhone}|${s.teacherEmail}|${s.mentors.joinToString(",")}"
                )
            }
            appendLine()
            appendLine("4. IoT 2B TIMETABLE (iot_2b_schedule.json — ${data.iot2BSlots.size} slots | Day|Period|Time|Subject|Faculty|Room):")
            data.iot2BSlots.forEach { t ->
                appendLine(
                    "${t.day}|${t.periodLabel}|${t.timeRange}|${t.subject}|${t.facultyCodes.joinToString(",")}|${t.room}"
                )
            }
        }
    }

    fun buildIot2BScheduleItems(defaultLeadMinutes: Int): List<ScheduleItem> {
        val data = load()
        val nonBreak = data.iot2BSlots.filter {
            !it.subject.equals("BREAK", ignoreCase = true) &&
                !it.periodLabel.equals("BREAK", ignoreCase = true) &&
                it.subject.isNotBlank()
        }
        return nonBreak.mapNotNull { slot ->
            val dayNum = parseDayOfWeek(slot.day) ?: return@mapNotNull null
            val (startMin, endMin) = parseTimeRange(slot.timeRange)
            val code = buildShortCourseCode(slot.subject, slot.periodLabel)
            ScheduleItem(
                itemType = ScheduleItemType.CLASS.name,
                courseCode = code,
                title = slot.subject.take(100),
                dayOfWeek = dayNum,
                startTimeMinutes = startMin,
                endTimeMinutes = endMin,
                examEpochMillis = 0L,
                location = slot.room.take(100),
                instructor = slot.facultyCodes.joinToString(", ").take(80),
                reminderEnabled = true,
                reminderLeadMinutes = defaultLeadMinutes.coerceIn(5, 1440)
            )
        }
    }

    private fun readAsset(path: String): String =
        appContext.assets.open(path).bufferedReader().use { it.readText() }

    private fun parseDayOfWeek(day: String): Int? = when (day.trim().lowercase()) {
        "monday", "mon" -> 1
        "tuesday", "tue" -> 2
        "wednesday", "wed" -> 3
        "thursday", "thu" -> 4
        "friday", "fri" -> 5
        "saturday", "sat" -> 6
        "sunday", "sun" -> 7
        else -> null
    }

    private fun parseTimeRange(range: String): Pair<Int, Int> {
        val parts = range.split("-")
        val start = parseClockMinutes(parts.getOrNull(0).orEmpty()) ?: 570
        val end = parseClockMinutes(parts.getOrNull(1).orEmpty()) ?: (start + 45).coerceAtMost(1439)
        return start to end
    }

    private fun parseClockMinutes(clock: String): Int? {
        val clean = clock.trim().split(":")
        val h = clean.getOrNull(0)?.toIntOrNull() ?: return null
        val m = clean.getOrNull(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).coerceIn(0, 1439)
    }

    private fun buildShortCourseCode(subject: String, periodLabel: String): String {
        val words = subject.split(Regex("\\s+")).filter { it.isNotBlank() }
        val acronym = if (words.size == 1) {
            words[0].take(8).uppercase()
        } else {
            words.take(4).map { it.first().uppercaseChar() }.joinToString("")
        }
        return if (periodLabel.isNotBlank()) "IoT2B-$acronym" else acronym
    }
}

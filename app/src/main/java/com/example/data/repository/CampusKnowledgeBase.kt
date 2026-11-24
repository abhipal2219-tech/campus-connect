package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

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
    val iot2BSlots: List<TimetableSlot> = emptyList()
)

private const val MAX_RAG_CONTEXT_CHARS = 3000

private val StopWords = setOf(
    "what", "is", "the", "in", "at", "of", "and", "for", "where", "who", "to", "on",
    "my", "me", "tell", "can", "you", "a", "an", "do", "does", "are", "be", "please",
    "show", "give", "find", "about", "with", "from", "or", "their", "his", "her",
    "this", "that", "how", "which", "when", "dr", "prof", "sir", "maam", "mam", "mr",
    "mrs", "ms", "room", "rooms", "class", "classes", "have", "has", "any", "get", "me"
)

private val FollowUpHints = setOf(
    "she", "he", "her", "his", "they", "their", "them", "that", "it", "same", "also",
    "what about", "and", "email", "phone", "number", "cabin", "office", "contact"
)

private val CampusSynonyms: Map<String, List<String>> = mapOf(
    "med" to listOf("medical"),
    "doctor" to listOf("medical"),
    "infirmary" to listOf("medical"),
    "sick" to listOf("medical"),
    "lib" to listOf("library"),
    "aud" to listOf("auditorium"),
    "audi" to listOf("auditorium"),
    "math" to listOf("mathematics"),
    "maths" to listOf("mathematics"),
    "ds" to listOf("data", "structures"),
    "dsa" to listOf("data", "structures", "algorithms"),
    "dec" to listOf("digital", "electronic", "circuits"),
    "aec" to listOf("analog", "electronic", "circuits"),
    "ppt" to listOf("pre", "placement", "training"),
    "pom" to listOf("principles", "management"),
    "tsd" to listOf("technical", "skill", "development"),
    "hod" to listOf("hod", "head", "department"),
    "cafeteria" to listOf("canteen"),
    "food" to listOf("canteen"),
    "wifi" to listOf("server", "network", "it")
)

private data class IndexedDoc<T>(
    val item: T,
    val tokens: Set<String>,
    val compactKey: String,
    val searchableText: String
)

private class RagSearchIndex(data: CampusKnowledgeData) {
    val facultyDocs: List<IndexedDoc<FacultyMember>> = data.faculty.map { f ->
        val raw = "${f.name} ${f.code} ${f.dept} ${f.room}"
        IndexedDoc(
            item = f,
            tokens = tokenizeWithCompactForms(raw),
            compactKey = compactAlphaNum(f.code.ifBlank { f.name }),
            searchableText = raw.lowercase()
        )
    }

    val roomDocs: List<IndexedDoc<CampusRoom>> = data.rooms.map { r ->
        val raw = "${r.room} ${r.use} ${r.bld} ${r.floor}"
        IndexedDoc(
            item = r,
            tokens = tokenizeWithCompactForms(raw),
            compactKey = compactAlphaNum(r.room),
            searchableText = raw.lowercase()
        )
    }

    val sectionDocs: List<IndexedDoc<CampusSection>> = data.sections.map { s ->
        val raw = "${s.section} ${s.block} ${s.floor} ${s.room} ${s.teacherName} ${s.mentors.joinToString(" ")}"
        IndexedDoc(
            item = s,
            tokens = tokenizeWithCompactForms(raw),
            compactKey = compactAlphaNum(s.section),
            searchableText = raw.lowercase()
        )
    }

    val slotDocs: List<IndexedDoc<TimetableSlot>> = data.iot2BSlots
        .filter { !it.subject.equals("BREAK", ignoreCase = true) && !it.periodLabel.equals("BREAK", ignoreCase = true) }
        .map { slot ->
            val raw = "${slot.day} ${slot.subject} ${slot.room} ${slot.facultyCodes.joinToString(" ")} ${slot.timeRange}"
            IndexedDoc(
                item = slot,
                tokens = tokenizeWithCompactForms(raw),
                compactKey = compactAlphaNum(slot.room),
                searchableText = raw.lowercase()
            )
        }

    // Precompute BM25-style IDF weights across the full campus corpus once at load time
    private val idfTable: Map<String, Double>
    private val defaultIdf: Double

    init {
        val allDocs = facultyDocs.map { it.tokens } +
            roomDocs.map { it.tokens } +
            sectionDocs.map { it.tokens } +
            slotDocs.map { it.tokens }
        val totalDocs = allDocs.size.coerceAtLeast(1).toDouble()
        val df = HashMap<String, Int>(1024)
        for (docTokens in allDocs) {
            for (tok in docTokens) {
                df[tok] = (df[tok] ?: 0) + 1
            }
        }
        val map = HashMap<String, Double>(df.size)
        for ((tok, count) in df) {
            map[tok] = ln(1.0 + (totalDocs - count + 0.5) / (count + 0.5))
        }
        idfTable = map
        defaultIdf = ln(1.0 + totalDocs)
    }

    fun idf(token: String): Double = idfTable[token] ?: defaultIdf

    companion object {
        fun compactAlphaNum(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]"), "")

        fun tokenizeWithCompactForms(text: String): Set<String> {
            val base = text.lowercase()
                .split(Regex("[^a-z0-9.]+"))
                .filter { it.isNotBlank() }
            val result = LinkedHashSet<String>(base.size + 4)
            for (t in base) {
                result.add(t)
                if (t.contains('.')) {
                    t.split('.').filter { it.isNotBlank() }.forEach { result.add(it) }
                }
            }
            // Add compact room codes like "b3lg4.6" or "lg4.6"
            Regex("\\b(b[1-3])\\s*(lg|ug|g|f)?\\s*([0-9]+(?:\\.[0-9]+)?)\\b")
                .findAll(text.lowercase())
                .forEach { m ->
                    val b = m.groupValues[1]
                    val f = m.groupValues[2]
                    val n = m.groupValues[3]
                    result.add("$b$f$n")
                    if (f.isNotEmpty()) result.add("$f$n")
                    result.add(n)
                }
            return result
        }
    }
}

class CampusKnowledgeBase(private val appContext: Context) {

    @Volatile
    private var cached: CampusKnowledgeData? = null

    @Volatile
    private var cachedIndex: RagSearchIndex? = null

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
                iot2BSlots = iotSlots
            )
        }.onFailure { e ->
            Log.e("CampusKnowledgeBase", "Failed loading bundled campus JSON assets", e)
        }.getOrDefault(CampusKnowledgeData())

        cached = loaded
        cachedIndex = RagSearchIndex(loaded)
        return loaded
    }

    private fun getIndex(): RagSearchIndex {
        cachedIndex?.let { return it }
        load()
        return cachedIndex ?: RagSearchIndex(CampusKnowledgeData())
    }

    fun buildRelevantContextForQuery(
        query: String,
        recentUserTurns: List<String> = emptyList()
    ): String {
        val data = load()
        val index = getIndex()
        val q = query.trim().lowercase()

        // Expand follow-up pronouns ("what is her room?") with the last user turn at lower weight
        val isLikelyFollowUp = recentUserTurns.isNotEmpty() && (
            q.length < 36 || FollowUpHints.any { hint -> Regex("\\b${Regex.escape(hint)}\\b").containsMatchIn(q) }
        )
        val primaryTokens = extractQueryTokens(q)
        val secondaryTokens = if (isLikelyFollowUp) {
            extractQueryTokens(recentUserTurns.last().lowercase()) - primaryTokens
        } else {
            emptySet()
        }
        val combinedText = if (isLikelyFollowUp) "$q ${recentUserTurns.last().lowercase()}" else q

        val singleLetterSections = Regex("\\b(?:sec|section)\\s*[-:]?\\s*([a-z0-9]+)\\b")
            .findAll(combinedText)
            .map { it.groupValues[1].lowercase() }
            .toSet()

        val mentionedDay = detectDayName(q) ?: if (isLikelyFollowUp) detectDayName(recentUserTurns.last().lowercase()) else null

        // Lightweight intent flags keep irrelevant domains out of the prompt to save tokens
        val asksSchedule = mentionedDay != null || listOf(
            "iot", "2b", "schedule", "timetable", "routine", "period", "lecture", "lab", "next class", "eaa"
        ).any { q.contains(it) }
        val asksSections = singleLetterSections.isNotEmpty() || listOf(
            "section", "sec ", "mentor", "class teacher", "coordinator"
        ).any { q.contains(it) }
        val asksFaculty = listOf(
            "faculty", "professor", "prof", "teacher", "dr.", "dr ", "sir", "ma'am", "cabin", "hod", "who teaches"
        ).any { q.contains(it) }
        val asksRoom = listOf(
            "where", "room", "block", "floor", "hall", "auditorium", "medical", "library", "canteen", "lab", "b1", "b2", "b3"
        ).any { q.contains(it) }
        val asksAllFaculty = q.contains("all faculty") || q.contains("list faculty")
        val asksAllRooms = q.contains("all room") || q.contains("list room")

        // 1. Retrieve IoT 2B Timetable Slots (focused by day or high-signal BM25 match)
        val matchedSlots: List<TimetableSlot> = when {
            asksSchedule && mentionedDay != null -> {
                index.slotDocs.map { it.item }.filter { it.day.equals(mentionedDay, ignoreCase = true) }
            }
            asksSchedule && (q.contains("iot") || q.contains("2b") || q.contains("timetable") || q.contains("full schedule")) -> {
                index.slotDocs.map { it.item }
            }
            else -> {
                scoreDocs(
                    docs = index.slotDocs,
                    index = index,
                    primaryTokens = primaryTokens,
                    secondaryTokens = secondaryTokens,
                    minScore = if (asksSchedule) 1.5 else 4.2
                ).take(if (asksSchedule) 15 else 6)
            }
        }

        // 2. Retrieve Sections
        val matchedSections: List<CampusSection> = when {
            singleLetterSections.isNotEmpty() -> {
                data.sections.filter { it.section.lowercase() in singleLetterSections }
                    .ifEmpty { data.sections.take(6) }
            }
            asksSections -> {
                scoreDocs(index.sectionDocs, index, primaryTokens, secondaryTokens, minScore = 1.2)
                    .take(6)
                    .ifEmpty { data.sections }
            }
            else -> {
                scoreDocs(index.sectionDocs, index, primaryTokens, secondaryTokens, minScore = 4.5)
                    .take(3)
            }
        }

        // Entity-linking sets from matched slots & sections (resolves codes like SGH/MNC -> full faculty + room floor)
        val linkedFacultyCodes = matchedSlots.flatMap { it.facultyCodes }.map { it.lowercase() }.toSet()
        val linkedTeacherNames = matchedSections
            .flatMap { listOf(it.teacherName) + it.mentors }
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
        val linkedRoomKeys = (matchedSlots.map { it.room } + matchedSections.map { it.room })
            .map { RagSearchIndex.compactAlphaNum(it) }
            .filter { it.isNotBlank() }
            .toSet()

        // 3. Retrieve Faculty (BM25 + fuzzy name match + entity links from schedule/sections)
        val rawTokensSet = q.split(Regex("[^a-z0-9.]+")).filter { it.isNotBlank() }.toSet()
        val facultyLimit = when {
            asksAllFaculty -> 25
            asksFaculty || linkedFacultyCodes.isNotEmpty() -> 10
            else -> 5
        }
        val scoredFaculty = index.facultyDocs.mapNotNull { doc ->
            var score = bm25FuzzyScore(doc, index, primaryTokens, secondaryTokens)
            val codeLow = doc.item.code.lowercase()
            val nameLow = doc.item.name.lowercase()
            if (codeLow.isNotEmpty() && codeLow in rawTokensSet) score += 14.0
            if (codeLow.isNotEmpty() && codeLow in linkedFacultyCodes) score += 9.0
            if (linkedTeacherNames.any { ref -> nameLow.contains(ref) || ref.contains(nameLow) }) score += 10.0
            val threshold = if (asksFaculty || linkedFacultyCodes.isNotEmpty()) 1.8 else 4.2
            if (score >= threshold) doc.item to score else null
        }.sortedByDescending { it.second }
            .map { it.first }
            .take(facultyLimit)

        val finalFaculty = if (asksAllFaculty && scoredFaculty.size < 8) {
            data.faculty.take(25)
        } else {
            scoredFaculty
        }

        // Link cabins of matched faculty to rooms.json so the model knows exact block/floor
        val facultyRoomKeys = finalFaculty
            .map { RagSearchIndex.compactAlphaNum(it.room) }
            .filter { it.isNotBlank() }
            .toSet()
        val allLinkedRooms = linkedRoomKeys + facultyRoomKeys

        // 4. Retrieve Rooms (BM25 + compact room-code match + fuzzy typo tolerance + linked rooms)
        val roomLimit = when {
            asksAllRooms -> 24
            asksRoom -> 10
            else -> 6
        }
        val compactQuery = RagSearchIndex.compactAlphaNum(q)
        val scoredRooms = index.roomDocs.mapNotNull { doc ->
            var score = bm25FuzzyScore(doc, index, primaryTokens, secondaryTokens)
            if (doc.compactKey.length >= 3 && (doc.compactKey in allLinkedRooms || compactQuery.contains(doc.compactKey))) {
                score += 12.0
            }
            val threshold = if (asksRoom || allLinkedRooms.isNotEmpty()) 1.8 else 4.2
            if (score >= threshold) doc.item to score else null
        }.sortedByDescending { it.second }
            .map { it.first }
            .take(roomLimit)

        val totalMatched = matchedSlots.size + matchedSections.size + finalFaculty.size + scoredRooms.size

        // 5. Assemble ultra-compact, token-budgeted context string
        val built = buildString {
            appendLine("CAMPUS KB (${data.faculty.size} faculty, ${data.rooms.size} rooms, ${data.sections.size} sections):")

            if (matchedSlots.isNotEmpty()) {
                appendLine("IoT2B(Day|Per|Time|Subj|Fac|Room):")
                matchedSlots.forEach { t ->
                    appendLine("${t.day.take(3)}|${t.periodLabel}|${t.timeRange}|${t.subject}|${t.facultyCodes.joinToString(",")}|${t.room}")
                }
            }
            if (matchedSections.isNotEmpty()) {
                appendLine("SEC(Sec|Bld|Flr|Room|Teacher|Ph|Email|Mentors):")
                matchedSections.forEach { s ->
                    appendLine("${s.section}|${s.block}|${s.floor}|${s.room}|${s.teacherName}|${s.teacherPhone}|${s.teacherEmail}|${s.mentors.joinToString(",")}")
                }
            }
            if (finalFaculty.isNotEmpty()) {
                appendLine("FAC(Name|Code|Dept|Room):")
                finalFaculty.forEach { f ->
                    appendLine("${f.name}|${f.code}|${f.dept}|${f.room}")
                }
            }
            if (scoredRooms.isNotEmpty()) {
                appendLine("ROOMS(Room|Use|Bld|Flr):")
                scoredRooms.forEach { r ->
                    appendLine("${r.room}|${r.use}|${r.bld}|${r.floor}")
                }
            }

            if (totalMatched < 2) {
                val blocks = data.rooms.map { it.bld }.filter { it.isNotBlank() }.distinct().sorted()
                val depts = data.faculty.map { it.dept }.filter { it.isNotBlank() }.distinct().sorted()
                appendLine("OVERVIEW: Blocks=${blocks.joinToString(",")}; Depts=${depts.joinToString(",")}; Sections=${data.sections.joinToString(",") { "${it.section}(${it.room})" }}")
                val keyFacilities = data.rooms.filter { r ->
                    val u = r.use.lowercase()
                    u.contains("auditorium") || u.contains("medical") || u.contains("library") ||
                        u.contains("canteen") || u.contains("office") || u.contains("admission") ||
                        u.contains("principal")
                }.take(10)
                if (keyFacilities.isNotEmpty()) {
                    appendLine("FACILITIES(Room|Use|Bld|Flr):")
                    keyFacilities.forEach { r ->
                        appendLine("${r.room}|${r.use}|${r.bld}|${r.floor}")
                    }
                }
            }
        }.trim()

        return if (built.length > MAX_RAG_CONTEXT_CHARS) {
            built.take(MAX_RAG_CONTEXT_CHARS)
        } else {
            built
        }
    }

    private fun extractQueryTokens(normalizedQuery: String): Set<String> {
        val raw = RagSearchIndex.tokenizeWithCompactForms(normalizedQuery)
            .filter { it !in StopWords && it.length >= 2 }
        val expanded = LinkedHashSet<String>(raw.size + 4)
        for (tok in raw) {
            expanded.add(tok)
            CampusSynonyms[tok]?.forEach { expanded.add(it) }
        }
        return expanded
    }

    private fun <T> scoreDocs(
        docs: List<IndexedDoc<T>>,
        index: RagSearchIndex,
        primaryTokens: Set<String>,
        secondaryTokens: Set<String>,
        minScore: Double
    ): List<T> {
        if (primaryTokens.isEmpty() && secondaryTokens.isEmpty()) return emptyList()
        return docs.mapNotNull { doc ->
            val score = bm25FuzzyScore(doc, index, primaryTokens, secondaryTokens)
            if (score >= minScore) doc.item to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun <T> bm25FuzzyScore(
        doc: IndexedDoc<T>,
        index: RagSearchIndex,
        primaryTokens: Set<String>,
        secondaryTokens: Set<String>
    ): Double {
        var score = 0.0
        var primaryHits = 0
        for (tok in primaryTokens) {
            val hit = tokenMatchWeight(tok, doc)
            if (hit > 0.0) {
                score += hit * index.idf(tok)
                primaryHits++
            }
        }
        // Multi-token coverage boost when a single record satisfies multiple query keywords
        if (primaryHits >= 2) {
            score *= 1.0 + (0.18 * (primaryHits - 1).coerceAtMost(4))
        }
        for (tok in secondaryTokens) {
            val hit = tokenMatchWeight(tok, doc)
            if (hit > 0.0) {
                score += 0.35 * hit * index.idf(tok)
            }
        }
        return score
    }

    private fun <T> tokenMatchWeight(queryToken: String, doc: IndexedDoc<T>): Double {
        if (queryToken in doc.tokens) return 2.2
        if (queryToken.length >= 3 && doc.searchableText.contains(queryToken)) return 1.45
        // Bounded typo tolerance (edit distance 1 for len 4-6, edit distance 2 for len >= 7)
        if (queryToken.length >= 4) {
            val maxDist = if (queryToken.length >= 7) 2 else 1
            for (dt in doc.tokens) {
                if (dt.length >= 4 && abs(dt.length - queryToken.length) <= maxDist && dt[0] == queryToken[0]) {
                    val d = boundedLevenshtein(queryToken, dt, maxDist)
                    if (d <= maxDist) return if (d == 1) 1.35 else 0.95
                }
            }
        }
        return 0.0
    }

    private fun boundedLevenshtein(a: String, b: String, limit: Int): Int {
        val m = a.length
        val n = b.length
        if (abs(m - n) > limit) return limit + 1
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            var rowMin = curr[0]
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                val v = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }

    fun detectDayName(q: String): String? {
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
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysOffset) }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
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

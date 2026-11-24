package com.example.data.model

data class AcademicProgram(
    val name: String,
    val maxSemesters: Int,
    val departments: List<String>
)

data class StudentAcademicScope(
    val program: String,
    val department: String,
    val semester: Int,
    val section: String,
    val rollNumber: String = "",
    val enrollmentNumber: String = ""
) {
    val academicYear: Int
        get() = AcademicCatalog.yearFromSemester(semester)

    val cacheKey: String
        get() = "$program|$department|$semester|$section".lowercase()

    val badgeLabel: String
        get() {
            val parts = buildList {
                if (department.isNotBlank()) add(department)
                if (semester > 0) add("SEM $semester (YR $academicYear)")
                val cleanSec = AcademicCatalog.normalizeSection(section)
                if (cleanSec.isNotBlank()) add("SEC $cleanSec")
            }
            return parts.joinToString(" · ")
        }

    val isConfigured: Boolean
        get() = department.isNotBlank() && semester > 0 && section.isNotBlank()
}

object AcademicCatalog {
    val programs: List<AcademicProgram> = listOf(
        AcademicProgram(
            name = "B.Tech.",
            maxSemesters = 8,
            departments = listOf(
                "BioTech",
                "C.S.E (A.I.)",
                "C.S.E (A.I.D.S.)",
                "C.S.E (A.I.M.L.)",
                "C.S.E (CyberSecurity)",
                "C.S.E (DataScience)",
                "C.S.E (I.O.T.)",
                "C.S.E (I.O.T.C.S.B.T)",
                "C.S.E (Networks)",
                "CSBS",
                "CSE",
                "CSIT",
                "CST",
                "ECE",
                "EE",
                "EEE",
                "IT",
                "ME",
                "RoboticsAndAI"
            )
        ),
        AcademicProgram(
            name = "M.Tech.",
            maxSemesters = 4,
            departments = listOf(
                "CSE",
                "C.S.E (A.I.M.L.)",
                "ECE"
            )
        ),
        AcademicProgram(
            name = "B.B.A.",
            maxSemesters = 6,
            departments = listOf(
                "B.Analytics",
                "B.B.A"
            )
        ),
        AcademicProgram(
            name = "M.B.A.",
            maxSemesters = 4,
            departments = listOf(
                "MBA (FT)",
                "MBA (GM)"
            )
        ),
        AcademicProgram(
            name = "B.C.A.",
            maxSemesters = 6,
            departments = listOf(
                "B.C.A"
            )
        ),
        AcademicProgram(
            name = "M.C.A.",
            maxSemesters = 4,
            departments = listOf(
                "M.C.A"
            )
        ),
        AcademicProgram(
            name = "B.Sc.",
            maxSemesters = 6,
            departments = listOf(
                "BHTA",
                "HHA"
            )
        ),
        AcademicProgram(
            name = "B.B.A-L.L.B",
            maxSemesters = 10,
            departments = listOf(
                "LLB"
            )
        )
    )

    val quickSections: List<String> = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H",
        "I", "J", "K", "L", "M", "N", "O", "P"
    )

    fun departmentsFor(programName: String): List<String> =
        programs.firstOrNull { it.name.equals(programName, ignoreCase = true) }?.departments
            ?: programs.first().departments

    fun semestersFor(programName: String): List<Int> {
        val maxSem = programs.firstOrNull { it.name.equals(programName, ignoreCase = true) }?.maxSemesters ?: 8
        return (1..maxSem).toList()
    }

    fun yearsFor(programName: String): List<Int> {
        val maxSem = programs.firstOrNull { it.name.equals(programName, ignoreCase = true) }?.maxSemesters ?: 8
        val maxYears = ((maxSem + 1) / 2).coerceIn(1, 5)
        return (1..maxYears).toList()
    }

    fun yearFromSemester(semester: Int): Int =
        if (semester <= 0) 1 else ((semester - 1) / 2) + 1

    /**
     * Normalizes section input (e.g. "b", "Sec B", or legacy "2B") to its clean section code ("B").
     */
    fun normalizeSection(raw: String): String {
        val cleaned = raw.trim().uppercase()
            .removePrefix("SECTION")
            .removePrefix("SEC")
            .replace(Regex("[^A-Z0-9]"), "")
        if (cleaned.isEmpty()) return ""
        // Legacy format like "2B" / "3C" means Year 2 Sec B -> extract section letter(s)
        val strippedLeadingDigits = cleaned.dropWhile { it.isDigit() }
        return strippedLeadingDigits.ifEmpty { cleaned }
    }

    fun matchesBundledIot2B(department: String, semester: Int, section: String): Boolean {
        val deptNorm = department.lowercase()
        val isIotDept = deptNorm.contains("i.o.t") || deptNorm.contains("iot")
        val isYear2 = yearFromSemester(semester) == 2
        val secNorm = normalizeSection(section)
        return isIotDept && isYear2 && secNorm == "B"
    }

    /**
     * Maps a student's chosen department into keywords used to pre-chunk campus faculty & rooms
     * without scanning unrelated departments.
     */
    fun facultyDeptKeywords(department: String): Set<String> {
        val d = department.lowercase()
        return when {
            d.contains("i.o.t") || d.contains("iot") -> setOf("iot", "cse", "cs", "ece", "bsh", "math")
            d.contains("c.s.e") || d == "cse" || d == "csbs" || d == "csit" || d == "cst" || d == "it" ->
                setOf("cse", "cs", "it", "csbs", "csit", "cst", "ai", "ml", "ds", "cyber", "iot", "bsh")
            d.contains("robotics") -> setOf("robotics", "ai", "ece", "me", "cse", "ee")
            d == "ece" -> setOf("ece", "electronics", "ee", "bsh")
            d == "ee" || d == "eee" -> setOf("ee", "eee", "electrical", "ece", "bsh")
            d == "me" -> setOf("me", "mechanical", "bsh")
            d.contains("biotech") -> setOf("biotech", "bt", "bio", "bsh")
            d.contains("b.b.a") || d.contains("mba") || d.contains("analytics") ->
                setOf("bba", "mba", "management", "business", "analytics")
            d.contains("b.c.a") || d.contains("m.c.a") -> setOf("bca", "mca", "computer", "cse", "it")
            d == "bhta" || d == "hha" -> setOf("bhta", "hha", "hospitality", "hotel")
            d == "llb" -> setOf("llb", "law", "legal", "bba")
            else -> d.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }.toSet()
        }
    }
}

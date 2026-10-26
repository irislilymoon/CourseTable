package com.example.coursetable

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 标准化课表 JSON 解析 —— 格式与 tools/bit-timetable-export.user.js 导出一致。
 * 仅支持此一种格式: 从文件选择或直接粘贴, 其余格式一律不识别。
 */
object DataNormalizer {

    class Result(
        val data: TimetableData,
        val courseCount: Int,
        val unitCount: Int,
    ) {
        val summary: String
            get() = "识别成功: ${courseCount} 门课程 / ${unitCount} 个排课单元"
    }

    /** 周号列表 → 紧凑显示串(负数周 = 第一周之前的小学期周) */
    fun compactWeeks(weeks: List<Int>, regular: Int): String {
        if (weeks.isEmpty()) return ""
        val runs = mutableListOf<Pair<Int, Int>>()
        for (w in weeks.sorted()) {
            val last = runs.lastOrNull()
            if (last != null && w == last.second + 1) runs[runs.size - 1] = last.first to w
            else runs.add(w to w)
        }
        return runs.joinToString("、") { (a, b) ->
            val parts = mutableListOf<String>()
            if (a < 0) {
                // 负数周逐个显示, 不做区间压缩(避免 -2--1 这类双横线)
                for (w in a..minOf(b, -1)) parts.add("第${w}周")
            }
            if (b > 0) {
                val la = maxOf(a, 1)
                val rr = b.coerceAtMost(regular)
                if (la <= rr) parts.add(if (la == rr) "第${la}周" else "第${la}-${rr}周")
            }
            parts.joinToString("、")
        }
    }

    private fun str(o: JSONObject, key: String): String =
        o.optString(key).let { if (it == "null") "" else it.trim() }

    /** 标准化 JSON → TimetableData; 解析失败抛异常 */
    fun fromJson(text: String): TimetableData {
        val t = text.trim()
        if (!t.startsWith("{")) throw IllegalArgumentException("不是标准化课表 JSON")
        val root = JSONObject(t)
        val start = try {
            LocalDate.parse(str(root, "termStart"))
        } catch (e: Exception) {
            // 缺失时退化为"本周一", 由周数校正兜底
            LocalDate.now().let { it.minusDays(it.dayOfWeek.value - 1L) }
        }
        val periods = mutableListOf<PeriodTime>()
        val pa = root.optJSONArray("periodTimes")
        if (pa != null) for (i in 0 until pa.length()) {
            val p = pa.optJSONObject(i) ?: continue
            val n = p.optInt("period", 0)
            if (n >= 1) periods.add(PeriodTime(n, str(p, "start"), str(p, "end"), str(p, "section")))
        }

        val courses = mutableListOf<Course>()
        val ca = root.optJSONArray("courses")
        if (ca != null) for (i in 0 until ca.length()) {
            val co = ca.optJSONObject(i) ?: continue
            val name = str(co, "name")
            if (name.isEmpty()) continue
            val schedules = mutableListOf<Schedule>()
            val sa = co.optJSONArray("schedules")
            if (sa != null) for (j in 0 until sa.length()) {
                val so = sa.optJSONObject(j) ?: continue
                val weeks = so.optJSONArray("weeks")?.let { wa ->
                    (0 until wa.length()).mapNotNull { wa.optInt(it, 0) }.filter { it != 0 }
                } ?: emptyList()
                val day = so.optInt("day", 0)
                val p1 = so.optInt("startPeriod", 0)
                if (weeks.isEmpty() || day !in 1..7 || p1 < 1) continue
                val p2 = maxOf(p1, so.optInt("endPeriod", p1))
                schedules.add(
                    Schedule(
                        weeks, day, p1, p2,
                        str(so, "teacher"), str(so, "location"),
                        str(so, "classNo"), str(so, "note"),
                    ),
                )
            }
            if (schedules.isEmpty()) continue
            courses.add(
                Course(
                    name = name,
                    code = str(co, "code"),
                    department = str(co, "department"),
                    credit = co.optDouble("credit", 0.0),
                    nature = str(co, "nature"),
                    category = str(co, "category"),
                    subCategory = str(co, "subCategory"),
                    campus = str(co, "campus"),
                    colorIndex = if (co.has("colorIndex")) co.optInt("colorIndex", 0) else courses.size % 10,
                    schedules = schedules.sortedWith(compareBy({ it.day }, { it.startPeriod })),
                ),
            )
        }
        return TimetableData(
            version = root.optInt("version", 1),
            termName = str(root, "termName"),
            studentName = str(root, "studentName"),
            studentId = str(root, "studentId"),
            termStart = start,
            regularWeeks = root.optInt("regularWeeks", 16).coerceAtLeast(1),
            miniTermWeeks = root.optInt("miniTermWeeks", 0),
            periodTimes = periods.sortedBy { it.period },
            courses = courses,
        )
    }

    /** 标准化 JSON 文本导入入口 */
    fun importText(text: String): Result {
        if (text.trim().isEmpty()) throw IllegalArgumentException("导入内容为空")
        val d = fromJson(text)
        if (d.courses.isEmpty()) throw IllegalArgumentException("JSON 中没有课程安排")
        return Result(d, d.courses.size, d.courses.sumOf { it.schedules.size })
    }

    /** TimetableData → 标准化 JSON(持久化格式) */
    fun toJson(d: TimetableData): String {
        val root = JSONObject()
        root.put("version", d.version)
        root.put("termName", d.termName)
        root.put("studentName", d.studentName)
        root.put("studentId", d.studentId)
        root.put("termStart", d.termStart.toString())
        root.put("regularWeeks", d.regularWeeks)
        root.put("miniTermWeeks", d.miniTermWeeks)
        root.put("periodTimes", JSONArray(d.periodTimes.map {
            JSONObject().put("period", it.period).put("start", it.start)
                .put("end", it.end).put("section", it.section)
        }))
        root.put("courses", JSONArray(d.courses.map { c ->
            JSONObject()
                .put("name", c.name).put("code", c.code).put("department", c.department)
                .put("credit", c.credit).put("nature", c.nature).put("category", c.category)
                .put("subCategory", c.subCategory).put("campus", c.campus)
                .put("colorIndex", c.colorIndex)
                .put("schedules", JSONArray(c.schedules.map { s ->
                    JSONObject()
                        .put("weeks", JSONArray(s.weeks)).put("day", s.day)
                        .put("startPeriod", s.startPeriod).put("endPeriod", s.endPeriod)
                        .put("teacher", s.teacher).put("location", s.location)
                        .put("classNo", s.classNo).put("note", s.note)
                }))
        }))
        return root.toString(2)
    }
}

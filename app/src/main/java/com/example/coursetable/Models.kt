package com.example.coursetable

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 按当前日期自动推断学期名(无手动覆盖时顶栏显示用):
 *  - 1 月仍属上学年(y-1)-y 的第 1 学期
 *  - 2-7 月属 (y-1)-y 学年第 2 学期
 *  - 8-12 月属新学年 y-(y+1) 第 1 学期
 */
fun autoTermName(today: LocalDate): String {
    val (startYear, no) = when (today.monthValue) {
        1 -> today.year - 1 to 1          // 1月 仍属 (y-1)-y 第1学期
        in 2..7 -> today.year - 1 to 2    // 2-7月 第2学期
        else -> today.year to 1           // 8-12月 新学年 第1学期
    }
    return "${startYear}-${startYear + 1}学年 第${no}学期"
}

/** 节次时间(作息表) */
data class PeriodTime(
    val period: Int,
    val start: String,
    val end: String,
    val section: String, // 上午 / 下午 / 晚上
)

/**
 * 一条排课单元: 某课程的 {周次集合, 星期, 节次区间, 教师, 教室}
 * 周次已规范化: 负数小学期周映射为 regularWeeks + |n|
 */
data class Schedule(
    val weeks: List<Int>,
    val day: Int, // 1=周一 .. 7=周日
    val startPeriod: Int,
    val endPeriod: Int,
    val teacher: String = "",
    val location: String = "",
    val classNo: String = "",
    val note: String = "",
) {
    val span: Int get() = endPeriod - startPeriod + 1
}

data class Course(
    val name: String,
    val code: String = "",
    val department: String = "",
    val credit: Double = 0.0,
    val nature: String = "",
    val category: String = "",
    val subCategory: String = "",
    val campus: String = "",
    val colorIndex: Int = 0,
    val schedules: List<Schedule> = emptyList(),
)

data class TimetableData(
    val version: Int = 1,
    val termName: String = "",
    val studentName: String = "",
    val studentId: String = "",
    val termStart: LocalDate = LocalDate.now(),
    val regularWeeks: Int = 16,
    val miniTermWeeks: Int = 0,
    val periodTimes: List<PeriodTime> = emptyList(),
    val courses: List<Course> = emptyList(),
) {
    /** 可翻阅周序列: 小学期周(-mini..-1)在第一周之前, 正常教学周(1..regular)在后; 无第 0 周 */
    val weekSequence: List<Int>
        get() = ((-miniTermWeeks..-1).toList() + (1..regularWeeks).toList())

    /** 周标签, 负数周按系统原始标法直接显示: 第-1周 */
    fun weekLabel(week: Int): String = "第${week}周"

    /** 周号在周序列中的下标 = 距 termStart 的日历周偏移(0 = termStart 那一周); 序列跳过 0 周 */
    private fun seqIndex(week: Int): Int =
        if (week < 0) week + miniTermWeeks else week + miniTermWeeks - 1

    fun dateOf(week: Int, day: Int): LocalDate =
        termStart.plusDays(seqIndex(week) * 7L + (day - 1))

    fun timeOf(period: Int): PeriodTime? = periodTimes.firstOrNull { it.period == period }

    fun periodClock(start: Int, end: Int): String {
        val a = timeOf(start)
        val b = timeOf(end)
        return if (a != null && b != null) "${a.start}-${b.end}" else ""
    }

    /** 今天所在教学周: termStart 那一周 = 周序列首项(第-mini 或第 1 周), 之后逐周顺推, 超范围夹到边界周 */
    fun currentWeek(today: LocalDate): Int {
        val seq = weekSequence
        val off = ChronoUnit.DAYS.between(termStart, today).floorDiv(7L).toInt()
        return seq[off.coerceIn(0, seq.size - 1)]
    }

    /** 今天若落在参数周的周一~周日范围内, 返回周几(1..7), 否则 null(用于今天列/时间线判定) */
    fun todayDow(week: Int, today: LocalDate): Int? {
        val monday = dateOf(week, 1)
        return if (!today.isBefore(monday) && today.isBefore(monday.plusDays(7))) today.dayOfWeek.value else null
    }
}

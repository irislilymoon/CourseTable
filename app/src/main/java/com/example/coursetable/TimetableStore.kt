package com.example.coursetable

import android.content.Context
import java.io.File

/** 课表持久化: 内部存储为主, 数据一律经导入页(文件/粘贴)以标准化 JSON 导入 */
object TimetableStore {

    private const val FILE = "timetable.json"

    // 数据语义版本: 2 = 小学期负数周位于第一周之前(v1 的旧数据把小学期排在学期末, 需重建)
    // 3 = 22 个教学周; 4 = 仅接受标准化 JSON, 学期/姓名/学期起点全部以 JSON 为准(旧数据需重新导入)
    // 5 = termStart 锚点改为学期首周(负数小学期周或第 1 周)的周一, 周数纯按系统日期推算(旧数据需重新导入)
    private const val SCHEMA_VER = 5

    /** 架构升级时清除旧已存数据, 回到空白状态等待重新导入 */
    fun migrate(context: Context) {
        val sp = context.getSharedPreferences("schema", Context.MODE_PRIVATE)
        if (sp.getInt("ver", 0) < SCHEMA_VER) {
            File(context.filesDir, FILE).delete()
            sp.edit().putInt("ver", SCHEMA_VER).apply()
        }
    }

    fun load(context: Context): TimetableData? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return runCatching { DataNormalizer.fromJson(f.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun save(context: Context, data: TimetableData) {
        File(context.filesDir, FILE).writeText(DataNormalizer.toJson(data), Charsets.UTF_8)
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /** 主题模式: SharedPreferences "settings", 键 "theme_mode", 默认跟随系统 */
    fun loadTheme(context: Context): ThemeMode {
        val name = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("theme_mode", ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun saveTheme(context: Context, mode: ThemeMode) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode.name).apply()
    }
}

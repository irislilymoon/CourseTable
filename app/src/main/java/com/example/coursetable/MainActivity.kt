package com.example.coursetable

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CourseTableApp()
        }
    }
}

/** 主题模式: 跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val paletteLight = listOf(
    Color(0xFFE1F5FE) to Color(0xFF0277BD),
    Color(0xFFE8F5E9) to Color(0xFF2E7D32),
    Color(0xFFFCE4EC) to Color(0xFFC2185B),
    Color(0xFFFFF3E0) to Color(0xFFE65100),
    Color(0xFFF3E5F5) to Color(0xFF6A1B9A),
    Color(0xFFE0F7FA) to Color(0xFF00695C),
    Color(0xFFFFFDE7) to Color(0xFF9E9D24),
    Color(0xFFECEFF1) to Color(0xFF37474F),
    Color(0xFFFFEBEE) to Color(0xFFB71C1C),
    Color(0xFFE3F2FD) to Color(0xFF1565C0),
)

/** 暗色模式课程配色(顺序与 paletteLight 一一对应): 深色底 + 同系浅色字 */
private val paletteDark = listOf(
    Color(0xFF1C3B5A) to Color(0xFF9ECAFF),
    Color(0xFF1E3B2A) to Color(0xFFA5D6A7),
    Color(0xFF4A2538) to Color(0xFFF8BBD0),
    Color(0xFF4A3018) to Color(0xFFFFCC80),
    Color(0xFF392A4D) to Color(0xFFD1C4E9),
    Color(0xFF1A3B38) to Color(0xFF9CD5CE),
    Color(0xFF3A3D1E) to Color(0xFFE6EE9C),
    Color(0xFF2E3438) to Color(0xFFB0BEC5),
    Color(0xFF4A2323) to Color(0xFFEF9A9A),
    Color(0xFF233559) to Color(0xFFA9C3F5),
)

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val dateFmt = DateTimeFormatter.ofPattern("MM.dd")
private val todayStroke = 1.dp
private val todayRadius = 10.dp
private val gutterW = 42.dp

/** "HH:mm" → 当日分钟数 */
private fun minutesOf(s: String): Int? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
    val h = m.groupValues[1].toIntOrNull() ?: return null
    val min = m.groupValues[2].toIntOrNull() ?: return null
    return h * 60 + min
}

/** 当前时刻映射到网格 y 坐标(按作息表分段线性, 课间也线性过渡); 作息时间范围外返回 null */
private fun nowLineY(d: TimetableData, cellH: Dp, now: LocalTime): Dp? {
    val anchors = mutableListOf<Pair<Int, Dp>>()
    for (pt in d.periodTimes.sortedBy { it.period }) {
        val s = minutesOf(pt.start) ?: continue
        val e = minutesOf(pt.end) ?: continue
        anchors += s to cellH * (pt.period - 1)
        anchors += e to (cellH * pt.period)
    }
    if (anchors.size < 2) return null
    anchors.sortBy { it.first }
    val mins = now.hour * 60 + now.minute
    if (mins < anchors.first().first || mins > anchors.last().first) return null
    for (i in 0 until anchors.size - 1) {
        val (m1, y1) = anchors[i]
        val (m2, y2) = anchors[i + 1]
        if (mins in m1..m2) {
            if (m2 == m1) return y1
            return y1 + (y2 - y1) * ((mins - m1).toFloat() / (m2 - m1).toFloat())
        }
    }
    return null
}

/** 圆弧折线: 极角从 fromDeg 到 toDeg(度, y 向下为正, 0°=右 90°=下), 逐点 lineTo */
private fun Path.arcPoints(cx: Float, cy: Float, r: Float, fromDeg: Float, toDeg: Float) {
    val steps = 10
    for (i in 1..steps) {
        val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * i / steps).toDouble())
        lineTo(cx + (r * kotlin.math.cos(a)).toFloat(), cy + (r * kotlin.math.sin(a)).toFloat())
    }
}

/** 表头段: 左边线 → 左上圆弧 → 顶边线 → 右上圆弧 → 右边线(下端延伸至接缝) */
private fun DrawScope.strokeTopRounded(color: Color, strokePx: Float, radiusPx: Float) {
    val half = strokePx / 2f
    val r = (radiusPx - strokePx * 1.5f).coerceAtLeast(0f)
    val path = Path().apply {
        moveTo(half, size.height)
        lineTo(half, half + r)
        arcPoints(half + r, half + r, r, 180f, 270f)
        lineTo(size.width - half - r, half)
        arcPoints(size.width - half - r, half + r, r, 270f, 360f)
        lineTo(size.width - half, size.height)
    }
    drawPath(path, color, style = Stroke(width = strokePx))
}

/** 网格段: 左边线(上端起于接缝) → 左下圆弧 → 底边线 → 右下圆弧 → 右边线 */
private fun DrawScope.strokeBottomRounded(color: Color, strokePx: Float, radiusPx: Float) {
    val half = strokePx / 2f
    val r = (radiusPx - strokePx * 1.5f).coerceAtLeast(0f)
    val bottom = size.height - half
    val path = Path().apply {
        moveTo(half, 0f)
        lineTo(half, bottom - r)
        arcPoints(half + r, bottom - r, r, 180f, 90f)
        lineTo(size.width - half - r, bottom)
        arcPoints(size.width - half - r, bottom - r, r, 90f, 0f)
        lineTo(size.width - half, 0f)
    }
    drawPath(path, color, style = Stroke(width = strokePx))
}

@Composable
fun CourseTableApp() {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(TimetableStore.loadTheme(context)) }
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 状态栏图标明暗随主题切换: 浅色主题用深色图标, 深色主题用浅色图标
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
    ) {
        // MaterialTheme 不提供 LocalContentColor, 缺省恒为黑色 → 暗色模式下未显式指定颜色的
        // 文字(标题/周数/节号等)全是黑字。全局提供 onSurface, 未指定颜色的文字统一随主题适配
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        var storedData by remember {
            // 首帧前同步读盘(文件仅几十 KB, 毫秒级): 若走 LaunchedEffect 异步加载,
            // 第一帧会先画"还没有课程"空白页再跳课表, 每次冷启动都闪一下
            TimetableStore.migrate(context)
            mutableStateOf(TimetableStore.load(context))
        }
        var showImport by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var detail by remember { mutableStateOf<Pair<Course, Schedule>?>(null) }

        val d = storedData
        if (d == null) {
            // 空白状态页: 只保留顶栏(学期名/设置/导入入口)与中央引导, 不渲染周导航/表头/网格
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding(),
            ) {
                TopBar(
                    termName = autoTermName(LocalDate.now()),
                    studentName = "",
                    onImport = { showImport = true },
                    onSettings = { showSettings = true },
                )
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有课程", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "导入你的课表后在这里显示",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = { showImport = true },
                            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
                        ) {
                            Text("导入数据", fontSize = 17.sp)
                        }
                    }
                }
            }
        } else {
            var week by remember(d) { mutableStateOf(d.currentWeek(LocalDate.now())) }

            // 每次回到 App(含从后台切回)都重算并定位到本周
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, d) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        week = d.currentWeek(LocalDate.now())
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // 今天所在列: 仅当今天确实落在当前浏览的这一周内才高亮, 1=周一..7=周日
            val todayDow = d.todayDow(week, LocalDate.now())

            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                TopBar(
                    termName = d.termName.ifBlank { autoTermName(LocalDate.now()) },
                    studentName = d.studentName,
                    onImport = { showImport = true },
                    onSettings = { showSettings = true },
                )
                val seq = d.weekSequence
                WeekNav(
                    d, week,
                    onPrev = {
                        val i = seq.indexOf(week)
                        if (i > 0) week = seq[i - 1]
                    },
                    onNext = {
                        val i = seq.indexOf(week)
                        if (i < seq.size - 1) week = seq[i + 1]
                    },
                    onToday = { week = d.currentWeek(LocalDate.now()) },
                )
                DayHeader(d, week, todayDow)
                Grid(d, week, todayDow, dark) { c, s -> detail = c to s }
            }

            detail?.let { (c, s) -> CourseDetailDialog(d, c, s) { detail = null } }
        }

        // 导入/设置覆盖层: 空白状态与周视图共用
        if (showImport) {
            ImportScreen(
                onApply = { newData ->
                    TimetableStore.save(context, newData)
                    storedData = newData
                    showImport = false
                },
                onBack = { showImport = false },
            )
        }

        if (showSettings) {
            SettingsScreen(
                themeMode = themeMode,
                onThemeChange = { mode ->
                    themeMode = mode
                    TimetableStore.saveTheme(context, mode)
                },
                onBack = { showSettings = false },
            )
        }
        }
    }
}

@Composable
private fun TopBar(termName: String, studentName: String, onImport: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("课程表", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                listOf(termName, studentName).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onSettings) { Text("设置") }
        TextButton(onClick = onImport) { Text("导入数据") }
    }
}

@Composable
private fun WeekNav(
    d: TimetableData,
    week: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        // +/− 与周数标签构成的三件套整体在屏幕水平居中
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            NavButton(isPlus = false, onClick = onPrev)
            Spacer(Modifier.width(4.dp))
            // 固定宽度标签容器: 周数/日期在格内居中, +/− 不随"第-3周/第22周"的字宽变化而移动
            Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.weekLabel(week), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${d.dateOf(week, 1).format(dateFmt)} - ${d.dateOf(week, 7).format(dateFmt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(4.dp))
            NavButton(isPlus = true, onClick = onNext)
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            TodayButton(onClick = onToday)
        }
    }
}

/** 按压变深的胶囊按钮: 与 +/− 的按压反馈逻辑一致(按下时整体变深) */
@Composable
private fun PressPillButton(
    label: String,
    onClick: () -> Unit,
    fontSize: TextUnit = 15.sp,
    hPad: Dp = 18.dp,
    vPad: Dp = 7.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (pressed) lerp(container, MaterialTheme.colorScheme.primary, 0.30f) else container,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = fontSize, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** "今"胶囊按钮: 与 +/− 同色系同高度, 风格统一 */
@Composable
private fun TodayButton(onClick: () -> Unit) {
    PressPillButton("今", onClick = onClick, fontSize = 14.sp, hPad = 13.dp, vPad = 7.dp)
}

@Composable
private fun NavButton(isPlus: Boolean, onClick: () -> Unit) {
    // clip 必须在 clickable 之前, 否则按压水波纹按矩形边界显示
    // 符号用 Canvas 几何绘制: 不依赖字体度量, 任何厂商字体都精确居中
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
    ) {
        val color = MaterialTheme.colorScheme.onPrimaryContainer
        Canvas(Modifier.matchParentSize()) {
            val strokeW = 2.dp.toPx()
            val arm = 7.dp.toPx()
            drawLine(
                color = color,
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
            if (isPlus) {
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - arm),
                    end = Offset(center.x, center.y + arm),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(d: TimetableData, week: Int, todayDow: Int?) {
    // 不加横向 padding, 与 Grid 列边界精确对齐
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(gutterW))
        repeat(7) { i ->
            val day = i + 1
            val isToday = todayDow == day
            val todayLine = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (isToday) Modifier
                            // 描边放在 clip 之前: 不被圆角裁剪, 圆弧处也有包围线
                            .drawBehind { strokeTopRounded(todayLine, todayStroke.toPx(), todayRadius.toPx()) }
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        else Modifier
                    ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "周${dayNames[i]}",
                        fontSize = 13.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        d.dateOf(week, day).format(dateFmt),
                        fontSize = 10.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 课程卡文字行数分配(全局通用, 替代写死的 maxLines):
 * 以块内可用高度换算行数预算, 先保底 — name 2 行, location/teacher 各 1 行(仅在对应 span 档显示时);
 * 多余预算按优先级分配: name 补到实测需要行数 → location(上限 3) → teacher(上限 2), 均不超过各文本实测需要。
 * 块过小(字体放大/矮屏)放不下保底时自下而上收缩: teacher → location → name 降为 1 行。
 * 各元素真实需要行数用 TextMeasurer 按卡片内文字宽度实测, 真正超出预算才 Ellipsis。
 */
private fun allocateLines(
    name: String,
    location: String,
    teacher: String,
    availPx: Float,
    nameLhPx: Float,
    smallLhPx: Float,
    maxWidthPx: Int,
    textMeasurer: TextMeasurer,
    nameStyle: TextStyle,
    smallStyle: TextStyle,
): Triple<Int, Int, Int> {
    fun need(text: String, style: TextStyle): Int =
        if (text.isBlank()) 0
        else textMeasurer.measure(
            AnnotatedString(text), style,
            constraints = Constraints(maxWidth = maxWidthPx),
        ).lineCount

    val needName = need(name, nameStyle)
    val needLoc = need(location, smallStyle)
    val needTea = need(teacher, smallStyle)
    val showLoc = location.isNotBlank()
    val showTea = teacher.isNotBlank()

    // 保底行数
    var nameLines = 2
    var locLines = if (showLoc) 1 else 0
    var teaLines = if (showTea) 1 else 0
    var rem = availPx - nameLines * nameLhPx - (locLines + teaLines) * smallLhPx
    // 保底放不下: 自下而上砍
    if (rem < 0 && teaLines > 0) { teaLines = 0; rem += smallLhPx }
    if (rem < 0 && locLines > 0) { locLines = 0; rem += smallLhPx }
    if (rem < 0 && nameLines > 1) { nameLines = 1; rem += nameLhPx }
    // 多余预算: name 优先补到实际需要, 再 location(≤3), 再 teacher(≤2)
    if (rem > 0) {
        val take = minOf(needName - nameLines, (rem / nameLhPx).toInt())
        if (take > 0) { nameLines += take; rem -= take * nameLhPx }
    }
    if (rem > 0 && showLoc) {
        val take = minOf(minOf(needLoc, 3) - locLines, (rem / smallLhPx).toInt())
        if (take > 0) { locLines += take; rem -= take * smallLhPx }
    }
    if (rem > 0 && showTea) {
        val take = minOf(minOf(needTea, 2) - teaLines, (rem / smallLhPx).toInt())
        if (take > 0) { teaLines += take; rem -= take * smallLhPx }
    }
    return Triple(nameLines, locLines, teaLines)
}

@Composable
private fun Grid(
    d: TimetableData,
    week: Int,
    todayDow: Int?,
    dark: Boolean,
    onTap: (Course, Schedule) -> Unit,
) {
    // 抬到导航栏之上, 保证"今天列"底部封口线和圆角可见
    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 自适应行高: 屏幕够高用 52dp, 否则压缩让 13 节完整放下不截断
            val cellH = maxOf(40.dp, minOf(52.dp, (maxHeight - 4.dp) / 13))
            val gridH = cellH * 13
            // 课程卡配色随暗色模式切换
            val palette = if (dark) paletteDark else paletteLight
            // 网格分隔线颜色取自 colorScheme, 暗色模式自动适配; 横向虚线画在节次边界 {2,5,7,10} 与下一节之间
            val vLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            val dashLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            // BoxWithConstraintsScope 不是 Density, 像素换算须经 LocalDensity
            val density = LocalDensity.current
            val dashStroke = with(density) { 1.dp.toPx() }
            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 4.dp.toPx() }),
            )
            // 课程卡文字按块高动态分配行数: 公共测量资源(卡片内文字最大宽度 = 7 列均分 - 左右 padding 8dp)
            val textMeasurer = rememberTextMeasurer()
            val cardTextMaxWpx = with(density) { ((maxWidth - gutterW) / 7 - 8.dp).toPx().roundToInt() }
            // 滚动容器为 Box: 当前时间横线作为第二子级, 与网格同坐标系一起滚动
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().height(gridH)) {
        // 左侧时间栏: 节号 + 每节开始时间; 时段名只在段首(第1/6/11节)标注在该格最顶部
        Column(
            Modifier
                .width(gutterW)
                // 节次栏内的横向虚线段, 与右侧各天列的段同 y 连成整行
                .drawBehind {
                    for (p in intArrayOf(2, 5, 7, 10)) {
                        val y = cellH.toPx() * p
                        drawLine(dashLine, Offset(0f, y), Offset(size.width, y), strokeWidth = dashStroke, pathEffect = dashEffect)
                    }
                },
        ) {
            for (p in 1..13) {
                val pt = d.timeOf(p)
                Box(Modifier.height(cellH).fillMaxWidth()) {
                    // 节号 + 开始时间(段首 1/6/11 上方再加时段名)同列垂直居中: 同一 Column 内天然不重叠
                    // 显式 lineHeight 压掉 LocalTextStyle 的 24sp 默认行高, 否则行距过大且段首三行块溢出格子
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (p == 1 || p == 6 || p == 11) {
                            Text(
                                pt?.section ?: "",
                                fontSize = 10.sp, lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text("$p", fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            pt?.start ?: "",
                            fontSize = 10.sp, lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // 周一至周日 7 列
        for (day in 1..7) {
            val isToday = todayDow == day
            val todayLine = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            Box(
                Modifier
                    .weight(1f)
                    .height(gridH)
                    .then(
                        if (isToday) Modifier
                            .drawBehind { strokeBottomRounded(todayLine, todayStroke.toPx(), todayRadius.toPx()) }
                            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            // 虚线画在今天列底色之上、课程块之下
                            .drawBehind {
                                for (p in intArrayOf(2, 5, 7, 10)) {
                                    val y = cellH.toPx() * p
                                    drawLine(dashLine, Offset(0f, y), Offset(size.width, y), strokeWidth = dashStroke, pathEffect = dashEffect)
                                }
                            }
                        else Modifier
                            // 非今天列: 第 2~7 天左缘竖线 + 横向虚线段, 均画在课程块下层
                            .drawBehind {
                                if (day >= 2) {
                                    drawLine(vLine, Offset(dashStroke / 2f, 0f), Offset(dashStroke / 2f, size.height), strokeWidth = dashStroke)
                                }
                                for (p in intArrayOf(2, 5, 7, 10)) {
                                    val y = cellH.toPx() * p
                                    drawLine(dashLine, Offset(0f, y), Offset(size.width, y), strokeWidth = dashStroke, pathEffect = dashEffect)
                                }
                            }
                    ),
            ) {
                val blocks = d.courses.flatMap { c ->
                    c.schedules.filter { week in it.weeks && it.day == day }.map { c to it }
                }.sortedByDescending { it.second.span } // 大块画在下, 小块覆盖在上
                for ((c, s) in blocks) {
                    val (bg, fg) = palette[c.colorIndex % palette.size]
                    // 按压态变色反馈: 按下时背景向深色文字色混 10%, 与 PressPillButton 同思路
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Box(
                        Modifier
                            .offset(y = cellH * (s.startPeriod - 1))
                            .height(cellH * s.span)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (pressed) lerp(bg, fg, 0.10f) else bg)
                            // 同色系深色圆角包围线(文字色 45% 叠加)
                            .border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = interaction, indication = null) { onTap(c, s) }
                            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                    ) {
                        // ---- 行数按块高动态分配(实测各文本需要行数, 不再写死 maxLines) ----
                        val showLoc = s.span >= 2 && s.location.isNotBlank()
                        val showTea = s.span >= 3 && s.teacher.isNotBlank()
                        val nameFs = if (s.span >= 2) 12.sp else 10.sp
                        val nameLh = if (s.span >= 2) 14.sp else 11.sp
                        val nameStyle = LocalTextStyle.current.copy(
                            fontSize = nameFs, lineHeight = nameLh, fontWeight = FontWeight.Bold,
                        )
                        val smallStyle = LocalTextStyle.current.copy(fontSize = 10.sp, lineHeight = 12.sp)
                        val (nameLines, locLines, teaLines) = remember(
                            c.name, s.location, s.teacher, s.span, cardTextMaxWpx, nameStyle, smallStyle,
                        ) {
                            allocateLines(
                                name = c.name,
                                location = if (showLoc) "@${s.location}" else "",
                                teacher = if (showTea) s.teacher else "",
                                // 可用高度 = 块高 - 上下 padding(4+2dp) - 3dp 块间余量
                                availPx = with(density) { (cellH * s.span - 9.dp).toPx() },
                                nameLhPx = with(density) { nameLh.toPx() },
                                smallLhPx = with(density) { 12.sp.toPx() },
                                maxWidthPx = cardTextMaxWpx,
                                textMeasurer = textMeasurer,
                                nameStyle = nameStyle,
                                smallStyle = smallStyle,
                            )
                        }
                        Column {
                            Text(
                                c.name,
                                fontSize = nameFs,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                                maxLines = nameLines,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = nameLh,
                            )
                            if (showLoc && locLines > 0) {
                                Text(
                                    "@${s.location}",
                                    fontSize = 10.sp,
                                    color = fg,
                                    maxLines = locLines,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp,
                                )
                            }
                            if (showTea && teaLines > 0) {
                                Text(
                                    s.teacher,
                                    fontSize = 10.sp,
                                    color = fg,
                                    maxLines = teaLines,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
                }
                // 当前时间横线: 仅本周视图且在作息时间范围内显示, 贯穿时间栏与课程块
                if (todayDow != null) {
                    var nowTick by remember { mutableStateOf(LocalTime.now()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(60_000)
                            nowTick = LocalTime.now()
                        }
                    }
                    nowLineY(d, cellH, nowTick)?.let { y ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .offset(y = y)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(d: TimetableData, c: Course, s: Schedule, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { PressPillButton("关闭", onClick = onClose) },
        title = { Text(c.name, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                DetailRow(
                    "时间",
                    "周${dayNames[s.day - 1]} 第${s.startPeriod}" +
                        (if (s.endPeriod != s.startPeriod) "-${s.endPeriod}" else "") + "节" +
                        d.periodClock(s.startPeriod, s.endPeriod).let { if (it.isNotBlank()) " ($it)" else "" },
                )
                DetailRow("周次", DataNormalizer.compactWeeks(s.weeks, d.regularWeeks))
                if (s.location.isNotBlank()) DetailRow("地点", s.location)
                if (s.teacher.isNotBlank()) DetailRow("教师", s.teacher)
                if (s.classNo.isNotBlank()) DetailRow("班级", s.classNo)
                if (c.credit > 0) DetailRow("学分", c.credit.toString())
                if (c.code.isNotBlank()) DetailRow("课程号", c.code)
                if (c.department.isNotBlank()) DetailRow("开课单位", c.department)
                if (c.nature.isNotBlank() || c.category.isNotBlank()) {
                    DetailRow("性质", listOf(c.nature, c.category).filter { it.isNotBlank() }.joinToString(" / "))
                }
                if (s.note.isNotBlank()) DetailRow("备注", s.note)
            }
        },
    )
}

@Composable
private fun DetailRow(k: String, v: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            k, fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
        )
        Text(v, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ImportScreen(
    onApply: (TimetableData) -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // 解析成功的数据: 短暂展示"已导入 X 门课程"后自动应用返回主界面
    var pending by remember { mutableStateOf<TimetableData?>(null) }

    fun doImport(src: String) {
        try {
            val r = DataNormalizer.importText(src)
            message = "已导入 ${r.courseCount} 门课程"
            error = false
            pending = r.data
        } catch (e: Exception) {
            message = "识别失败: ${e.message}"
            error = true
        }
    }

    LaunchedEffect(pending) {
        pending?.let { data ->
            delay(900)
            onApply(data)
        }
    }

    // 系统文件选择器: 选 JSON 文件后自动读取导入
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val loaded = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > 5 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
                }
            }.getOrNull()
            when {
                loaded == null -> { message = "无法读取所选文件(超过 5MB 或不可读)"; error = true }
                loaded.isBlank() -> { message = "所选文件内容为空"; error = true }
                else -> doImport(loaded)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .statusBarsPadding()
            // 消费空白处点击, 防止穿透到下层主界面的课程卡
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(12.dp),
    ) {
        // Box 布局: 标题绝对居中, 返回键靠左(右侧无按钮)
        Box(Modifier.fillMaxWidth()) {
            Text(
                "导入数据", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("‹ 返回")
            }
        }
        Text(
            "仅支持教务导出的标准化课表 JSON, 学期与姓名会自动填充",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        OutlinedButton(
            onClick = { filePicker.launch(arrayOf("application/json", "text/*")) },
            modifier = Modifier.padding(vertical = 4.dp),
        ) { Text("从文件选择 (.json)") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; message = "" },
            modifier = Modifier.fillMaxWidth().height(280.dp),
            placeholder = { Text("粘贴JSON文本", fontSize = 12.sp) },
            textStyle = TextStyle(fontSize = 11.sp),
        )
        Row(Modifier.padding(vertical = 8.dp)) {
            TextButton(
                onClick = { doImport(text) },
                enabled = text.isNotBlank(),
            ) { Text("导入") }
        }
        if (message.isNotBlank()) {
            Text(
                message, fontSize = 12.sp,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .statusBarsPadding()
            // 消费空白处点击, 防止穿透到下层主界面的课程卡
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "设置", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text("‹ 返回", fontSize = 15.sp)
            }
        }
        Text(
            "外观", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        ThemeOption(
            title = "跟随系统",
            selected = themeMode == ThemeMode.SYSTEM,
        ) { onThemeChange(ThemeMode.SYSTEM) }
        ThemeOption(
            title = "浅色",
            selected = themeMode == ThemeMode.LIGHT,
        ) { onThemeChange(ThemeMode.LIGHT) }
        ThemeOption(
            title = "深色",
            selected = themeMode == ThemeMode.DARK,
        ) { onThemeChange(ThemeMode.DARK) }
        Text(
            "关于", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
        )
        Text(
            "课程表 Lite v1.4.4",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 外观主题单选行 */
@Composable
private fun ThemeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            title, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

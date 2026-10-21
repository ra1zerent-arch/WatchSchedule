package com.watch.schedule

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.Calendar
import java.util.Locale

data class Lesson(val num: Int, val name: String, val room: String)

data class Bell(val num: Int, val startH: Int, val startM: Int, val endH: Int, val endM: Int) {
    val startTotal get() = startH * 60 + startM
    val endTotal get() = endH * 60 + endM
    val timeStr get() = String.format(Locale.getDefault(), "%d:%02d – %d:%02d", startH, startM, endH, endM)
}

data class LiveStatus(
    val timerText: String,
    val timerColorHex: String,
    val labelText: String,
    val currentText: String,
    val nextText: String,
    val progressPercent: Int,
    val footerTodayText: String,
    val footerWeekendText: String
)

object ScheduleRepo {
    private const val PREFS_NAME = "watch_schedule_prefs"

    val bells = listOf(
        Bell(1, 8, 30, 9, 15),
        Bell(2, 9, 25, 10, 10),
        Bell(3, 10, 30, 11, 15),
        Bell(4, 11, 35, 12, 20),
        Bell(5, 12, 40, 13, 25),
        Bell(6, 13, 45, 14, 30),
        Bell(7, 14, 40, 15, 25)
    )

    val schedule = mapOf(
        Calendar.MONDAY to listOf(
            Lesson(1, "Разговор о важном", "315"),
            Lesson(2, "Алгебра", "220"),
            Lesson(3, "Химия", "310"),
            Lesson(4, "Русский язык", "315"),
            Lesson(5, "Вероятность и стат.", "217"),
            Lesson(6, "Литература", "305"),
            Lesson(7, "Английский яз.", "317/327")
        ),
        Calendar.TUESDAY to listOf(
            Lesson(1, "История", "325"),
            Lesson(2, "Физика", "305"),
            Lesson(3, "География", "319"),
            Lesson(4, "Геометрия", "220"),
            Lesson(5, "Английский яз.", "317/327"),
            Lesson(6, "Химия", "310"),
            Lesson(7, "Биология", "326")
        ),
        Calendar.WEDNESDAY to listOf(
            Lesson(1, "Русский язык", "315"),
            Lesson(2, "Физкультура", "Зал"),
            Lesson(3, "История", "325"),
            Lesson(4, "ОБЗР", "201"),
            Lesson(5, "Алгебра", "220"),
            Lesson(6, "Биология", "326"),
            Lesson(7, "Геометрия", "220")
        ),
        Calendar.THURSDAY to listOf(
            Lesson(1, "Английский яз.", "317/327"),
            Lesson(2, "Физика", "325"),
            Lesson(3, "Физкультура", "Зал"),
            Lesson(4, "Информатика", "219/223"),
            Lesson(5, "Литература", "315"),
            Lesson(6, "Россия - мои гориз.", "315"),
            Lesson(7, "Алгебра", "220")
        ),
        Calendar.FRIDAY to listOf(
            Lesson(1, "География", "319"),
            Lesson(2, "Русский язык", "315"),
            Lesson(3, "История", "325"),
            Lesson(4, "Геометрия", "220"),
            Lesson(5, "Физкультура", "Зал"),
            Lesson(6, "Музыка", "113"),
            Lesson(7, "Труд (технология)", "119/121")
        )
    )

    fun getRoom(context: Context, day: Int, lessonNum: Int, defaultRoom: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString("room_${day}_$lessonNum", null)
        return if (!custom.isNullOrEmpty()) "$custom*" else defaultRoom
    }

    fun setCustomRoom(context: Context, day: Int, lessonNum: Int, newRoom: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (newRoom.isNullOrBlank()) {
            prefs.remove("room_${day}_$lessonNum")
        } else {
            prefs.putString("room_${day}_$lessonNum", newRoom.trim())
        }
        prefs.apply()
    }

    fun getCheatSheet(subjectName: String): Pair<String, String> {
        val s = subjectName.lowercase(Locale.getDefault())
        return when {
            s.contains("алгебр") -> "Шпора: Алгебра" to
                "• Квадратное ур-е: ax² + bx + c = 0\n" +
                "  D = b² - 4ac\n" +
                "  x = (-b ± √D) / 2a\n\n" +
                "• Теорема Виета (при a=1):\n" +
                "  x₁ + x₂ = -b  |  x₁ · x₂ = c\n\n" +
                "• Формулы (ФСУ):\n" +
                "  (a±b)² = a² ± 2ab + b²\n" +
                "  a² - b² = (a - b)(a + b)"

            s.contains("геометр") -> "Шпора: Геометрия" to
                "• Теорема Пифагора:\n" +
                "  c² = a² + b²\n\n" +
                "• Площади фигур:\n" +
                "  Треугольник: S = ½ · a · h\n" +
                "  Параллелограмм: S = a · h\n" +
                "  Трапеция: S = ((a + b) / 2) · h"

            s.contains("физик") -> "Шпора: Физика" to
                "• Тепловые явления:\n" +
                "  Нагрев: Q = c · m · Δt\n" +
                "  Плавление: Q = λ · m\n" +
                "  Сгорание: Q = q · m\n\n" +
                "• Электричество:\n" +
                "  Закон Ома: I = U / R\n" +
                "  Мощность: P = U · I"

            s.contains("хими") -> "Шпора: Химия" to
                "• Формулы:\n" +
                "  n = m / M\n" +
                "  V = n · Vm (Vm = 22.4)\n" +
                "  ω = m(в-ва) / m(р-ра)\n\n" +
                "• Кислоты: HCl, H₂SO₄, HNO₃"

            else -> subjectName to "• Шпоры и подсказки:\n\n1. Зажми урок для смены кабинета.\n2. Тап по часам сверху — Стелс-режим."
        }
    }

    fun getLiveStatus(context: Context): LiveStatus {
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        val nowSeconds = h * 3600 + m * 60 + s
        val nowMinutes = h * 60 + m

        val weekendText = calcWeekendCountdown(dayOfWeek, nowMinutes)

        val todayLessons = schedule[dayOfWeek]
            ?: return LiveStatus(
                "Выходной!",
                "#64D2FF",
                "Расписание 8В",
                "Уроков сегодня нет",
                "Пн: Разговор о важном",
                100,
                "Сегодня выходной день",
                weekendText
            )

        val firstBell = bells.first()
        val lastBell = bells.last()

        if (nowSeconds < firstBell.startTotal * 60) {
            val diffSec = firstBell.startTotal * 60 - nowSeconds
            val diffMin = (diffSec + 59) / 60
            val firstLesson = todayLessons.first()
            val room1 = getRoom(context, dayOfWeek, 1, firstLesson.room)
            val room2 = getRoom(context, dayOfWeek, 2, todayLessons[1].room)
            val timerStr = if (diffSec < 300) {
                String.format(Locale.getDefault(), "До урока: %02d:%02d", diffSec / 60, diffSec % 60)
            } else {
                "До уроков: $diffMin мин"
            }
            return LiveStatus(
                timerStr,
                "#FFD60A",
                "Скоро 1 урок (8:30)",
                "${firstLesson.name} ($room1)",
                "Дальше: ${todayLessons[1].name} ($room2)",
                0,
                "Впереди 7 уроков (до 15:25)",
                weekendText
            )
        }

        if (nowSeconds >= lastBell.endTotal * 60) {
            return LiveStatus(
                "Уроки кончились!",
                "#30D158",
                "Свободен (7 из 7 позади)",
                "Можно идти домой",
                "Хорошего отдыха!",
                100,
                "Учебный день завершен!",
                weekendText
            )
        }

        val totalSchoolRemainingMin = (lastBell.endTotal * 60 - nowSeconds + 59) / 60
        val remH = totalSchoolRemainingMin / 60
        val remM = totalSchoolRemainingMin % 60
        val todayFooter = if (remH > 0) {
            "До конца дня (15:25): $remH ч $remM мин"
        } else {
            "До конца дня (15:25): $remM мин"
        }

        for (i in bells.indices) {
            val bell = bells[i]
            val lesson = todayLessons[i]
            val startSec = bell.startTotal * 60
            val endSec = bell.endTotal * 60

            if (nowSeconds in startSec until endSec) {
                val leftSec = endSec - nowSeconds
                val leftMin = (leftSec + 59) / 60
                val totalLessonSec = endSec - startSec
                val passedSec = nowSeconds - startSec
                val pct = ((passedSec * 100) / totalLessonSec).coerceIn(0, 100)

                val roomCur = getRoom(context, dayOfWeek, lesson.num, lesson.room)
                val nextStr = if (i + 1 < todayLessons.size) {
                    val nextLesson = todayLessons[i + 1]
                    val roomNext = getRoom(context, dayOfWeek, nextLesson.num, nextLesson.room)
                    "Дальше: ${nextLesson.name} ($roomNext)"
                } else {
                    "Это последний урок!"
                }

                val timerStr = if (leftSec <= 300) {
                    String.format(Locale.getDefault(), "Звонок: %02d:%02d", leftSec / 60, leftSec % 60)
                } else {
                    "Звонок через $leftMin мин"
                }

                return LiveStatus(
                    timerStr,
                    "#30D158",
                    "${bell.num} урок (${bell.timeStr}) • $pct%",
                    "${lesson.name} • каб. $roomCur",
                    nextStr,
                    pct,
                    todayFooter,
                    weekendText
                )
            }

            if (i + 1 < bells.size) {
                val nextBell = bells[i + 1]
                val nextStartSec = nextBell.startTotal * 60
                if (nowSeconds in endSec until nextStartSec) {
                    val leftSec = nextStartSec - nowSeconds
                    val leftMin = (leftSec + 59) / 60
                    val totalBreakSec = nextStartSec - endSec
                    val passedBreakSec = nowSeconds - endSec
                    val pct = ((passedBreakSec * 100) / totalBreakSec).coerceIn(0, 100)

                    val nextLesson = todayLessons[i + 1]
                    val roomNext = getRoom(context, dayOfWeek, nextLesson.num, nextLesson.room)

                    val timerStr = if (leftSec <= 180) {
                        String.format(Locale.getDefault(), "Урок через %02d:%02d", leftSec / 60, leftSec % 60)
                    } else {
                        "Перемена! $leftMin мин"
                    }

                    return LiveStatus(
                        timerStr,
                        "#FF9F0A",
                        "Иди на ${nextBell.num} урок • $pct%",
                        "${nextLesson.name} • каб. $roomNext",
                        "Звонок в ${String.format(Locale.getDefault(), "%d:%02d", nextBell.startH, nextBell.startM)}",
                        pct,
                        todayFooter,
                        weekendText
                    )
                }
            }
        }

        return LiveStatus("Расписание 8В", "#30D158", "", "", "", 0, "", weekendText)
    }

    private fun calcWeekendCountdown(dayOfWeek: Int, nowMinutes: Int): String {
        val fridayEnd = 15 * 60 + 25
        val dayIndex = when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            else -> return "Сейчас выходные — отдыхай!"
        }
        val minutesUntilFridayEnd = (4 - dayIndex) * 24 * 60 + (fridayEnd - nowMinutes)
        if (minutesUntilFridayEnd <= 0) return "Учебная неделя позади! Выходные!"
        val d = minutesUntilFridayEnd / (24 * 60)
        val h = (minutesUntilFridayEnd % (24 * 60)) / 60
        val m = minutesUntilFridayEnd % 60
        return if (d > 0) "До выходных: $d дн $h ч" else "До выходных: $h ч $m мин"
    }
}

class MainActivity : Activity() {

    private lateinit var rootFrame: FrameLayout
    private lateinit var mainScrollView: ScrollView
    private lateinit var overlayScrollView: ScrollView
    private lateinit var overlayContainer: LinearLayout

    private lateinit var tvClock: TextView
    private lateinit var tvTimerBig: TextView
    private lateinit var tvCurrentLabel: TextView
    private lateinit var tvCurrentSubject: TextView
    private lateinit var progressFillView: View
    private lateinit var progressEmptyView: View
    private lateinit var tvNextSubject: TextView
    private lateinit var lessonsListContainer: LinearLayout
    private lateinit var dayButtonsContainer: LinearLayout
    private lateinit var tvFooterToday: TextView
    private lateinit var tvFooterWeekend: TextView

    private var selectedDay: Int = Calendar.MONDAY
    private var stealthMode: Boolean = false
    private var lastRenderedMinute: Int = -1

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateLiveStatus()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        selectedDay = if (ScheduleRepo.schedule.containsKey(today)) today else Calendar.MONDAY

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        mainScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(26), dp(18), dp(52))
        }

        tvClock = TextView(this).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                stealthMode = !stealthMode
                rootFrame.alpha = if (stealthMode) 0.32f else 1.0f
                updateLiveStatus()
            }
        }
        root.addView(tvClock)

        tvTimerBig = TextView(this).apply {
            setTextColor(Color.parseColor("#30D158"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(8))
        }
        root.addView(tvTimerBig)

        val liveCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg("#1C1C1E", dp(16), "#2C2C2E", dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        tvCurrentLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#98989D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }
        liveCard.addView(tvCurrentLabel)

        tvCurrentSubject = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(6))
        }
        liveCard.addView(tvCurrentSubject)

        val progressTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
            background = roundedBg("#2C2C2E", dp(4), "#00000000", 0)
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(5)
            )
            params.setMargins(dp(8), 0, dp(8), dp(6))
            layoutParams = params
        }

        progressFillView = View(this).apply {
            background = roundedBg("#30D158", dp(4), "#00000000", 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f)
        }
        progressEmptyView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100f)
        }
        progressTrack.addView(progressFillView)
        progressTrack.addView(progressEmptyView)
        liveCard.addView(progressTrack)

        tvNextSubject = TextView(this).apply {
            setTextColor(Color.parseColor("#64D2FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
        }
        liveCard.addView(tvNextSubject)

        root.addView(liveCard)

        dayButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(dayButtonsContainer)

        lessonsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(lessonsListContainer)

        val footerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg("#101012", dp(14), "#1F1F24", dp(1))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, dp(12), 0, 0)
            layoutParams = params
        }

        tvFooterToday = TextView(this).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }
        footerCard.addView(tvFooterToday)

        tvFooterWeekend = TextView(this).apply {
            setTextColor(Color.parseColor("#64D2FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        footerCard.addView(tvFooterWeekend)

        val tvHint = TextView(this).apply {
            text = "Тап по уроку — шпора • Зажать — каб.\nТап по часам сверху — Стелс-режим"
            setTextColor(Color.parseColor("#55555A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            gravity = Gravity.CENTER
        }
  

package com.watch.schedule

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
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
    val nextText: String
)

object ScheduleRepo {
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

    fun getLiveStatus(): LiveStatus {
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val todayLessons = schedule[dayOfWeek]
            ?: return LiveStatus(
                "Выходной!",
                "#64D2FF",
                "Расписание 8В",
                "Уроков сегодня нет",
                "Пн: Разговор о важном (315)"
            )

        val firstBell = bells.first()
        val lastBell = bells.last()

        if (nowMinutes < firstBell.startTotal) {
            val diffMin = firstBell.startTotal - nowMinutes
            val firstLesson = todayLessons.first()
            return LiveStatus(
                "До уроков: $diffMin мин",
                "#FFD60A",
                "Скоро 1 урок (8:30)",
                "${firstLesson.name} (${firstLesson.room})",
                "Дальше: ${todayLessons[1].name} (${todayLessons[1].room})"
            )
        }

        if (nowMinutes >= lastBell.endTotal) {
            return LiveStatus(
                "Уроки кончились!",
                "#30D158",
                "Свободен",
                "Можно идти домой",
                "Хорошего отдыха!"
            )
        }

        for (i in bells.indices) {
            val bell = bells[i]
            val lesson = todayLessons[i]

            if (nowMinutes >= bell.startTotal && nowMinutes < bell.endTotal) {
                val leftMin = bell.endTotal - nowMinutes
                val nextStr = if (i + 1 < todayLessons.size) {
                    val nextLesson = todayLessons[i + 1]
                    "Дальше: ${nextLesson.name} (${nextLesson.room})"
                } else {
                    "Это последний урок!"
                }
                return LiveStatus(
                    "Звонок через $leftMin мин",
                    "#30D158",
                    "Сейчас ${bell.num} урок (${bell.timeStr})",
                    "${lesson.name} • каб. ${lesson.room}",
                    nextStr
                )
            }

            if (i + 1 < bells.size) {
                val nextBell = bells[i + 1]
                if (nowMinutes >= bell.endTotal && nowMinutes < nextBell.startTotal) {
                    val leftMin = nextBell.startTotal - nowMinutes
                    val nextLesson = todayLessons[i + 1]
                    return LiveStatus(
                        "Перемена! $leftMin мин",
                        "#FF9F0A",
                        "Иди на ${nextBell.num} урок",
                        "${nextLesson.name} • каб. ${nextLesson.room}",
                        "Звонок в ${String.format(Locale.getDefault(), "%d:%02d", nextBell.startH, nextBell.startM)}"
                    )
                }
            }
        }

        return LiveStatus("Расписание 8В", "#30D158", "", "", "")
    }
}

class MainActivity : Activity() {

    private lateinit var tvClock: TextView
    private lateinit var tvTimerBig: TextView
    private lateinit var tvCurrentLabel: TextView
    private lateinit var tvCurrentSubject: TextView
    private lateinit var tvNextSubject: TextView
    private lateinit var lessonsListContainer: LinearLayout
    private lateinit var dayButtonsContainer: LinearLayout

    private var selectedDay: Int = Calendar.MONDAY
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

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(28), dp(18), dp(48))
        }

        tvClock = TextView(this).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        root.addView(tvClock)

        tvTimerBig = TextView(this).apply {
            setTextColor(Color.parseColor("#30D158"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
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

        scrollView.addView(root)
        setContentView(scrollView)
        scrollView.requestFocus()

        renderDayButtons()
        renderScheduleList()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
        TileService.getUpdater(this).requestUpdate(ScheduleTileService::class.java)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun updateLiveStatus() {
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)

        val dayNames = mapOf(
            Calendar.MONDAY to "Пн",
            Calendar.TUESDAY to "Вт",
            Calendar.WEDNESDAY to "Ср",
            Calendar.THURSDAY to "Чт",
            Calendar.FRIDAY to "Пт",
            Calendar.SATURDAY to "Сб",
            Calendar.SUNDAY to "Вс"
        )
        tvClock.text = String.format(Locale.getDefault(), "%s • %02d:%02d:%02d", dayNames[dayOfWeek] ?: "", h, m, s)

        val status = ScheduleRepo.getLiveStatus()
        tvTimerBig.setTextColor(Color.parseColor(status.timerColorHex))
        tvTimerBig.text = status.timerText
        tvCurrentLabel.text = status.labelText
        tvCurrentSubject.text = status.currentText
        tvNextSubject.text = status.nextText
    }

    private fun renderDayButtons() {
        dayButtonsContainer.removeAllViews()
        val days = listOf(
            Calendar.MONDAY to "Пн",
            Calendar.TUESDAY to "Вт",
            Calendar.WEDNESDAY to "Ср",
            Calendar.THURSDAY to "Чт",
            Calendar.FRIDAY to "Пт"
        )

        for ((dayConst, label) in days) {
            val isSelected = dayConst == selectedDay
            val btn = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(if (isSelected) Color.BLACK else Color.WHITE)
                gravity = Gravity.CENTER
                background = roundedBg(
                    if (isSelected) "#30D158" else "#2C2C2E",
                    dp(12),
                    "#00000000",
                    0
                )
                setPadding(dp(8), dp(6), dp(8), dp(6))
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(dp(3), 0, dp(3), 0)
                layoutParams = params
                setOnClickListener {
                    selectedDay = dayConst
                    renderDayButtons()
                    renderScheduleList()
                }
            }
            dayButtonsContainer.addView(btn)
        }
    }

    private fun renderScheduleList() {
        lessonsListContainer.removeAllViews()
        val dayLessons = ScheduleRepo.schedule[selectedDay] ?: return
        val now = Calendar.getInstance()
        val isToday = now.get(Calendar.DAY_OF_WEEK) == selectedDay
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (i in dayLessons.indices) {
            val lesson = dayLessons[i]
            val bell = ScheduleRepo.bells[i]
            val isCurrent = isToday && nowMinutes >= bell.startTotal && nowMinutes < bell.endTotal

            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(
                    if (isCurrent) "#11321B" else "#141416",
                    dp(12),
                    if (isCurrent) "#30D158" else "#262629",
                    dp(1)
                )
                setPadding(dp(12), dp(8), dp(12), dp(8))
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, dp(4), 0, dp(4))
                layoutParams = params
            }

            val topRow = TextView(this).apply {
                text = "${lesson.num} урок • ${bell.timeStr}"
                setTextColor(if (isCurrent) Color.parseColor("#30D158") else Color.parseColor("#8E8E93"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
            itemCard.addView(topRow)

            val bottomRow = TextView(this).apply {
                text = "${lesson.name}  (каб. ${lesson.room})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            itemCard.addView(bottomRow)

            lessonsListContainer.addView(itemCard)
        }
    }

    private fun roundedBg(fillHex: String, radiusPx: Int, strokeHex: String, strokePx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(Color.parseColor(fillHex))
            if (strokePx > 0) {
                setStroke(strokePx, Color.parseColor(strokeHex))
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}

class ScheduleTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val status = ScheduleRepo.getLiveStatus()

        val refreshClickable = ModifiersBuilders.Clickable.Builder()
            .setId("refresh_tile")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val openAppClickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        val timerElement = LayoutElementBuilders.Text.Builder()
            .setText(status.timerText)
            .setMaxLines(1)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(refreshClickable)
                    .build()
            )
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(17f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setColor(ColorBuilders.argb(Color.parseColor(status.timerColorHex)))
                    .build()
            )
            .build()

        val cardColumn = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openAppClickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(Color.parseColor("#1C1C1E")))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(DimensionBuilders.dp(16f))
                                    .build()
                            )
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(DimensionBuilders.dp(10f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(status.labelText)
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(11f))
                            .setColor(ColorBuilders.argb(Color.parseColor("#98989D")))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(3f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(status.currentText)
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(15f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(ColorBuilders.argb(Color.WHITE))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementB

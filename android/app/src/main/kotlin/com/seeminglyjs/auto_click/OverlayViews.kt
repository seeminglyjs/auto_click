package com.seeminglyjs.auto_click

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val ACCENT = 0xFF26A69A.toInt()
const val HIGHLIGHT = 0xFFFF7043.toInt()
const val PANEL_BG = 0xF2202428.toInt()
const val CHIP_BG = 0xFF33393E.toInt()
const val TEXT_DIM = 0xFFB0BEC5.toInt()
const val GREEN = 0xFF2E7D32.toInt()
const val RED = 0xFFC62828.toInt()

fun Context.dp(v: Float) = v * resources.displayMetrics.density
fun Context.dpi(v: Float) = dp(v).toInt()

fun typeLabel(type: String) = when (type) {
    "tap" -> "탭"
    "doubleTap" -> "더블 탭"
    "longPress" -> "길게 누르기"
    "areaTap" -> "영역 랜덤 탭"
    "swipe" -> "스와이프"
    "wait" -> "대기"
    else -> type
}

fun drawArrow(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float, paint: Paint, len: Float) {
    val angle = atan2(by - ay, bx - ax)
    val p = Path().apply {
        moveTo(bx, by)
        lineTo(bx - len * cos(angle - 0.45f), by - len * sin(angle - 0.45f))
        lineTo(bx - len * cos(angle + 0.45f), by - len * sin(angle + 0.45f))
        close()
    }
    canvas.drawPath(p, Paint(paint).apply { style = Paint.Style.FILL })
}

// ─────────────────────────────────────────────────────────────────────────────
// 아이콘 (Canvas 로 직접 그림 — 리소스 없이 어디서나 선명하게)
// ─────────────────────────────────────────────────────────────────────────────

enum class Glyph { PLAY, STOP, ADD, SETTINGS, SAVE, EYE, EYE_OFF, HOME, CLOSE, UNDO, TAP, LONG, AREA, SWIPE, WAIT, BACK, GRIP, HELP }

/** 원형 배경 + 흰색 아이콘. */
class IconCircle(context: Context, var glyph: Glyph, var bg: Int) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        p.style = Paint.Style.FILL
        p.color = bg
        if (bg != Color.TRANSPARENT) canvas.drawCircle(cx, cy, r, p)
        p.color = Color.WHITE
        p.strokeWidth = r * 0.12f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        drawGlyph(canvas, glyph, cx, cy, r * 0.5f, p)
    }

    companion object {
        fun drawGlyph(c: Canvas, g: Glyph, cx: Float, cy: Float, s: Float, p: Paint) {
            val stroke = Paint(p).apply { style = Paint.Style.STROKE }
            val fill = Paint(p).apply { style = Paint.Style.FILL }
            when (g) {
                Glyph.PLAY -> c.drawPath(Path().apply {
                    moveTo(cx - s * 0.55f, cy - s * 0.8f); lineTo(cx + s * 0.85f, cy)
                    lineTo(cx - s * 0.55f, cy + s * 0.8f); close()
                }, fill)
                Glyph.STOP -> c.drawRoundRect(RectF(cx - s * 0.65f, cy - s * 0.65f, cx + s * 0.65f, cy + s * 0.65f), s * 0.15f, s * 0.15f, fill)
                Glyph.ADD -> {
                    c.drawLine(cx - s * 0.8f, cy, cx + s * 0.8f, cy, stroke)
                    c.drawLine(cx, cy - s * 0.8f, cx, cy + s * 0.8f, stroke)
                }
                Glyph.SETTINGS -> {
                    c.drawCircle(cx, cy, s * 0.55f, stroke)
                    c.drawCircle(cx, cy, s * 0.2f, fill)
                    for (i in 0 until 8) {
                        c.save(); c.rotate(i * 45f, cx, cy)
                        c.drawRect(cx - s * 0.14f, cy - s * 0.95f, cx + s * 0.14f, cy - s * 0.6f, fill)
                        c.restore()
                    }
                }
                Glyph.SAVE -> {
                    c.drawRoundRect(RectF(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f), s * 0.12f, s * 0.12f, stroke)
                    c.drawRect(cx - s * 0.45f, cy - s * 0.8f, cx + s * 0.35f, cy - s * 0.3f, fill)
                    c.drawCircle(cx, cy + s * 0.3f, s * 0.22f, fill)
                }
                Glyph.EYE, Glyph.EYE_OFF -> {
                    c.drawPath(Path().apply {
                        moveTo(cx - s * 0.95f, cy)
                        quadTo(cx, cy - s * 1.0f, cx + s * 0.95f, cy)
                        quadTo(cx, cy + s * 1.0f, cx - s * 0.95f, cy)
                        close()
                    }, stroke)
                    c.drawCircle(cx, cy, s * 0.25f, fill)
                    if (g == Glyph.EYE_OFF) c.drawLine(cx - s * 0.8f, cy + s * 0.8f, cx + s * 0.8f, cy - s * 0.8f, stroke)
                }
                Glyph.HOME -> c.drawPath(Path().apply {
                    moveTo(cx, cy - s * 0.85f); lineTo(cx + s * 0.85f, cy - s * 0.05f)
                    lineTo(cx + s * 0.6f, cy - s * 0.05f); lineTo(cx + s * 0.6f, cy + s * 0.8f)
                    lineTo(cx - s * 0.6f, cy + s * 0.8f); lineTo(cx - s * 0.6f, cy - s * 0.05f)
                    lineTo(cx - s * 0.85f, cy - s * 0.05f); close()
                }, fill)
                Glyph.CLOSE -> {
                    c.drawLine(cx - s * 0.65f, cy - s * 0.65f, cx + s * 0.65f, cy + s * 0.65f, stroke)
                    c.drawLine(cx + s * 0.65f, cy - s * 0.65f, cx - s * 0.65f, cy + s * 0.65f, stroke)
                }
                Glyph.UNDO -> {
                    c.drawArc(RectF(cx - s * 0.7f, cy - s * 0.55f, cx + s * 0.8f, cy + s * 0.85f), 200f, 250f, false, stroke)
                    c.drawPath(Path().apply {
                        moveTo(cx - s * 0.95f, cy - s * 0.55f); lineTo(cx - s * 0.35f, cy - s * 0.65f)
                        lineTo(cx - s * 0.75f, cy + s * 0.05f); close()
                    }, fill)
                }
                Glyph.TAP -> {
                    c.drawCircle(cx, cy, s * 0.3f, fill)
                    c.drawCircle(cx, cy, s * 0.75f, stroke)
                }
                Glyph.LONG -> {
                    c.drawCircle(cx, cy, s * 0.25f, fill)
                    c.drawCircle(cx, cy, s * 0.55f, stroke)
                    c.drawCircle(cx, cy, s * 0.9f, Paint(stroke).apply { alpha = 140 })
                }
                Glyph.AREA -> {
                    c.drawRect(cx - s * 0.8f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.6f,
                        Paint(stroke).apply { pathEffect = DashPathEffect(floatArrayOf(s * 0.3f, s * 0.2f), 0f) })
                    c.drawCircle(cx + s * 0.2f, cy - s * 0.1f, s * 0.18f, fill)
                }
                Glyph.SWIPE -> {
                    c.drawLine(cx - s * 0.8f, cy + s * 0.5f, cx + s * 0.6f, cy - s * 0.4f, stroke)
                    drawArrow(c, cx - s * 0.8f, cy + s * 0.5f, cx + s * 0.8f, cy - s * 0.55f, fill, s * 0.6f)
                }
                Glyph.WAIT -> {
                    c.drawLine(cx - s * 0.6f, cy - s * 0.85f, cx + s * 0.6f, cy - s * 0.85f, stroke)
                    c.drawLine(cx - s * 0.6f, cy + s * 0.85f, cx + s * 0.6f, cy + s * 0.85f, stroke)
                    c.drawPath(Path().apply {
                        moveTo(cx - s * 0.5f, cy - s * 0.85f); lineTo(cx + s * 0.5f, cy - s * 0.85f)
                        lineTo(cx - s * 0.5f, cy + s * 0.85f); lineTo(cx + s * 0.5f, cy + s * 0.85f); close()
                    }, stroke)
                }
                Glyph.BACK -> c.drawPath(Path().apply {
                    moveTo(cx + s * 0.3f, cy - s * 0.7f); lineTo(cx - s * 0.4f, cy); lineTo(cx + s * 0.3f, cy + s * 0.7f)
                }, stroke)
                Glyph.GRIP -> for (ix in -1..1) for (iy in 0..1) {
                    c.drawCircle(cx + ix * s * 0.5f, cy + (iy - 0.5f) * s * 0.5f, s * 0.12f, fill)
                }
                Glyph.HELP -> {
                    val t = Paint(fill).apply { textAlign = Paint.Align.CENTER; textSize = s * 1.7f; isFakeBoldText = true }
                    c.drawText("?", cx, cy + s * 0.6f, t)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 플로팅 컨트롤 바
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class PanelView(context: Context, private val actions: Actions) : LinearLayout(context) {

    interface Actions {
        fun onToggleRun()
        fun onCapture(mode: CaptureView.Mode)
        fun onAddWait()
        fun onUndo()
        fun onSettings()
        fun onSave()
        fun onToggleMarkers()
        fun onOpenApp()
        fun onClose()
        fun onDrag(dx: Int, dy: Int)
    }

    private val grip = IconCircle(context, Glyph.GRIP, Color.TRANSPARENT)
    private val mainGroup = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private val addGroup = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private val runIcon: IconCircle
    private val runLabel: TextView
    private val eyeIcon: IconCircle
    private val status: TextView
    private var collapsed = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = GradientDrawable().apply {
            cornerRadius = context.dp(28f)
            setColor(PANEL_BG)
        }
        setPadding(context.dpi(6f), context.dpi(4f), context.dpi(6f), context.dpi(8f))

        addView(grip, LayoutParams(context.dpi(44f), context.dpi(26f)))

        val run = button(Glyph.PLAY, GREEN, "실행") { actions.onToggleRun() }
        runIcon = run.getChildAt(0) as IconCircle
        runLabel = run.getChildAt(1) as TextView
        addView(run)

        mainGroup.addView(button(Glyph.ADD, ACCENT, "추가") { showAdd(true) })
        mainGroup.addView(button(Glyph.SETTINGS, CHIP_BG, "설정") { actions.onSettings() })
        mainGroup.addView(button(Glyph.SAVE, CHIP_BG, "저장") { actions.onSave() })
        val eye = button(Glyph.EYE, CHIP_BG, "마커") { actions.onToggleMarkers() }
        eyeIcon = eye.getChildAt(0) as IconCircle
        mainGroup.addView(eye)
        mainGroup.addView(button(Glyph.HOME, CHIP_BG, "앱") { actions.onOpenApp() })
        mainGroup.addView(button(Glyph.CLOSE, 0xFF5D2B2B.toInt(), "닫기") { actions.onClose() })
        addView(mainGroup)

        addGroup.addView(button(Glyph.TAP, ACCENT, "탭") { pick(CaptureView.Mode.TAP) })
        addGroup.addView(button(Glyph.LONG, ACCENT, "길게") { pick(CaptureView.Mode.LONG_PRESS) })
        addGroup.addView(button(Glyph.AREA, ACCENT, "영역") { pick(CaptureView.Mode.AREA) })
        addGroup.addView(button(Glyph.SWIPE, ACCENT, "스와이프") { pick(CaptureView.Mode.SWIPE) })
        addGroup.addView(button(Glyph.WAIT, ACCENT, "대기") { showAdd(false); actions.onAddWait() })
        addGroup.addView(button(Glyph.UNDO, CHIP_BG, "되돌리기") { actions.onUndo() })
        addGroup.addView(button(Glyph.BACK, CHIP_BG, "뒤로") { showAdd(false) })
        addGroup.visibility = GONE
        addView(addGroup)

        status = TextView(context).apply {
            setTextColor(TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            maxWidth = context.dpi(64f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, context.dpi(4f), 0, 0)
        }
        addView(status)

        // 손잡이: 드래그 = 이동, 탭 = 접기/펴기
        var lastX = 0f
        var lastY = 0f
        var moved = false
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    if (moved || abs(dx) + abs(dy) > context.dp(6f)) {
                        moved = true
                        actions.onDrag(dx.toInt(), dy.toInt())
                        lastX = e.rawX; lastY = e.rawY
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) setCollapsed(!collapsed)
            }
            true
        }
    }

    private fun pick(mode: CaptureView.Mode) {
        showAdd(false)
        actions.onCapture(mode)
    }

    fun showAdd(show: Boolean) {
        if (show) setCollapsed(false)
        addGroup.visibility = if (show) VISIBLE else GONE
        mainGroup.visibility = if (show || collapsed) GONE else VISIBLE
    }

    private fun setCollapsed(c: Boolean) {
        collapsed = c
        addGroup.visibility = GONE
        mainGroup.visibility = if (c) GONE else VISIBLE
        status.visibility = if (c) GONE else VISIBLE
    }

    fun update(running: Boolean, statusText: String, markersOn: Boolean) {
        runIcon.glyph = if (running) Glyph.STOP else Glyph.PLAY
        runIcon.bg = if (running) RED else GREEN
        runIcon.invalidate()
        runLabel.text = if (running) "정지" else "실행"
        eyeIcon.glyph = if (markersOn) Glyph.EYE else Glyph.EYE_OFF
        eyeIcon.invalidate()
        status.text = statusText
    }

    private fun button(glyph: Glyph, color: Int, text: String, onClick: () -> Unit): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, context.dpi(4f), 0, context.dpi(2f))
            isClickable = true
            setOnClickListener { onClick() }
        }
        col.addView(IconCircle(context, glyph, color), LayoutParams(context.dpi(42f), context.dpi(42f)))
        col.addView(TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        })
        return col
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 단계 위치 표시 (터치 통과 — 그리기 전용)
// ─────────────────────────────────────────────────────────────────────────────

class MarkerView(context: Context, private val screenSize: () -> Pair<Int, Int>) : View(context) {
    var steps: List<Step> = emptyList()
        set(value) { field = value; invalidate() }
    var highlight: Int = -1
        set(value) { field = value; invalidate() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2.5f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = context.dp(12f)
        isFakeBoldText = true
    }
    private val loc = IntArray(2)

    private fun number(canvas: Canvas, x: Float, y: Float, n: Int, color: Int) {
        badge.color = color
        val r = context.dp(11f)
        canvas.drawCircle(x, y, r, badge)
        canvas.drawText("$n", x, y + text.textSize / 3, text)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (w, h) = screenSize()
        getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        val r = context.dp(22f)
        steps.forEachIndexed { i, s ->
            if (s.type == "wait") return@forEachIndexed
            val c = if (i == highlight) HIGHLIGHT else ACCENT
            stroke.color = c
            fill.color = (c and 0x00FFFFFF) or 0x33000000
            val ax = (s.x * w).toFloat()
            val ay = (s.y * h).toFloat()
            val bx = (s.x2 * w).toFloat()
            val by = (s.y2 * h).toFloat()
            when (s.type) {
                "areaTap" -> {
                    val rect = RectF(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
                    canvas.drawRect(rect, fill)
                    canvas.drawRect(rect, stroke)
                    // 크기 조절 손잡이 표시
                    canvas.drawCircle(rect.right, rect.bottom, context.dp(7f), Paint(stroke).apply { style = Paint.Style.FILL })
                    number(canvas, rect.centerX(), rect.centerY(), i + 1, c)
                }
                "swipe" -> {
                    canvas.drawLine(ax, ay, bx, by, stroke)
                    drawArrow(canvas, ax, ay, bx, by, stroke, context.dp(14f))
                    canvas.drawCircle(ax, ay, r * 0.7f, fill)
                    canvas.drawCircle(ax, ay, r * 0.7f, stroke)
                    number(canvas, ax, ay, i + 1, c)
                }
                else -> {
                    canvas.drawCircle(ax, ay, r, fill)
                    canvas.drawCircle(ax, ay, r, stroke)
                    if (s.type == "longPress") canvas.drawCircle(ax, ay, r * 1.3f, stroke)
                    if (s.type == "doubleTap") canvas.drawCircle(ax, ay, r * 0.65f, stroke)
                    number(canvas, ax, ay, i + 1, c)
                }
            }
        }
        canvas.restore()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 마커 손잡이 — 정지 상태에서만 표시. 탭 = 단계 설정, 드래그 = 위치 이동
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HandleView(
    context: Context,
    val stepIndex: Int,
    val role: Role,
    private val listener: Listener,
) : View(context) {

    enum class Role { POINT, AREA_MOVE, AREA_RESIZE, SWIPE_START, SWIPE_END }

    interface Listener {
        fun onHandleTap(handle: HandleView)
        fun onHandleDrag(handle: HandleView, dxPx: Float, dyPx: Float)
        fun onHandleDragEnd(handle: HandleView)
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val slop = context.dp(8f)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1.5f)
        color = 0x66FFFFFF
    }

    override fun onDraw(canvas: Canvas) {
        // 잡을 수 있다는 걸 알려주는 옅은 테두리
        if (role == Role.AREA_RESIZE) {
            canvas.drawCircle(width / 2f, height / 2f, width / 2.6f, ring)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX; lastY = e.rawY; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - lastX
                val dy = e.rawY - lastY
                if (dragging || hypot(dx, dy) > slop) {
                    dragging = true
                    listener.onHandleDrag(this, dx, dy)
                    lastX = e.rawX; lastY = e.rawY
                }
            }
            MotionEvent.ACTION_UP -> if (dragging) listener.onHandleDragEnd(this) else listener.onHandleTap(this)
            MotionEvent.ACTION_CANCEL -> if (dragging) listener.onHandleDragEnd(this)
        }
        return true
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 위치 기록 레이어 (전체 화면, 터치를 가로챔)
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class CaptureView(
    context: Context,
    private val mode: Mode,
    private val screenSize: () -> Pair<Int, Int>,
    private val onResult: (JSONObject?) -> Unit,
) : View(context) {

    enum class Mode(val hint: String, val sub: String) {
        TAP("누를 위치를 탭하세요", "이 위치를 반복해서 누릅니다"),
        LONG_PRESS("누르고 있다가 떼세요", "누른 시간만큼 길게 누릅니다"),
        AREA("드래그해서 영역을 그리세요", "영역 안 무작위 위치를 누릅니다"),
        SWIPE("밀고 싶은 방향으로 드래그하세요", "드래그 속도도 그대로 기록됩니다"),
    }

    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f
    private var downAt = 0L
    private var dragging = false
    private var cancelPressed = false
    private var retryHint = false
    private val loc = IntArray(2)
    private val cancelRect = RectF()

    private val dim = Paint().apply { color = 0x66000000 }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL_BG }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = context.dp(16f)
        isFakeBoldText = true
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_DIM
        textAlign = Paint.Align.CENTER
        textSize = context.dp(12f)
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5D2B2B.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HIGHLIGHT
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44FF7043 }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        val cx = width / 2f
        val top = context.dp(72f)
        val cw = min(width - context.dp(32f), context.dp(340f)) / 2f
        val cardRect = RectF(cx - cw, top, cx + cw, top + context.dp(118f))
        canvas.drawRoundRect(cardRect, context.dp(18f), context.dp(18f), card)
        canvas.drawText(mode.hint, cx, top + context.dp(32f), title)
        canvas.drawText(
            if (retryHint) "조금 더 크게 드래그해 주세요" else mode.sub,
            cx, top + context.dp(54f), sub,
        )
        val pw = context.dp(52f)
        cancelRect.set(cx - pw, top + context.dp(70f), cx + pw, top + context.dp(104f))
        canvas.drawRoundRect(cancelRect, context.dp(17f), context.dp(17f), pill)
        canvas.drawText("취소", cx, cancelRect.centerY() + title.textSize / 3, title)

        if (!dragging) return
        getLocationOnScreen(loc)
        val ax = downX - loc[0]
        val ay = downY - loc[1]
        val bx = curX - loc[0]
        val by = curY - loc[1]
        when (mode) {
            Mode.TAP, Mode.LONG_PRESS -> {
                canvas.drawCircle(ax, ay, context.dp(24f), fill)
                canvas.drawCircle(ax, ay, context.dp(24f), stroke)
            }
            Mode.AREA -> {
                val r = RectF(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
                canvas.drawRect(r, fill)
                canvas.drawRect(r, stroke)
            }
            Mode.SWIPE -> {
                canvas.drawLine(ax, ay, bx, by, stroke)
                drawArrow(canvas, ax, ay, bx, by, stroke, context.dp(16f))
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPressed = cancelRect.contains(e.x, e.y)
                if (cancelPressed) return true
                downX = e.rawX; downY = e.rawY
                curX = downX; curY = downY
                downAt = SystemClock.uptimeMillis()
                dragging = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                curX = e.rawX; curY = e.rawY
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (cancelPressed) {
                    if (cancelRect.contains(e.x, e.y)) onResult(null)
                    cancelPressed = false
                    return true
                }
                if (!dragging) return true
                dragging = false
                curX = e.rawX; curY = e.rawY
                val result = build(SystemClock.uptimeMillis() - downAt)
                if (result != null) onResult(result) else { retryHint = true; invalidate() }
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                cancelPressed = false
                invalidate()
            }
        }
        return true
    }

    /** 정규화 좌표로 단계 JSON 생성. 너무 작은 드래그면 null (다시 시도). */
    private fun build(heldMs: Long): JSONObject? {
        val (w, h) = screenSize()
        val nx = (downX / w).toDouble().coerceIn(0.0, 1.0)
        val ny = (downY / h).toDouble().coerceIn(0.0, 1.0)
        val ex = (curX / w).toDouble().coerceIn(0.0, 1.0)
        val ey = (curY / h).toDouble().coerceIn(0.0, 1.0)
        val dist = hypot(curX - downX, curY - downY)
        val o = JSONObject()
            .put("x", nx).put("y", ny).put("x2", nx).put("y2", ny)
            .put("delayAfterMs", 500).put("repeat", 1).put("label", "")
        return when (mode) {
            Mode.TAP -> o.put("type", "tap").put("durationMs", 40)
            Mode.LONG_PRESS -> o.put("type", "longPress")
                .put("durationMs", heldMs.coerceIn(400L, 10_000L))
            Mode.AREA -> {
                if (abs(curX - downX) < context.dp(16f) || abs(curY - downY) < context.dp(16f)) return null
                o.put("type", "areaTap").put("durationMs", 40)
                    .put("x", min(nx, ex)).put("y", min(ny, ey))
                    .put("x2", max(nx, ex)).put("y2", max(ny, ey))
            }
            Mode.SWIPE -> {
                if (dist < context.dp(24f)) return null
                o.put("type", "swipe").put("x2", ex).put("y2", ey)
                    .put("durationMs", heldMs.coerceIn(80L, 3000L))
            }
        }
    }
}

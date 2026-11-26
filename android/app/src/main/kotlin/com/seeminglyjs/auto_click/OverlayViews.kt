package com.seeminglyjs.auto_click

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
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

private const val ACCENT = 0xFF26A69A.toInt()
private const val HIGHLIGHT = 0xFFFF7043.toInt()

private fun Context.dp(v: Float) = v * resources.displayMetrics.density

/** 화살표 머리 그리기 (스와이프 표시용). */
private fun drawArrow(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float, paint: Paint, len: Float) {
    val angle = atan2(by - ay, bx - ax)
    val p = Path().apply {
        moveTo(bx, by)
        lineTo(bx - len * cos(angle - 0.45f), by - len * sin(angle - 0.45f))
        lineTo(bx - len * cos(angle + 0.45f), by - len * sin(angle + 0.45f))
        close()
    }
    val fill = Paint(paint).apply { style = Paint.Style.FILL }
    canvas.drawPath(p, fill)
}

// ─────────────────────────────────────────────────────────────────────────────
// 플로팅 컨트롤 패널
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor", "ClickableViewAccessibility", "SetTextI18n")
class PanelView(context: Context, private val actions: Actions) : LinearLayout(context) {

    interface Actions {
        fun onToggleRun()
        fun onCapture(mode: CaptureView.Mode)
        fun onUndo()
        fun onToggleMarkers()
        fun onOpenApp()
        fun onClose()
        fun onDrag(dx: Int, dy: Int)
    }

    private val handle: TextView
    private val runButton: TextView
    private val markerButton: TextView
    private val status: TextView
    private val body: LinearLayout
    private var collapsed = false

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = context.dp(16f)
            setColor(0xEE1E2324.toInt())
        }
        val pad = context.dp(8f).toInt()
        setPadding(pad, pad, pad, pad)

        handle = label("⠿ AutoClick", 12f, 0xFFB0BEC5.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, context.dp(4f).toInt())
        }
        addView(handle)

        runButton = button("▶ 실행", 0xFF2E7D32.toInt()) { actions.onToggleRun() }
        addView(runButton)

        body = LinearLayout(context).apply { orientation = VERTICAL }
        body.addView(button("＋ 탭", ACCENT) { actions.onCapture(CaptureView.Mode.TAP) })
        body.addView(button("◎ 길게", ACCENT) { actions.onCapture(CaptureView.Mode.LONG_PRESS) })
        body.addView(button("▭ 영역", ACCENT) { actions.onCapture(CaptureView.Mode.AREA) })
        body.addView(button("↕ 스와이프", ACCENT) { actions.onCapture(CaptureView.Mode.SWIPE) })
        body.addView(button("↶ 되돌리기", 0xFF455A64.toInt()) { actions.onUndo() })
        markerButton = button("◉ 마커", 0xFF455A64.toInt()) { actions.onToggleMarkers() }
        body.addView(markerButton)
        body.addView(button("⌂ 앱 열기", 0xFF455A64.toInt()) { actions.onOpenApp() })
        body.addView(button("✕ 닫기", 0xFF8E2C2C.toInt()) { actions.onClose() })
        status = label("", 11f, 0xFFB0BEC5.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, context.dp(6f).toInt(), 0, 0)
        }
        body.addView(status)
        addView(body)

        // 제목줄: 드래그 = 이동, 탭 = 접기/펴기
        var lastX = 0f
        var lastY = 0f
        var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX; lastY = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY
                    if (moved || abs(dx) + abs(dy) > context.dp(6f)) {
                        moved = true
                        actions.onDrag(dx.toInt(), dy.toInt())
                        lastX = e.rawX; lastY = e.rawY
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    collapsed = !collapsed
                    body.visibility = if (collapsed) GONE else VISIBLE
                }
            }
            true
        }
    }

    fun update(running: Boolean, stepCount: Int, statusText: String, markersOn: Boolean) {
        runButton.text = if (running) "■ 정지" else "▶ 실행"
        (runButton.background as GradientDrawable).setColor(
            if (running) 0xFFC62828.toInt() else 0xFF2E7D32.toInt(),
        )
        markerButton.text = if (markersOn) "◉ 마커 숨김" else "○ 마커 표시"
        status.text = statusText.ifEmpty { "${stepCount}단계" }
    }

    private fun label(text: String, sp: Float, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun button(text: String, color: Int, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        val h = context.dp(12f).toInt()
        val v = context.dp(8f).toInt()
        setPadding(h, v, h, v)
        minWidth = context.dp(96f).toInt()
        background = GradientDrawable().apply {
            cornerRadius = context.dp(10f)
            setColor(color)
        }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(5f).toInt()
        }
        setOnClickListener { onClick() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 단계 위치 마커 (터치 통과)
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
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = context.dp(13f)
        isFakeBoldText = true
        setShadowLayer(context.dp(3f), 0f, 0f, Color.BLACK)
    }
    private val loc = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (w, h) = screenSize()
        // 화면 좌표 → 이 뷰의 좌표로 변환 (상태바/컷아웃 오프셋 보정)
        getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        val r = context.dp(20f)
        steps.forEachIndexed { i, s ->
            if (s.type == "wait") return@forEachIndexed
            val c = if (i == highlight) HIGHLIGHT else ACCENT
            stroke.color = c
            fill.color = (c and 0x00FFFFFF) or 0x44000000
            val ax = (s.x * w).toFloat()
            val ay = (s.y * h).toFloat()
            val bx = (s.x2 * w).toFloat()
            val by = (s.y2 * h).toFloat()
            when (s.type) {
                "areaTap" -> {
                    val rect = RectF(min(ax, bx), min(ay, by), max(ax, bx), max(ay, by))
                    canvas.drawRect(rect, fill)
                    canvas.drawRect(rect, stroke)
                    canvas.drawText("${i + 1}", rect.centerX(), rect.centerY() + text.textSize / 3, text)
                }
                "swipe" -> {
                    canvas.drawLine(ax, ay, bx, by, stroke)
                    drawArrow(canvas, ax, ay, bx, by, stroke, context.dp(14f))
                    canvas.drawCircle(ax, ay, r * 0.8f, fill)
                    canvas.drawText("${i + 1}", ax, ay + text.textSize / 3, text)
                }
                else -> {
                    canvas.drawCircle(ax, ay, r, fill)
                    canvas.drawCircle(ax, ay, r, stroke)
                    if (s.type == "longPress") canvas.drawCircle(ax, ay, r * 1.35f, stroke)
                    if (s.type == "doubleTap") canvas.drawCircle(ax, ay, r * 0.6f, stroke)
                    canvas.drawText("${i + 1}", ax, ay + text.textSize / 3, text)
                }
            }
        }
        canvas.restore()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 위치 지정 레이어 (전체 화면, 터치를 가로챔)
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class CaptureView(
    context: Context,
    private val mode: Mode,
    private val screenSize: () -> Pair<Int, Int>,
    private val onResult: (JSONObject?) -> Unit,
) : View(context) {

    enum class Mode(val hint: String) {
        TAP("누를 위치를 탭하세요"),
        LONG_PRESS("길게 누를 위치를 누르고 있다가 떼세요 (누른 시간 기록)"),
        AREA("드래그해서 랜덤으로 누를 영역을 그리세요"),
        SWIPE("스와이프할 경로로 드래그하세요 (속도 기록)"),
    }

    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f
    private var downAt = 0L
    private var dragging = false
    private var cancelPressed = false
    private val loc = IntArray(2)
    private val cancelRect = RectF()

    private val dim = Paint().apply { color = 0x55000000 }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = context.dp(15f)
        setShadowLayer(context.dp(4f), 0f, 0f, Color.BLACK)
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDD8E2C2C.toInt() }
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
        val top = context.dp(110f)
        canvas.drawText(mode.hint, cx, top, hintPaint)
        val pw = context.dp(64f)
        val ph = context.dp(22f)
        cancelRect.set(cx - pw, top + context.dp(18f), cx + pw, top + context.dp(18f) + ph * 2)
        canvas.drawRoundRect(cancelRect, ph, ph, pill)
        canvas.drawText("취소", cx, cancelRect.centerY() + hintPaint.textSize / 3, hintPaint)

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
                val held = SystemClock.uptimeMillis() - downAt
                val result = build(held)
                if (result != null) onResult(result) else invalidate()
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

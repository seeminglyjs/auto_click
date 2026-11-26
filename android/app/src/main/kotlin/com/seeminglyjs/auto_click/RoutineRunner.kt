package com.seeminglyjs.auto_click

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 접근성 서비스의 dispatchGesture 로 루틴을 순서대로 실행한다.
 * 각 제스처가 끝나야(콜백) 다음 단계로 넘어가므로 순서가 보장된다.
 */
class RoutineRunner(
    private val service: AccessibilityService,
    private val routine: Routine,
    private val screenSize: () -> Pair<Int, Int>,
    private val listener: Listener,
) {
    interface Listener {
        fun onStep(index: Int, loop: Int)
        fun onFinished(reason: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    var isRunning = false
        private set
    private var index = 0
    private var repeatDone = 0
    private var loop = 0
    private var startedAt = 0L

    /** stop() 후 늦게 도착한 콜백을 무시하기 위한 세대 번호. */
    private var token = 0

    fun start() {
        if (routine.steps.isEmpty()) {
            listener.onFinished("empty")
            return
        }
        isRunning = true
        token++
        index = 0
        repeatDone = 0
        loop = 0
        startedAt = SystemClock.elapsedRealtime()
        schedule(routine.startDelayMs)
    }

    fun stop(reason: String = "stopped") {
        if (!isRunning) return
        isRunning = false
        token++
        handler.removeCallbacksAndMessages(null)
        listener.onFinished(reason)
    }

    private fun schedule(delayMs: Long) {
        val t = token
        handler.postDelayed({ if (t == token && isRunning) next() }, max(0L, delayMs))
    }

    private fun next() {
        if (routine.maxRunSeconds > 0 &&
            SystemClock.elapsedRealtime() - startedAt >= routine.maxRunSeconds * 1000L
        ) {
            stop("timeout")
            return
        }
        if (index >= routine.steps.size) {
            loop++
            if (routine.loopCount > 0 && loop >= routine.loopCount) {
                stop("completed")
                return
            }
            index = 0
            repeatDone = 0
            schedule(routine.loopIntervalMs)
            return
        }

        val step = routine.steps[index]
        listener.onStep(index, loop)
        val t = token
        val done: () -> Unit = {
            if (t == token && isRunning) {
                repeatDone++
                if (repeatDone >= step.repeat) {
                    repeatDone = 0
                    index++
                }
                schedule(jitter(step.delayAfterMs))
            }
        }

        if (step.type == "wait") {
            handler.postDelayed(done, max(1L, step.durationMs))
            return
        }
        val gesture = buildGesture(step)
        if (gesture == null) {
            done()
            return
        }
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = done()
                // 사용자가 화면을 만지면 취소될 수 있다. 그래도 루틴은 계속 진행.
                override fun onCancelled(gestureDescription: GestureDescription?) = done()
            },
            handler,
        )
        if (!accepted) handler.postDelayed(done, 100)
    }

    private fun jitter(ms: Long): Long =
        ms + if (routine.randomDelayMs > 0) Random.nextLong(0, routine.randomDelayMs + 1) else 0

    private fun buildGesture(step: Step): GestureDescription? {
        val (w, h) = screenSize()
        if (w <= 0 || h <= 0) return null
        val off = routine.randomOffsetPx
        fun jit(): Float = if (off > 0) Random.nextInt(-off, off + 1).toFloat() else 0f
        fun px(nx: Double) = (nx * w).toFloat() + jit()
        fun py(ny: Double) = (ny * h).toFloat() + jit()
        fun cx(v: Float) = v.coerceIn(0f, (w - 1).toFloat())
        fun cy(v: Float) = v.coerceIn(0f, (h - 1).toFloat())
        fun dot(x: Float, y: Float) = Path().apply { moveTo(cx(x), cy(y)) }

        val tapMs = step.durationMs.coerceIn(1L, 300L)
        val b = GestureDescription.Builder()
        when (step.type) {
            "tap" -> b.addStroke(GestureDescription.StrokeDescription(dot(px(step.x), py(step.y)), 0, tapMs))
            "doubleTap" -> {
                val x = px(step.x)
                val y = py(step.y)
                b.addStroke(GestureDescription.StrokeDescription(dot(x, y), 0, tapMs))
                b.addStroke(GestureDescription.StrokeDescription(dot(x, y), tapMs + 90, tapMs))
            }
            "longPress" -> b.addStroke(
                GestureDescription.StrokeDescription(
                    dot(px(step.x), py(step.y)), 0, step.durationMs.coerceIn(400L, 59_000L),
                ),
            )
            "areaTap" -> {
                val nx = min(step.x, step.x2) + Random.nextDouble() * kotlin.math.abs(step.x2 - step.x)
                val ny = min(step.y, step.y2) + Random.nextDouble() * kotlin.math.abs(step.y2 - step.y)
                b.addStroke(GestureDescription.StrokeDescription(dot(px(nx), py(ny)), 0, tapMs))
            }
            "swipe" -> {
                val path = Path().apply {
                    moveTo(cx(px(step.x)), cy(py(step.y)))
                    lineTo(cx(px(step.x2)), cy(py(step.y2)))
                }
                b.addStroke(
                    GestureDescription.StrokeDescription(path, 0, step.durationMs.coerceIn(50L, 59_000L)),
                )
            }
            else -> return null
        }
        return b.build()
    }
}

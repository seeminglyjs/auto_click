package com.seeminglyjs.auto_click

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * 다른 앱 위에서 터치를 주입하는 접근성 서비스.
 *
 * - 오버레이는 TYPE_ACCESSIBILITY_OVERLAY 를 쓰므로 "다른 앱 위에 표시" 권한이
 *   따로 필요 없다. 접근성 권한 하나면 된다.
 * - 화면 내용은 읽지 않는다 (canRetrieveWindowContent=false).
 */
class AutoClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickService? = null
            private set

        /** MainActivity 가 Flutter 로 이벤트를 전달하기 위해 등록한다. */
        var eventSink: ((Map<String, Any?>) -> Unit)? = null
    }

    private lateinit var wm: WindowManager
    private var panel: PanelView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var markers: MarkerView? = null
    private var capture: CaptureView? = null
    private var runner: RoutineRunner? = null
    private var markersVisible = true
    private var statusText = ""

    /** 오버레이에 올라간 루틴 원본 JSON. 패널에서 단계를 추가하면 여기에 쌓인다. */
    private var routineJson: JSONObject? = null

    /** 패널에서 수정되어 앱에 아직 반영 안 된 변경이 있는지. */
    private var dirty = false

    val isRunning get() = runner?.isRunning == true
    val isOverlayVisible get() = panel != null

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        emit(mapOf("type" to "service", "enabled" to true))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance == null) return
        hideOverlay()
        instance = null
        emit(mapOf("type" to "service", "enabled" to false))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 회전 시 전체 화면 레이어 크기 갱신
        markers?.let { safeUpdate(it, fullScreenParams(touchable = false)) }
        capture?.let { safeUpdate(it, fullScreenParams(touchable = true)) }
    }

    // ── 화면 크기 ──────────────────────────────────────────────────────────

    fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    // ── 오버레이 ───────────────────────────────────────────────────────────

    fun showOverlay(json: String) {
        routineJson = JSONObject(json)
        dirty = false
        if (markers == null) {
            markers = MarkerView(this, ::screenSize).also {
                wm.addView(it, fullScreenParams(touchable = false))
            }
        }
        if (panel == null) addPanel()
        refreshViews()
        emit(mapOf("type" to "overlay", "visible" to true))
    }

    fun hideOverlay() {
        runner?.stop("closed")
        runner = null
        removeCapture()
        panel?.let { safeRemove(it) }
        markers?.let { safeRemove(it) }
        panel = null
        markers = null
        emit(mapOf("type" to "overlay", "visible" to false))
    }

    /** 앱에서 같은 루틴을 편집하면 오버레이에도 반영. */
    fun updateRoutine(json: String) {
        val incoming = JSONObject(json)
        if (routineJson?.optString("id") != incoming.optString("id")) return
        routineJson = incoming
        dirty = false
        refreshViews()
    }

    /** 패널에서 변경된 루틴을 앱이 가져간다. 변경이 없으면 null. */
    fun takeRoutineIfDirty(): String? {
        if (!dirty) return null
        dirty = false
        return routineJson?.toString()
    }

    fun runRoutine(json: String) {
        showOverlay(json)
        startRoutine()
    }

    fun startRoutine() {
        val json = routineJson ?: return
        removeCapture()
        runner?.stop("restart")
        val routine = Routine.fromJson(json)
        if (routine.steps.isEmpty()) {
            statusText = "단계가 없습니다"
            refreshViews()
            return
        }
        runner = RoutineRunner(this, routine, ::screenSize, object : RoutineRunner.Listener {
            override fun onStep(index: Int, loop: Int) {
                markers?.highlight = index
                val total = if (routine.loopCount > 0) "/${routine.loopCount}" else ""
                statusText = "단계 ${index + 1}/${routine.steps.size} · 루프 ${loop + 1}$total"
                refreshPanel()
            }

            override fun onFinished(reason: String) {
                markers?.highlight = -1
                statusText = when (reason) {
                    "completed" -> "완료"
                    "timeout" -> "시간 제한으로 종료"
                    else -> "정지됨"
                }
                refreshPanel()
                emit(mapOf("type" to "run", "running" to false, "reason" to reason))
            }
        }).also { it.start() }
        statusText = "${routine.startDelayMs}ms 후 시작"
        refreshPanel()
        emit(mapOf("type" to "run", "running" to true))
    }

    fun stopRoutine() {
        runner?.stop("stopped")
    }

    private fun addPanel() {
        val p = PanelView(this, object : PanelView.Actions {
            override fun onToggleRun() = if (isRunning) stopRoutine() else startRoutine()
            override fun onCapture(mode: CaptureView.Mode) = beginCapture(mode)
            override fun onUndo() = undoLast()
            override fun onToggleMarkers() {
                markersVisible = !markersVisible
                refreshViews()
            }
            override fun onOpenApp() = openApp()
            override fun onClose() = hideOverlay()
            override fun onDrag(dx: Int, dy: Int) {
                val lp = panelParams ?: return
                lp.x += dx
                lp.y += dy
                panel?.let { safeUpdate(it, lp) }
            }
        })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * resources.displayMetrics.density).toInt()
            y = (160 * resources.displayMetrics.density).toInt()
        }
        wm.addView(p, lp)
        panel = p
        panelParams = lp
    }

    private fun fullScreenParams(touchable: Boolean): WindowManager.LayoutParams {
        val (w, h) = screenSize()
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // ── 위치 지정 ──────────────────────────────────────────────────────────

    private fun beginCapture(mode: CaptureView.Mode) {
        if (isRunning) stopRoutine()
        removeCapture()
        val v = CaptureView(this, mode, ::screenSize) { step ->
            removeCapture()
            if (step != null) addStep(step)
        }
        wm.addView(v, fullScreenParams(touchable = true))
        capture = v
    }

    private fun removeCapture() {
        capture?.let { safeRemove(it) }
        capture = null
    }

    private fun addStep(step: JSONObject) {
        val r = routineJson ?: return
        val arr = r.optJSONArray("steps") ?: JSONArray().also { r.put("steps", it) }
        step.put("id", "n${System.currentTimeMillis().toString(36)}${Random.nextInt(1 shl 20).toString(36)}")
        arr.put(step)
        statusText = "단계 ${arr.length()} 추가됨"
        changed()
    }

    private fun undoLast() {
        val arr = routineJson?.optJSONArray("steps") ?: return
        if (arr.length() == 0) return
        arr.remove(arr.length() - 1)
        statusText = "마지막 단계 삭제"
        changed()
    }

    private fun changed() {
        val r = routineJson ?: return
        r.put("updatedAt", System.currentTimeMillis())
        dirty = true
        refreshViews()
        emit(mapOf("type" to "routine", "json" to r.toString()))
    }

    // ── 공통 ───────────────────────────────────────────────────────────────

    private fun refreshViews() {
        val json = routineJson
        markers?.steps = if (json != null) Routine.fromJson(json).steps else emptyList()
        markers?.visibility = if (markersVisible) View.VISIBLE else View.GONE
        refreshPanel()
    }

    private fun refreshPanel() {
        val count = routineJson?.optJSONArray("steps")?.length() ?: 0
        panel?.update(isRunning, count, statusText, markersVisible)
    }

    private fun openApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        } ?: return
        startActivity(intent)
    }

    private fun safeRemove(v: View) {
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun safeUpdate(v: View, lp: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
    }

    private fun emit(event: Map<String, Any?>) {
        eventSink?.invoke(event)
    }
}

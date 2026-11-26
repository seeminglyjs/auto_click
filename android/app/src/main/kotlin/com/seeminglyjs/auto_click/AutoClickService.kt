package com.seeminglyjs.auto_click

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 다른 앱 위에서 터치를 주입하는 접근성 서비스.
 *
 * - 오버레이는 TYPE_ACCESSIBILITY_OVERLAY 라서 "다른 앱 위에 표시" 권한이 필요 없다.
 * - 화면 내용은 읽지 않는다 (canRetrieveWindowContent=false).
 * - 프리셋은 Flutter 앱과 같은 저장소(FlutterSharedPreferences)에 직접 저장하므로
 *   앱이 꺼져 있어도 패널에서 한 변경이 유지된다.
 */
class AutoClickService : AccessibilityService(), SheetHost, HandleView.Listener {

    companion object {
        @Volatile
        var instance: AutoClickService? = null
            private set

        /** MainActivity 가 Flutter 로 이벤트를 전달하기 위해 등록한다. */
        var eventSink: ((Map<String, Any?>) -> Unit)? = null

        private const val FLUTTER_PREFS = "FlutterSharedPreferences"
        private const val ROUTINES_KEY = "flutter.routines_v1"
        private const val NATIVE_PREFS = "auto_click_native"
        private const val HELP_SEEN_KEY = "panel_help_seen"
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: PanelView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var markers: MarkerView? = null
    private var capture: CaptureView? = null
    private var sheet: SheetView? = null
    private var sheetParams: WindowManager.LayoutParams? = null
    private val handles = mutableListOf<Pair<HandleView, WindowManager.LayoutParams>>()
    private var runner: RoutineRunner? = null
    private var markersVisible = true
    private var statusText = ""

    /** 모든 프리셋 (Flutter 와 같은 순서). */
    private val routines = mutableListOf<JSONObject>()
    private var currentId: String? = null

    /** 저장 전 새 작업. null 이 아니면 패널은 프리셋 없이 열린 상태. */
    private var draft: JSONObject? = null
    private var draftDirty = false

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
        markers?.let { safeUpdate(it, fullScreenParams(touchable = false)) }
        capture?.let { safeUpdate(it, fullScreenParams(touchable = true)) }
        sheet?.let { s -> sheetParams?.let { applySheetSize(s, it); safeUpdate(s, it) } }
        rebuildHandles()
    }

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

    // ── 저장소 ─────────────────────────────────────────────────────────────

    private fun loadRoutines() {
        routines.clear()
        val raw = getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE).getString(ROUTINES_KEY, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { routines += it }
        } catch (_: Exception) {
        }
    }

    private val persistRunnable = Runnable { persistNow() }

    private fun persistSoon() {
        handler.removeCallbacks(persistRunnable)
        handler.postDelayed(persistRunnable, 400)
    }

    private fun persistNow() {
        handler.removeCallbacks(persistRunnable)
        getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE).edit()
            .putString(ROUTINES_KEY, JSONArray(routines).toString())
            .apply()
    }

    private fun upsertLocal(r: JSONObject) {
        val id = r.optString("id")
        val idx = routines.indexOfFirst { it.optString("id") == id }
        if (idx >= 0) routines[idx] = r else routines.add(0, r)
    }

    private fun newId() = "n${System.currentTimeMillis().toString(36)}${Random.nextInt(1 shl 20).toString(36)}"

    private fun newRoutineJson(name: String) = JSONObject()
        .put("id", newId()).put("name", name).put("steps", JSONArray())
        .put("loopCount", 0).put("loopIntervalMs", 0).put("startDelayMs", 1500)
        .put("maxRunSeconds", 0).put("randomOffsetPx", 0).put("randomDelayMs", 0)
        .put("updatedAt", System.currentTimeMillis())

    // ── Flutter 에서 호출 ───────────────────────────────────────────────────

    /** 프리셋 없이 빈 패널(새 작업)로 연다. */
    fun showEmptyOverlay() {
        loadRoutines()
        if (panel == null || draft == null) startDraft()
        attachOverlay()
    }

    /** 지정한 프리셋으로 패널을 연다. */
    fun showOverlay(json: String) {
        loadRoutines()
        val r = JSONObject(json)
        upsertLocal(r)
        currentId = r.optString("id")
        draft = null
        draftDirty = false
        attachOverlay()
    }

    private fun startDraft() {
        draft = newRoutineJson("새 작업")
        draftDirty = false
        currentId = null
    }

    private fun attachOverlay() {
        if (markers == null) {
            markers = MarkerView(this, ::screenSize).also { wm.addView(it, fullScreenParams(touchable = false)) }
        }
        if (panel == null) addPanel()
        statusText = ""
        refreshViews(structural = true)
        emitOverlay()
        if (!getSharedPreferences(NATIVE_PREFS, Context.MODE_PRIVATE).getBoolean(HELP_SEEN_KEY, false)) {
            openSheet { it.showHelp() }
        }
    }

    fun hideOverlay() {
        runner?.stop("closed")
        runner = null
        removeCapture()
        closeSheet()
        removeHandles()
        panel?.let { safeRemove(it) }
        markers?.let { safeRemove(it) }
        if (panel != null) persistNow()
        panel = null
        markers = null
        draft = null
        draftDirty = false
        emit(mapOf("type" to "overlay", "visible" to false))
    }

    /** 앱에서 편집한 루틴을 오버레이 쪽 목록에도 반영. */
    fun updateRoutine(json: String) {
        if (panel == null) return
        val r = JSONObject(json)
        upsertLocal(r)
        if (draft == null && r.optString("id") == currentId) refreshViews(structural = true)
    }

    fun removeRoutine(id: String) {
        if (panel == null) return
        routines.removeAll { it.optString("id") == id }
        if (draft == null && id == currentId) {
            startDraft()
            refreshViews(structural = true)
            emitOverlay()
        }
    }

    fun runRoutine(json: String) {
        showOverlay(json)
        closeSheet()
        startRoutine()
    }

    fun startRoutine() {
        val json = current() ?: return
        removeCapture()
        closeSheet()
        runner?.stop("restart")
        val routine = Routine.fromJson(json)
        if (routine.steps.isEmpty()) {
            toast("먼저 ＋ 추가로 단계를 만드세요")
            return
        }
        removeHandles() // 실행 중엔 마커가 터치를 가로채지 않게
        runner = RoutineRunner(this, routine, ::screenSize, object : RoutineRunner.Listener {
            override fun onStep(index: Int, loop: Int) {
                markers?.highlight = index
                val total = if (routine.loopCount > 0) "/${routine.loopCount}" else ""
                statusText = "${index + 1}단계\n${loop + 1}$total 회"
                refreshPanel()
            }

            override fun onFinished(reason: String) {
                markers?.highlight = -1
                statusText = when (reason) {
                    "completed" -> "완료"
                    "timeout" -> "시간 종료"
                    else -> ""
                }
                refreshPanel()
                rebuildHandles()
                emit(mapOf("type" to "run", "running" to false, "reason" to reason))
            }
        }).also { it.start() }
        statusText = "${(routine.startDelayMs / 100) / 10.0}초 후\n시작"
        refreshPanel()
        emit(mapOf("type" to "run", "running" to true))
    }

    fun stopRoutine() {
        runner?.stop("stopped")
    }

    // ── 패널 ───────────────────────────────────────────────────────────────

    private fun addPanel() {
        val p = PanelView(this, object : PanelView.Actions {
            override fun onToggleRun() = if (isRunning) stopRoutine() else startRoutine()
            override fun onCapture(mode: CaptureView.Mode) = beginCapture(mode)
            override fun onAddWait() {
                addStep(
                    JSONObject().put("type", "wait").put("x", 0.5).put("y", 0.5).put("x2", 0.5).put("y2", 0.5)
                        .put("durationMs", 1000).put("delayAfterMs", 0).put("repeat", 1).put("label", ""),
                )
                toast("1초 대기를 추가했어요 · 설정에서 시간 변경")
            }
            override fun onUndo() = undoLast()
            override fun onSettings() {
                if (isRunning) stopRoutine()
                openSheet { it.showSettings() }
            }
            override fun onSave() = saveNow()
            override fun onToggleMarkers() {
                markersVisible = !markersVisible
                refreshViews(structural = true)
            }
            override fun onOpenApp() = openApp()
            override fun onClose() {
                if (draft != null && draftDirty && (draft?.optJSONArray("steps")?.length() ?: 0) > 0) {
                    if (isRunning) stopRoutine()
                    openSheet { it.showCloseConfirm() }
                } else {
                    hideOverlay()
                }
            }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpi(8f)
            y = dpi(120f)
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
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // ── 위치 기록 ──────────────────────────────────────────────────────────

    private fun beginCapture(mode: CaptureView.Mode) {
        if (isRunning) stopRoutine()
        closeSheet()
        removeCapture()
        val v = CaptureView(this, mode, ::screenSize) { step ->
            removeCapture()
            if (step != null) {
                addStep(step)
                toast("${current()?.optJSONArray("steps")?.length() ?: 0}번 단계 추가 · 마커를 눌러 주기 설정")
            }
        }
        wm.addView(v, fullScreenParams(touchable = true))
        capture = v
    }

    private fun removeCapture() {
        capture?.let { safeRemove(it) }
        capture = null
    }

    private fun steps(): JSONArray? {
        val r = current() ?: return null
        return r.optJSONArray("steps") ?: JSONArray().also { r.put("steps", it) }
    }

    private fun addStep(step: JSONObject) {
        val arr = steps() ?: return
        step.put("id", newId())
        arr.put(step)
        changed(structural = true)
    }

    private fun undoLast() {
        val arr = steps() ?: return
        if (arr.length() == 0) return toast("지울 단계가 없어요")
        arr.remove(arr.length() - 1)
        toast("마지막 단계를 지웠어요")
        changed(structural = true)
    }

    // ── 마커 손잡이 (정지 상태에서 탭 = 설정, 드래그 = 이동) ─────────────────

    private fun removeHandles() {
        handles.forEach { safeRemove(it.first) }
        handles.clear()
    }

    private fun rebuildHandles() {
        removeHandles()
        if (panel == null || !markersVisible || isRunning || capture != null || sheet != null) return
        val arr = current()?.optJSONArray("steps") ?: return
        val (w, h) = screenSize()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val x = s.optDouble("x", 0.5)
            val y = s.optDouble("y", 0.5)
            val x2 = s.optDouble("x2", 0.5)
            val y2 = s.optDouble("y2", 0.5)
            when (s.optString("type")) {
                "wait" -> {}
                "areaTap" -> {
                    addHandle(i, HandleView.Role.AREA_MOVE, (x + x2) / 2 * w, (y + y2) / 2 * h)
                    addHandle(i, HandleView.Role.AREA_RESIZE, max(x, x2) * w, max(y, y2) * h)
                }
                "swipe" -> {
                    addHandle(i, HandleView.Role.SWIPE_START, x * w, y * h)
                    addHandle(i, HandleView.Role.SWIPE_END, x2 * w, y2 * h)
                }
                else -> addHandle(i, HandleView.Role.POINT, x * w, y * h)
            }
        }
    }

    private fun addHandle(index: Int, role: HandleView.Role, cx: Double, cy: Double) {
        val size = if (role == HandleView.Role.AREA_RESIZE) dpi(36f) else dpi(52f)
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (cx - size / 2).toInt()
            y = (cy - size / 2).toInt()
        }
        val v = HandleView(this, index, role, this)
        wm.addView(v, lp)
        handles += v to lp
    }

    override fun onHandleTap(handle: HandleView) {
        openSheet { it.showStep(handle.stepIndex, fromSettings = false) }
    }

    override fun onHandleDrag(handle: HandleView, dxPx: Float, dyPx: Float) {
        val s = steps()?.optJSONObject(handle.stepIndex) ?: return
        val (w, h) = screenSize()
        val dx = dxPx / w.toDouble()
        val dy = dyPx / h.toDouble()
        fun c(v: Double) = v.coerceIn(0.0, 1.0)
        val x = s.optDouble("x", 0.5); val y = s.optDouble("y", 0.5)
        val x2 = s.optDouble("x2", 0.5); val y2 = s.optDouble("y2", 0.5)
        when (handle.role) {
            HandleView.Role.POINT -> s.put("x", c(x + dx)).put("y", c(y + dy)).put("x2", c(x + dx)).put("y2", c(y + dy))
            HandleView.Role.SWIPE_START -> s.put("x", c(x + dx)).put("y", c(y + dy))
            HandleView.Role.SWIPE_END -> s.put("x2", c(x2 + dx)).put("y2", c(y2 + dy))
            HandleView.Role.AREA_MOVE -> {
                val ddx = dx.coerceIn(-min(x, x2), 1 - max(x, x2))
                val ddy = dy.coerceIn(-min(y, y2), 1 - max(y, y2))
                s.put("x", x + ddx).put("y", y + ddy).put("x2", x2 + ddx).put("y2", y2 + ddy)
            }
            HandleView.Role.AREA_RESIZE -> {
                val minW = dp(24f) / w
                val minH = dp(24f) / h
                s.put("x2", c(max(min(x, x2) + minW, max(x, x2) + dx)))
                s.put("y2", c(max(min(y, y2) + minH, max(y, y2) + dy)))
                s.put("x", min(x, x2)).put("y", min(y, y2))
            }
        }
        // 잡고 있는 손잡이만 따라 움직이고, 나머지는 손을 뗀 뒤 다시 배치
        handles.firstOrNull { it.first === handle }?.let { (v, lp) ->
            lp.x += dxPx.toInt()
            lp.y += dyPx.toInt()
            safeUpdate(v, lp)
        }
        markers?.steps = Routine.fromJson(current()!!).steps
        markers?.highlight = handle.stepIndex
    }

    override fun onHandleDragEnd(handle: HandleView) {
        markers?.highlight = -1
        changed(structural = true)
    }

    // ── 설정 시트 ──────────────────────────────────────────────────────────

    private fun openSheet(show: (SheetView) -> Unit) {
        removeCapture()
        val existing = sheet
        if (existing != null) return show(existing)
        removeHandles() // 카드 위로 손잡이가 올라오지 않게
        val v = SheetView(this, this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.35f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        applySheetSize(v, lp)
        sheet = v
        sheetParams = lp
        show(v) // 내용을 먼저 채우고
        wm.addView(v, lp) // 그다음 띄운다
    }

    private fun applySheetSize(v: SheetView, lp: WindowManager.LayoutParams) {
        val (w, h) = screenSize()
        lp.width = min(w - dpi(24f), dpi(420f))
        v.setMaxHeight((h * 0.62f).toInt())
    }

    override fun closeSheet() {
        sheet?.let { safeRemove(it) }
        val wasOpen = sheet != null
        sheet = null
        sheetParams = null
        markers?.highlight = -1
        if (wasOpen) rebuildHandles()
    }

    override fun setSheetFocusable(focusable: Boolean) {
        val v = sheet ?: return
        val lp = sheetParams ?: return
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val newFlags = if (focusable) lp.flags and f.inv() else lp.flags or f
        if (newFlags == lp.flags) return
        lp.flags = newFlags
        if (v.isAttachedToWindow) safeUpdate(v, lp)
    }

    override fun markHelpSeen() {
        getSharedPreferences(NATIVE_PREFS, Context.MODE_PRIVATE).edit().putBoolean(HELP_SEEN_KEY, true).apply()
    }

    override fun routineCount() = routines.size
    override fun routineList(): List<JSONObject> = routines
    override fun currentId(): String? = if (draft != null) null else currentId
    override fun isDraft() = draft != null
    override fun isDraftDirty() = draft != null && draftDirty
    override fun current(): JSONObject? = draft ?: routines.firstOrNull { it.optString("id") == currentId }

    override fun loadRoutine(id: String) {
        if (routines.none { it.optString("id") == id }) return
        if (isRunning) stopRoutine()
        draft = null
        draftDirty = false
        currentId = id
        statusText = ""
        refreshViews(structural = true)
        emitOverlay()
        toast("‘${current()?.optString("name")}’ 을(를) 불러왔어요")
    }

    override fun newDraft() {
        if (isRunning) stopRoutine()
        startDraft()
        statusText = ""
        refreshViews(structural = true)
        emitOverlay()
    }

    override fun commitDraft(name: String) {
        val d = draft ?: return
        d.put("name", name).put("updatedAt", System.currentTimeMillis())
        routines.add(0, d)
        currentId = d.optString("id")
        draft = null
        draftDirty = false
        persistNow()
        refreshViews(structural = false)
        emit(mapOf("type" to "routine", "json" to d.toString()))
        emitOverlay()
        toast("‘$name’ 프리셋으로 저장했어요")
    }

    override fun forceClose() = hideOverlay()

    override fun renameCurrent(name: String) {
        current()?.put("name", name) ?: return
        changed(structural = false)
    }

    override fun saveAs(name: String) {
        val src = current() ?: return
        if (draft != null) return commitDraft(name)
        val copy = JSONObject(src.toString()).put("id", newId()).put("name", name)
        copy.optJSONArray("steps")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.put("id", newId())
        }
        routines.add(0, copy)
        currentId = copy.optString("id")
        changed(structural = true)
        persistNow()
        emitOverlay()
        toast("‘$name’ (으)로 저장했어요")
    }

    override fun deleteStep(index: Int) {
        val arr = steps() ?: return
        if (index in 0 until arr.length()) arr.remove(index)
        changed(structural = true)
    }

    override fun moveStep(index: Int, delta: Int) {
        val arr = steps() ?: return
        val to = index + delta
        if (index !in 0 until arr.length() || to !in 0 until arr.length()) return
        val a = arr.getJSONObject(index)
        arr.put(index, arr.getJSONObject(to))
        arr.put(to, a)
        changed(structural = true)
    }

    override fun duplicateStep(index: Int) {
        val arr = steps() ?: return
        val src = arr.optJSONObject(index) ?: return
        val list = (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
        list.add(index + 1, JSONObject(src.toString()).put("id", newId()))
        current()?.put("steps", JSONArray(list))
        changed(structural = true)
    }

    override fun onEdited(structural: Boolean) = changed(structural)

    override fun saveNow() {
        if (draft != null) {
            // 새 작업은 이름을 받아서 프리셋으로 만든다
            openSheet { it.showNameInput(SheetView.NameMode.SAVE_DRAFT) }
            return
        }
        val r = current() ?: return
        r.put("updatedAt", System.currentTimeMillis())
        persistNow()
        emit(mapOf("type" to "routine", "json" to r.toString()))
        toast("‘${r.optString("name")}’ 저장됨")
    }

    override fun highlight(index: Int) {
        markers?.highlight = index
    }

    // ── 공통 ───────────────────────────────────────────────────────────────

    /** 변경 → 화면 갱신 + 저장 예약 + 앱에 알림. */
    private fun changed(structural: Boolean) {
        val r = current() ?: return
        r.put("updatedAt", System.currentTimeMillis())
        refreshViews(structural)
        if (draft != null) {
            draftDirty = true // 새 작업은 저장 버튼을 누르기 전까지 저장하지 않는다
            return
        }
        persistSoon()
        emit(mapOf("type" to "routine", "json" to r.toString()))
    }

    private fun refreshViews(structural: Boolean) {
        val json = current()
        markers?.steps = if (json != null) Routine.fromJson(json).steps else emptyList()
        markers?.visibility = if (markersVisible) View.VISIBLE else View.GONE
        refreshPanel()
        if (structural) rebuildHandles()
    }

    private fun refreshPanel() {
        val n = current()?.optJSONArray("steps")?.length() ?: 0
        val name = if (draft != null) "새 작업" else current()?.optString("name") ?: ""
        val unsaved = if (draft != null && draftDirty) " •" else ""
        panel?.update(isRunning, statusText.ifEmpty { "$name$unsaved\n${n}단계" }, markersVisible)
    }

    private fun openApp() {
        persistNow()
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        } ?: return
        startActivity(intent)
    }

    private fun emitOverlay() {
        emit(mapOf("type" to "overlay", "visible" to (panel != null), "routineId" to currentId()))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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

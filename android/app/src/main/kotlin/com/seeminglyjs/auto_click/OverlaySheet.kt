package com.seeminglyjs.auto_click

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** 시트가 서비스에 요청하는 동작. */
interface SheetHost {
    fun routineCount(): Int
    fun routineList(): List<JSONObject>
    fun currentId(): String?
    fun isDraft(): Boolean
    fun isDraftDirty(): Boolean
    fun current(): JSONObject?
    fun loadRoutine(id: String)
    fun newDraft()
    fun commitDraft(name: String)
    fun forceClose()
    fun renameCurrent(name: String)
    fun saveAs(name: String)
    fun deleteStep(index: Int)
    fun moveStep(index: Int, delta: Int)
    fun duplicateStep(index: Int)
    fun onEdited(structural: Boolean)
    fun saveNow()
    fun highlight(index: Int)
    fun closeSheet()
    fun setSheetFocusable(focusable: Boolean)
    fun markHelpSeen()
}

// ── 값 표시 / 증감 규칙 ───────────────────────────────────────────────────────

private fun fmtMs(v: Long) = when {
    v < 1000 -> "${v}ms"
    v % 1000 == 0L -> "${v / 1000}초"
    else -> String.format("%.2f초", v / 1000.0).trimEnd('0').trimEnd('.') + ""
}

private fun fmtSec(v: Long) = when {
    v == 0L -> "제한 없음"
    v < 60 -> "${v}초"
    v % 60 == 0L -> "${v / 60}분"
    else -> "${v / 60}분 ${v % 60}초"
}

private fun msStep(v: Long) = when {
    v < 100 -> 10L
    v < 1000 -> 50L
    v < 5000 -> 250L
    v < 30000 -> 1000L
    else -> 5000L
}

private fun countStep(v: Long) = when {
    v < 10 -> 1L
    v < 50 -> 5L
    v < 200 -> 10L
    v < 1000 -> 50L
    else -> 100L
}

private fun secStep(v: Long) = when {
    v < 60 -> 10L
    v < 600 -> 30L
    else -> 60L
}

private fun pxStep(v: Long) = if (v < 10) 1L else 5L

@SuppressLint("ViewConstructor", "SetTextI18n", "ClickableViewAccessibility")
class SheetView(context: Context, private val host: SheetHost) : LinearLayout(context) {

    private val header = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val titleView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        paint.isFakeBoldText = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val scroll = MaxHeightScrollView(context)
    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val handler = Handler(Looper.getMainLooper())

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = context.dp(24f)
            setColor(0xFA1B1F22.toInt())
        }
        val p = context.dpi(18f)
        setPadding(p, context.dpi(14f), p, p)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroll.addView(body)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dpi(10f)
        })
    }

    fun setMaxHeight(px: Int) {
        scroll.maxHeight = px
    }

    private fun setHeader(title: String, onBack: (() -> Unit)?) {
        header.removeAllViews()
        if (onBack != null) {
            header.addView(iconButton(Glyph.BACK) { onBack() }, LayoutParams(context.dpi(36f), context.dpi(36f)).apply {
                marginEnd = context.dpi(8f)
            })
        }
        titleView.text = title
        header.addView(titleView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton(Glyph.CLOSE) { host.closeSheet() }, LayoutParams(context.dpi(36f), context.dpi(36f)))
    }

    // ── 1) 루틴 설정 ──────────────────────────────────────────────────────────

    fun showSettings() {
        host.setSheetFocusable(false)
        host.highlight(-1)
        val r = host.current() ?: return host.closeSheet()
        setHeader("루틴 설정", null)
        body.removeAllViews()
        scroll.scrollTo(0, 0)

        // 현재 작업
        val draft = host.isDraft()
        section(if (draft) "현재 작업 · 저장 안 됨" else "현재 프리셋")
        body.addView(TextView(context).apply {
            text = if (draft) "새 작업" else r.optString("name", "루틴")
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            paint.isFakeBoldText = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, context.dpi(4f), 0, 0)
        })
        if (draft) body.addView(hint("저장 버튼을 누르면 이름을 정해 프리셋으로 저장됩니다."))
        val actions = row()
        actions.addView(chip("불러오기") { showPresets() }, weighted())
        actions.addView(chip("새 작업") {
            if (host.isDraftDirty()) showDiscardConfirm { host.newDraft(); showSettings() }
            else { host.newDraft(); showSettings() }
        }, weighted())
        if (!draft) actions.addView(chip("이름 변경") { showNameInput(NameMode.RENAME) }, weighted())
        body.addView(actions)

        // 반복 설정
        section("반복")
        body.addView(stepper("루프 횟수", { r.optLong("loopCount", 1) }, { r.put("loopCount", it) },
            ::countStep, { if (it == 0L) "무한" else "${it}회" }, help = "0 = 정지할 때까지 무한 반복"))
        body.addView(stepper("루프 사이 대기", { r.optLong("loopIntervalMs", 500) }, { r.put("loopIntervalMs", it) },
            ::msStep, ::fmtMs))
        body.addView(stepper("시작 전 대기", { r.optLong("startDelayMs", 1500) }, { r.put("startDelayMs", it) },
            ::msStep, ::fmtMs, help = "실행 버튼을 누른 뒤 시작까지"))
        body.addView(stepper("최대 실행 시간", { r.optLong("maxRunSeconds", 0) }, { r.put("maxRunSeconds", it) },
            ::secStep, ::fmtSec))

        section("자연스럽게 (봇 감지 회피)")
        body.addView(stepper("위치 흔들기", { r.optLong("randomOffsetPx", 0) }, { r.put("randomOffsetPx", it) },
            ::pxStep, { if (it == 0L) "끔" else "±${it}px" }, max = 300))
        body.addView(stepper("랜덤 추가 대기", { r.optLong("randomDelayMs", 0) }, { r.put("randomDelayMs", it) },
            ::msStep, { if (it == 0L) "끔" else "0~${fmtMs(it)}" }))

        // 단계 목록
        val steps = r.optJSONArray("steps")
        val n = steps?.length() ?: 0
        section("단계 ${n}개 — 눌러서 설정")
        if (n == 0) {
            body.addView(hint("아직 단계가 없습니다. 패널의 ＋ 추가 버튼으로 화면 위에 위치를 지정하세요."))
        } else {
            val rowUpdaters = mutableListOf<() -> Unit>()
            body.addView(stepper("모든 단계 간격", {
                (0 until n).map { steps!!.getJSONObject(it) }.firstOrNull { it.optString("type") != "wait" }
                    ?.optLong("delayAfterMs", 500) ?: 500L
            }, { v ->
                for (i in 0 until n) {
                    val s = steps!!.getJSONObject(i)
                    if (s.optString("type") != "wait") s.put("delayAfterMs", v)
                }
            }, ::msStep, ::fmtMs, help = "탭 사이 간격을 한 번에 바꿉니다", onAfter = { rowUpdaters.forEach { it() } }))
            for (i in 0 until n) {
                val s = steps!!.getJSONObject(i)
                val label = TextView(context)
                val update = { label.text = "${i + 1}.  ${stepSummary(s)}" }
                update()
                rowUpdaters += update
                body.addView(listRow(label) { showStep(i, fromSettings = true) })
            }
        }

        body.addView(primary(if (host.isDraft()) "프리셋으로 저장" else "저장", GREEN) { host.saveNow() },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(48f)).apply { topMargin = context.dpi(18f) })
        if (!host.isDraft()) {
            body.addView(chip("다른 이름으로 저장") { showNameInput(NameMode.SAVE_AS) },
                LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(40f)).apply { topMargin = context.dpi(8f) })
        }
        body.addView(chip("사용 방법 보기") { showHelp() }, LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(40f)).apply {
            topMargin = context.dpi(8f)
        })
    }

    // ── 2) 단계 설정 ──────────────────────────────────────────────────────────

    fun showStep(index: Int, fromSettings: Boolean) {
        host.setSheetFocusable(false)
        val r = host.current() ?: return host.closeSheet()
        val steps = r.optJSONArray("steps") ?: return host.closeSheet()
        if (index !in 0 until steps.length()) return if (fromSettings) showSettings() else host.closeSheet()
        val s = steps.getJSONObject(index)
        val type = s.optString("type", "tap")
        host.highlight(index)
        setHeader("단계 ${index + 1} · ${typeLabel(type)}", if (fromSettings) ({ showSettings() }) else null)
        body.removeAllViews()
        scroll.scrollTo(0, 0)

        if (type == "tap" || type == "doubleTap" || type == "longPress") {
            section("동작")
            val types = row()
            for ((t, label) in listOf("tap" to "탭", "doubleTap" to "더블 탭", "longPress" to "길게")) {
                types.addView(chip(label, selected = t == type) {
                    if (t != type) {
                        s.put("type", t)
                        s.put("durationMs", if (t == "longPress") 800 else 40)
                        host.onEdited(false)
                        showStep(index, fromSettings)
                    }
                }, weighted())
            }
            body.addView(types)
        }

        section("타이밍")
        if (type == "wait") {
            body.addView(stepper("대기 시간", { s.optLong("durationMs", 1000) }, { s.put("durationMs", it) },
                ::msStep, ::fmtMs, min = 10))
        }
        if (type != "wait") {
            body.addView(stepper("다음 단계까지 간격", { s.optLong("delayAfterMs", 500) }, { s.put("delayAfterMs", it) },
                ::msStep, ::fmtMs, help = "이 동작 후 기다리는 시간 (클릭 주기)"))
            body.addView(stepper("연속 반복", { s.optLong("repeat", 1) }, { s.put("repeat", it) },
                ::countStep, { "${it}회" }, min = 1, help = "다음 단계로 넘어가기 전 이 동작을 몇 번 할지"))
        }
        when (type) {
            "longPress" -> body.addView(stepper("누르고 있는 시간", { s.optLong("durationMs", 800) },
                { s.put("durationMs", it) }, ::msStep, ::fmtMs, min = 400))
            "swipe" -> body.addView(stepper("스와이프 속도(시간)", { s.optLong("durationMs", 300) },
                { s.put("durationMs", it) }, ::msStep, ::fmtMs, min = 50, help = "짧을수록 빠르게 밀어냅니다"))
        }
        if (type != "wait") {
            body.addView(hint("위치는 화면의 마커를 드래그해서 옮기세요." +
                if (type == "areaTap") " 오른쪽 아래 점을 끌면 영역 크기가 바뀝니다." else ""))
        }

        section("순서 · 편집")
        val order = row()
        order.addView(chip("◀ 앞으로") { host.moveStep(index, -1); showStep(index - 1, fromSettings) }
            .apply { isEnabled = index > 0; alpha = if (index > 0) 1f else 0.4f }, weighted())
        order.addView(chip("뒤로 ▶") { host.moveStep(index, 1); showStep(index + 1, fromSettings) }
            .apply { isEnabled = index < steps.length() - 1; alpha = if (index < steps.length() - 1) 1f else 0.4f }, weighted())
        order.addView(chip("복제") { host.duplicateStep(index); showStep(index + 1, fromSettings) }, weighted())
        body.addView(order)
        body.addView(primary("이 단계 삭제", 0xFF8E2C2C.toInt()) {
            host.deleteStep(index)
            if (fromSettings) showSettings() else host.closeSheet()
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(44f)).apply { topMargin = context.dpi(14f) })
        body.addView(primary("완료", ACCENT) {
            if (fromSettings) showSettings() else host.closeSheet()
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(48f)).apply { topMargin = context.dpi(8f) })
    }

    // ── 3) 이름 입력 ──────────────────────────────────────────────────────────

    enum class NameMode(val title: String, val button: String) {
        SAVE_DRAFT("프리셋으로 저장", "저장"),
        SAVE_DRAFT_CLOSE("저장하고 닫기", "저장하고 닫기"),
        RENAME("이름 변경", "변경"),
        SAVE_AS("다른 이름으로 저장", "저장"),
    }

    fun showNameInput(mode: NameMode) {
        val r = host.current()
        setHeader(mode.title, if (mode == NameMode.SAVE_DRAFT_CLOSE) ({ showCloseConfirm() }) else ({ showSettings() }))
        body.removeAllViews()
        val input = EditText(context).apply {
            setText(
                when (mode) {
                    NameMode.SAVE_DRAFT, NameMode.SAVE_DRAFT_CLOSE -> "루틴 ${host.routineCount() + 1}"
                    NameMode.RENAME -> r?.optString("name") ?: ""
                    NameMode.SAVE_AS -> (r?.optString("name") ?: "루틴") + " (복사)"
                },
            )
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(TEXT_DIM)
            hint = "루틴 이름"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply {
                cornerRadius = context.dp(12f)
                setColor(CHIP_BG)
            }
            val p = context.dpi(14f)
            setPadding(p, p, p, p)
        }
        body.addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val submit = {
            val name = input.text.toString().trim().ifEmpty { "이름 없는 루틴" }
            hideKeyboard(input)
            when (mode) {
                NameMode.SAVE_DRAFT -> { host.commitDraft(name); host.closeSheet() }
                NameMode.SAVE_DRAFT_CLOSE -> { host.commitDraft(name); host.forceClose() }
                NameMode.RENAME -> { host.renameCurrent(name); showSettings() }
                NameMode.SAVE_AS -> { host.saveAs(name); showSettings() }
            }
        }
        input.setOnEditorActionListener { _, _, _ -> submit(); true }
        body.addView(primary(mode.button, ACCENT) { submit() },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(48f)).apply { topMargin = context.dpi(14f) })

        host.setSheetFocusable(true)
        handler.postDelayed({
            input.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun hideKeyboard(v: View) {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(v.windowToken, 0)
    }

    // ── 프리셋 불러오기 ──────────────────────────────────────────────────────

    fun showPresets() {
        host.setSheetFocusable(false)
        setHeader("프리셋 불러오기", { showSettings() })
        body.removeAllViews()
        scroll.scrollTo(0, 0)
        if (host.isDraftDirty()) {
            body.addView(hint("지금 새 작업은 저장되지 않았어요. 불러오면 사라집니다."))
        }
        val list = host.routineList()
        if (list.isEmpty()) {
            body.addView(hint("저장된 프리셋이 없어요. 새 작업을 만든 뒤 저장 버튼으로 저장하세요."))
        }
        for (r in list) {
            val id = r.optString("id")
            val n = r.optJSONArray("steps")?.length() ?: 0
            val loops = r.optLong("loopCount", 1)
            val label = TextView(context).apply {
                text = (if (id == host.currentId()) "✓  " else "") + r.optString("name", "루틴") +
                    "\n${n}단계 · " + (if (loops == 0L) "무한 반복" else "${loops}회 반복")
            }
            body.addView(listRow(label) {
                host.loadRoutine(id)
                host.closeSheet()
            }.apply { label.maxLines = 2 })
        }
    }

    /** 저장 안 한 새 작업이 있을 때 닫기 확인. */
    fun showCloseConfirm() {
        host.setSheetFocusable(false)
        setHeader("저장하지 않은 작업", null)
        body.removeAllViews()
        body.addView(TextView(context).apply {
            text = "새 작업에 추가한 단계가 저장되지 않았어요.\n프리셋으로 저장할까요?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        body.addView(primary("저장하고 닫기", GREEN) { showNameInput(NameMode.SAVE_DRAFT_CLOSE) },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(48f)).apply { topMargin = context.dpi(18f) })
        body.addView(primary("저장 안 하고 닫기", 0xFF8E2C2C.toInt()) { host.forceClose() },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(44f)).apply { topMargin = context.dpi(8f) })
        body.addView(chip("계속 편집") { host.closeSheet() },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(40f)).apply { topMargin = context.dpi(8f) })
    }

    private fun showDiscardConfirm(onConfirm: () -> Unit) {
        setHeader("새 작업 시작", { showSettings() })
        body.removeAllViews()
        body.addView(TextView(context).apply {
            text = "지금 작업은 저장되지 않았어요. 버리고 새로 시작할까요?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        })
        body.addView(primary("먼저 저장하기", GREEN) { showNameInput(NameMode.SAVE_DRAFT) },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(48f)).apply { topMargin = context.dpi(18f) })
        body.addView(primary("버리고 새로 시작", 0xFF8E2C2C.toInt()) { onConfirm() },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(44f)).apply { topMargin = context.dpi(8f) })
    }

    // ── 4) 사용 방법 ──────────────────────────────────────────────────────────

    fun showHelp() {
        host.setSheetFocusable(false)
        host.highlight(-1)
        setHeader("사용 방법", null)
        body.removeAllViews()
        val items = listOf(
            Glyph.ADD to "추가 — 탭 · 길게 · 영역 · 스와이프 · 대기 중 고른 뒤, 화면에서 원하는 위치를 누르거나 드래그하세요. 순서대로 번호가 붙습니다.",
            Glyph.TAP to "마커 — 멈춘 상태에서 번호 마커를 누르면 클릭 주기 · 반복 횟수를 바꿀 수 있고, 끌어서 위치를 옮길 수 있습니다.",
            Glyph.PLAY to "실행 / 정지 — 단계를 순서대로 반복합니다. 실행 중엔 마커가 터치를 막지 않습니다.",
            Glyph.SETTINGS to "설정 — 루프 횟수 · 간격 · 랜덤, 저장된 프리셋 불러오기 · 새 작업 · 이름 변경.",
            Glyph.SAVE to "저장 — 새 작업은 이름을 정해 프리셋으로 저장합니다. 불러온 프리셋은 바로 저장됩니다.",
            Glyph.GRIP to "맨 위 점(⠿)을 끌면 패널 이동, 누르면 접기 / 펴기.",
        )
        for ((g, text) in items) {
            val line = row().apply { gravity = Gravity.TOP }
            line.addView(IconCircle(context, g, ACCENT), LayoutParams(context.dpi(32f), context.dpi(32f)).apply {
                marginEnd = context.dpi(12f)
            })
            line.addView(TextView(context).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.15f)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            body.addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dpi(12f)
            })
        }
        body.addView(primary("시작하기", ACCENT) { host.markHelpSeen(); host.closeSheet() },
            LayoutParams(LayoutParams.MATCH_PARENT, context.dpi(48f)).apply { topMargin = context.dpi(20f) })
    }

    // ── 위젯 헬퍼 ─────────────────────────────────────────────────────────────

    private fun stepSummary(s: JSONObject): String {
        val type = s.optString("type")
        val rep = s.optLong("repeat", 1)
        return when (type) {
            "wait" -> "대기 ${fmtMs(s.optLong("durationMs", 1000))}"
            else -> typeLabel(type) + "  ·  간격 ${fmtMs(s.optLong("delayAfterMs", 500))}" +
                if (rep > 1) "  ×$rep" else ""
        }
    }

    private fun row() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dpi(6f)
        }
    }

    private fun weighted() = LayoutParams(0, context.dpi(40f), 1f).apply {
        marginStart = context.dpi(3f)
        marginEnd = context.dpi(3f)
    }

    private fun section(text: String) {
        body.addView(TextView(context).apply {
            this.text = text
            setTextColor(ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint.isFakeBoldText = true
            setPadding(0, context.dpi(16f), 0, context.dpi(2f))
        })
    }

    private fun hint(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(TEXT_DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, context.dpi(6f), 0, 0)
    }

    private fun iconButton(glyph: Glyph, onClick: () -> Unit) = IconCircle(context, glyph, CHIP_BG).apply {
        setOnClickListener { onClick() }
    }

    private fun chip(text: String, selected: Boolean = false, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        background = GradientDrawable().apply {
            cornerRadius = context.dp(12f)
            setColor(if (selected) ACCENT else CHIP_BG)
        }
        setOnClickListener { onClick() }
    }

    private fun primary(text: String, color: Int, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = context.dp(14f)
            setColor(color)
        }
        setOnClickListener { onClick() }
    }

    private fun listRow(label: TextView, onClick: () -> Unit): View {
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        val r = row().apply {
            background = GradientDrawable().apply {
                cornerRadius = context.dp(12f)
                setColor(CHIP_BG)
            }
            val p = context.dpi(12f)
            setPadding(p, p, p, p)
            setOnClickListener { onClick() }
        }
        r.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        r.addView(TextView(context).apply {
            text = "›"
            setTextColor(TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        })
        return r
    }

    /**
     * − 값 + 조절기. 버튼을 누르고 있으면 값이 계속 바뀐다.
     * 값이 바뀔 때마다 [SheetHost.onEdited] 로 즉시 반영 · 저장된다.
     */
    private fun stepper(
        label: String,
        get: () -> Long,
        set: (Long) -> Unit,
        step: (Long) -> Long,
        format: (Long) -> String,
        min: Long = 0,
        max: Long = 3_600_000,
        help: String? = null,
        onAfter: (() -> Unit)? = null,
    ): View {
        val col = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, context.dpi(8f), 0, 0)
        }
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        line.addView(TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            minWidth = context.dpi(84f)
        }
        val refresh = { value.text = format(get()) }
        refresh()
        val apply = { dir: Int ->
            val v = get()
            val next = (if (dir > 0) v + step(v) else v - step((v - 1).coerceAtLeast(0))).coerceIn(min, max)
            if (next != v) {
                set(next)
                refresh()
                onAfter?.invoke()
                host.onEdited(false)
            }
        }
        line.addView(repeatButton("−") { apply(-1) }, LayoutParams(context.dpi(40f), context.dpi(40f)))
        line.addView(value)
        line.addView(repeatButton("+") { apply(1) }, LayoutParams(context.dpi(40f), context.dpi(40f)))
        col.addView(line)
        if (help != null) col.addView(hint(help).apply { setPadding(0, 0, 0, 0) })
        return col
    }

    private fun repeatButton(text: String, action: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(CHIP_BG)
        }
        val repeater = object : Runnable {
            var delay = 380L
            override fun run() {
                action()
                delay = (delay * 0.8).toLong().coerceAtLeast(50L)
                handler.postDelayed(this, delay)
            }
        }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.6f
                    action()
                    repeater.delay = 380L
                    handler.postDelayed(repeater, repeater.delay)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    handler.removeCallbacks(repeater)
                }
            }
            true
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}

/** 최대 높이가 있는 스크롤뷰 (화면을 넘지 않게). */
class MaxHeightScrollView(context: Context) : ScrollView(context) {
    var maxHeight = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, hSpec)
    }
}

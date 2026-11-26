package com.seeminglyjs.auto_click

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private var channel: MethodChannel? = null

    private val serviceComponent by lazy {
        ComponentName(this, AutoClickService::class.java)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val ch = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "auto_click/native")
        channel = ch
        ch.setMethodCallHandler(::handle)
        AutoClickService.eventSink = { event ->
            runOnUiThread { channel?.invokeMethod("onEvent", event) }
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        AutoClickService.eventSink = null
        channel?.setMethodCallHandler(null)
        channel = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        try {
            val service = AutoClickService.instance
            when (call.method) {
                "isServiceEnabled" -> result.success(service != null)
                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(true)
                }
                "openAppSettings" -> {
                    openAppSettings()
                    result.success(true)
                }
                "showOverlay" -> {
                    if (service == null) return result.success(false)
                    service.showOverlay(call.argument<String>("routine")!!)
                    result.success(true)
                }
                "runRoutine" -> {
                    if (service == null) return result.success(false)
                    service.runRoutine(call.argument<String>("routine")!!)
                    result.success(true)
                }
                "updateOverlayRoutine" -> {
                    service?.updateRoutine(call.argument<String>("routine")!!)
                    result.success(null)
                }
                "takeOverlayRoutine" -> result.success(service?.takeRoutineIfDirty())
                "stopRoutine" -> {
                    service?.stopRoutine()
                    result.success(null)
                }
                "hideOverlay" -> {
                    service?.hideOverlay()
                    result.success(null)
                }
                "isRunning" -> result.success(service?.isRunning == true)
                "isOverlayVisible" -> result.success(service?.isOverlayVisible == true)
                "moveToBackground" -> {
                    moveTaskToBack(true)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("native_error", e.message, null)
        }
    }

    /**
     * 접근성 설정으로 이동. 가능한 한 Auto Click 항목으로 바로 보낸다.
     * 1) 접근성 서비스 상세 화면 (제조사 설정이 허용하는 기기에서만 열림)
     * 2) 접근성 목록 + 해당 항목 강조 (삼성/픽셀 등 다수 기기 지원)
     * 3) 모두 실패하면 앱 정보 화면
     */
    private fun openAccessibilitySettings() {
        val flat = serviceComponent.flattenToString()
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, flat)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(details)) return

        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            val args = Bundle().apply { putString(":settings:fragment_args_key", flat) }
            putExtra(":settings:fragment_args_key", flat)
            putExtra(":settings:show_fragment_args", args)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(list)) return
        openAppSettings()
    }

    /** 앱 정보 화면 (⋮ › 제한된 설정 허용 이 여기 있다). */
    private fun openAppSettings() {
        tryStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

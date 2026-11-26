import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../models/routine.dart';

/// Android 네이티브(접근성 서비스 + 오버레이)와 통신하는 브리지.
///
/// 다른 앱 위에서 터치를 주입하는 건 Android 접근성 서비스의
/// `dispatchGesture` 로만 가능하다. iOS · 웹은 OS 정책상 불가능하므로
/// [isSupported] 가 false 이고, 대신 앱 내부 테스트 패드를 사용한다.
class NativeBridge {
  NativeBridge._() {
    if (isSupported) _channel.setMethodCallHandler(_onCall);
  }

  static final instance = NativeBridge._();
  static const _channel = MethodChannel('auto_click/native');

  static bool get isSupported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// 접근성 서비스가 켜져 연결되어 있는지.
  final serviceEnabled = ValueNotifier<bool>(false);

  /// 네이티브에서 루틴이 실행 중인지.
  final running = ValueNotifier<bool>(false);

  /// 현재 오버레이에 올라간 루틴 ID (없으면 null).
  final overlayRoutineId = ValueNotifier<String?>(null);

  /// 오버레이에서 단계를 추가/삭제했을 때 호출된다.
  void Function(Routine routine)? onRoutineFromNative;

  Future<T?> _invoke<T>(String method, [Object? args]) async {
    if (!isSupported) return null;
    try {
      return await _channel.invokeMethod<T>(method, args);
    } on PlatformException catch (e) {
      debugPrint('native $method failed: ${e.message}');
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  Future<bool> refreshServiceStatus() async {
    final enabled = await _invoke<bool>('isServiceEnabled') ?? false;
    serviceEnabled.value = enabled;
    if (enabled) {
      running.value = await _invoke<bool>('isRunning') ?? false;
      final visible = await _invoke<bool>('isOverlayVisible') ?? false;
      if (!visible) overlayRoutineId.value = null;
    } else {
      running.value = false;
      overlayRoutineId.value = null;
    }
    return enabled;
  }

  Future<void> openAccessibilitySettings() =>
      _invoke('openAccessibilitySettings');

  Future<void> openAppSettings() => _invoke('openAppSettings');

  /// 플로팅 패널을 띄워 다른 앱 위에서 위치를 지정/실행할 수 있게 한다.
  Future<bool> showOverlay(Routine r, {bool goBackground = true}) async {
    final ok = await _invoke<bool>(
            'showOverlay', {'routine': jsonEncode(r.toJson())}) ??
        false;
    if (ok) {
      overlayRoutineId.value = r.id;
      if (goBackground) await _invoke('moveToBackground');
    }
    return ok;
  }

  /// 패널을 띄우고 곧바로 루틴을 실행한다 (시작 지연 후 시작).
  Future<bool> runRoutine(Routine r) async {
    final ok = await _invoke<bool>(
            'runRoutine', {'routine': jsonEncode(r.toJson())}) ??
        false;
    if (ok) {
      overlayRoutineId.value = r.id;
      running.value = true;
      await _invoke('moveToBackground');
    }
    return ok;
  }

  Future<void> stopRoutine() async {
    await _invoke('stopRoutine');
    running.value = false;
  }

  Future<void> hideOverlay() async {
    await _invoke('hideOverlay');
    overlayRoutineId.value = null;
    running.value = false;
  }

  /// 앱에서 편집한 루틴이 오버레이에 떠 있으면 그 내용도 갱신한다.
  Future<void> syncIfOverlay(Routine r) async {
    if (overlayRoutineId.value != r.id) return;
    await _invoke('updateOverlayRoutine', {'routine': jsonEncode(r.toJson())});
  }

  /// 오버레이에서 변경된 루틴이 있으면 가져온다 (앱 복귀 시 호출).
  Future<Routine?> takeOverlayRoutine() async {
    final raw = await _invoke<String>('takeOverlayRoutine');
    if (raw == null) return null;
    return Routine.fromJson(Map<String, dynamic>.from(jsonDecode(raw) as Map));
  }

  Future<dynamic> _onCall(MethodCall call) async {
    if (call.method != 'onEvent') return null;
    final ev = Map<String, dynamic>.from(call.arguments as Map);
    switch (ev['type']) {
      case 'service':
        serviceEnabled.value = ev['enabled'] == true;
        if (!serviceEnabled.value) {
          running.value = false;
          overlayRoutineId.value = null;
        }
      case 'run':
        running.value = ev['running'] == true;
      case 'overlay':
        if (ev['visible'] != true) {
          overlayRoutineId.value = null;
          running.value = false;
        }
      case 'routine':
        final raw = ev['json'] as String?;
        if (raw != null) {
          final r = Routine.fromJson(
              Map<String, dynamic>.from(jsonDecode(raw) as Map));
          onRoutineFromNative?.call(r);
        }
    }
    return null;
  }
}

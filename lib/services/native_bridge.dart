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

  /// 플로팅 패널이 떠 있는지.
  final overlayVisible = ValueNotifier<bool>(false);

  /// 패널에 불러온 프리셋 ID. 빈 패널(저장 안 된 새 작업)이면 null.
  final overlayRoutineId = ValueNotifier<String?>(null);

  void _setOverlay(bool visible, [String? routineId]) {
    overlayVisible.value = visible;
    overlayRoutineId.value = visible ? routineId : null;
  }

  /// 오버레이 패널에서 루틴을 바꾸거나 저장했을 때 호출된다.
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
      if (!visible) _setOverlay(false);
      overlayVisible.value = visible;
    } else {
      running.value = false;
      _setOverlay(false);
    }
    return enabled;
  }

  Future<void> openAccessibilitySettings() =>
      _invoke('openAccessibilitySettings');

  Future<void> openAppSettings() => _invoke('openAppSettings');

  /// 프리셋 없이 빈 플로팅 패널(새 작업)을 띄운다.
  Future<bool> showEmptyOverlay() async {
    final ok = await _invoke<bool>('showEmptyOverlay') ?? false;
    if (ok) {
      _setOverlay(true);
      await _invoke('moveToBackground');
    }
    return ok;
  }

  /// 지정한 프리셋으로 플로팅 패널을 띄운다.
  Future<bool> showOverlay(Routine r, {bool goBackground = true}) async {
    final ok = await _invoke<bool>(
            'showOverlay', {'routine': jsonEncode(r.toJson())}) ??
        false;
    if (ok) {
      _setOverlay(true, r.id);
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
      _setOverlay(true, r.id);
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
    _setOverlay(false);
    running.value = false;
  }

  /// 오버레이가 떠 있으면 앱에서 편집한 루틴을 오버레이 목록에도 반영한다.
  Future<void> syncIfOverlay(Routine r) async {
    if (!overlayVisible.value) return;
    await _invoke('updateOverlayRoutine', {'routine': jsonEncode(r.toJson())});
  }

  /// 앱에서 삭제한 루틴을 오버레이 목록에서도 뺀다.
  Future<void> removeFromOverlay(String id) async {
    if (!overlayVisible.value) return;
    await _invoke('removeOverlayRoutine', {'id': id});
  }

  Future<dynamic> _onCall(MethodCall call) async {
    if (call.method != 'onEvent') return null;
    final ev = Map<String, dynamic>.from(call.arguments as Map);
    switch (ev['type']) {
      case 'service':
        serviceEnabled.value = ev['enabled'] == true;
        if (!serviceEnabled.value) {
          running.value = false;
          _setOverlay(false);
        }
      case 'run':
        running.value = ev['running'] == true;
      case 'overlay':
        final visible = ev['visible'] == true;
        _setOverlay(visible, ev['routineId'] as String?);
        if (!visible) running.value = false;
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

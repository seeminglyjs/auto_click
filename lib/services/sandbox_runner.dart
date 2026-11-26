import 'dart:async';
import 'dart:math';

import 'package:flutter/gestures.dart';
import 'package:flutter/widgets.dart';

import '../models/action_step.dart';
import '../models/routine.dart';

/// 화면에 그릴 터치 효과.
class TouchFx {
  TouchFx(this.start, {this.end, this.longPress = false})
      : at = DateTime.now();

  final Offset start;
  final Offset? end;
  final bool longPress;
  final DateTime at;

  double get ageMs => DateTime.now().difference(at).inMilliseconds.toDouble();
}

/// 앱 내부(Flutter 뷰)에 가짜 포인터 이벤트를 주입해 루틴을 실행한다.
///
/// iOS · 웹 · (권한 없는) Android 어디서나 동작하며, 루틴이 의도대로
/// 움직이는지 테스트 패드에서 검증하는 용도다.
class SandboxRunner extends ChangeNotifier {
  SandboxRunner({required this.viewId, required this.sizeOf});

  final int viewId;
  final Size Function() sizeOf;

  bool running = false;
  int currentIndex = -1;
  int loop = 0;
  int injected = 0;
  final List<TouchFx> effects = [];

  int _token = 0;
  static int _nextPointer = 900000;
  final _rng = Random();
  final _clock = Stopwatch()..start();

  bool _alive(int t) => running && t == _token;

  Future<void> start(Routine routine) async {
    if (running || routine.steps.isEmpty) return;
    running = true;
    final t = ++_token;
    loop = 0;
    injected = 0;
    notifyListeners();

    final sw = Stopwatch()..start();
    bool timeUp() =>
        routine.maxRunSeconds > 0 &&
        sw.elapsed.inSeconds >= routine.maxRunSeconds;

    await _sleep(routine.startDelayMs);
    outer:
    while (_alive(t)) {
      for (var i = 0; i < routine.steps.length; i++) {
        final s = routine.steps[i];
        for (var r = 0; r < max(1, s.repeat); r++) {
          if (!_alive(t) || timeUp()) break outer;
          currentIndex = i;
          notifyListeners();
          await _perform(routine, s);
          await _sleep(_jitter(routine, s.delayAfterMs));
        }
      }
      loop++;
      notifyListeners();
      if (routine.loopCount > 0 && loop >= routine.loopCount) break;
      await _sleep(routine.loopIntervalMs);
    }
    if (t == _token) {
      running = false;
      currentIndex = -1;
      notifyListeners();
    }
  }

  void stop() {
    _token++;
    running = false;
    currentIndex = -1;
    notifyListeners();
  }

  int _jitter(Routine r, int ms) =>
      ms + (r.randomDelayMs > 0 ? _rng.nextInt(r.randomDelayMs + 1) : 0);

  Future<void> _sleep(int ms) =>
      Future<void>.delayed(Duration(milliseconds: max(0, ms)));

  Offset _toScreen(Routine r, double nx, double ny) {
    final size = sizeOf();
    final off = r.randomOffsetPx;
    double j() => off > 0 ? (_rng.nextDouble() * 2 - 1) * off : 0;
    return Offset(
      (nx * size.width + j()).clamp(0, size.width - 1),
      (ny * size.height + j()).clamp(0, size.height - 1),
    );
  }

  Future<void> _perform(Routine r, ActionStep s) async {
    switch (s.type) {
      case StepType.wait:
        await _sleep(s.durationMs);
      case StepType.tap:
        await _tap(_toScreen(r, s.x, s.y), s.durationMs);
      case StepType.doubleTap:
        final p = _toScreen(r, s.x, s.y);
        await _tap(p, s.durationMs);
        await _sleep(90);
        await _tap(p, s.durationMs);
      case StepType.longPress:
        final p = _toScreen(r, s.x, s.y);
        _fx(TouchFx(p, longPress: true));
        await _press(p, max(400, s.durationMs));
      case StepType.areaTap:
        final nx = _lerp(min(s.x, s.x2), max(s.x, s.x2), _rng.nextDouble());
        final ny = _lerp(min(s.y, s.y2), max(s.y, s.y2), _rng.nextDouble());
        await _tap(_toScreen(r, nx, ny), s.durationMs);
      case StepType.swipe:
        await _swipe(_toScreen(r, s.x, s.y), _toScreen(r, s.x2, s.y2),
            max(50, s.durationMs));
    }
  }

  double _lerp(double a, double b, double t) => a + (b - a) * t;

  void _fx(TouchFx fx) {
    effects.add(fx);
    effects.removeWhere((e) => e.ageMs > 1200);
    injected++;
    notifyListeners();
  }

  Future<void> _tap(Offset p, int durationMs) async {
    _fx(TouchFx(p));
    await _press(p, durationMs.clamp(1, 300));
  }

  Future<void> _press(Offset p, int durationMs) async {
    final id = _nextPointer++;
    _dispatch(PointerDownEvent(
        viewId: viewId,
        timeStamp: _clock.elapsed,
        pointer: id,
        position: p,
        kind: PointerDeviceKind.touch));
    await _sleep(durationMs);
    _dispatch(PointerUpEvent(
        viewId: viewId,
        timeStamp: _clock.elapsed,
        pointer: id,
        position: p,
        kind: PointerDeviceKind.touch));
  }

  Future<void> _swipe(Offset a, Offset b, int durationMs) async {
    _fx(TouchFx(a, end: b));
    final id = _nextPointer++;
    _dispatch(PointerDownEvent(
        viewId: viewId,
        timeStamp: _clock.elapsed,
        pointer: id,
        position: a,
        kind: PointerDeviceKind.touch));
    final steps = max(2, durationMs ~/ 16);
    var prev = a;
    for (var k = 1; k <= steps; k++) {
      await _sleep(durationMs ~/ steps);
      final p = Offset.lerp(a, b, k / steps)!;
      _dispatch(PointerMoveEvent(
          viewId: viewId,
          timeStamp: _clock.elapsed,
          pointer: id,
          position: p,
          delta: p - prev,
          kind: PointerDeviceKind.touch));
      prev = p;
    }
    _dispatch(PointerUpEvent(
        viewId: viewId,
        timeStamp: _clock.elapsed,
        pointer: id,
        position: b,
        kind: PointerDeviceKind.touch));
  }

  void _dispatch(PointerEvent e) =>
      GestureBinding.instance.handlePointerEvent(e);

  @override
  void dispose() {
    _token++;
    running = false;
    super.dispose();
  }
}

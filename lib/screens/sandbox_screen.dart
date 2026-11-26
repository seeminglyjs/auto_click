import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';

import '../services/routine_store.dart';
import '../services/sandbox_runner.dart';
import '../widgets/steps_painter.dart';

/// 테스트 패드: 앱 내부에 터치를 주입해 루틴을 검증한다.
///
/// iOS · 웹에서는 이 화면이 실제 실행 환경이고, Android 에서는
/// 다른 앱에서 돌리기 전에 미리 확인하는 용도다.
class SandboxScreen extends StatefulWidget {
  const SandboxScreen(
      {super.key, required this.routineId, this.autoStart = false});

  final String routineId;
  final bool autoStart;

  @override
  State<SandboxScreen> createState() => _SandboxScreenState();
}

class _SandboxScreenState extends State<SandboxScreen>
    with SingleTickerProviderStateMixin {
  static const _cols = 4;
  static const _rows = 6;

  SandboxRunner? _runner;
  late final Ticker _ticker = createTicker((_) {
    final fx = _runner?.effects;
    if (fx == null || fx.isEmpty) return;
    fx.removeWhere((e) => e.ageMs > 1200);
    setState(() {});
  });
  Size _size = Size.zero;
  bool _markers = true;
  final _taps = List<int>.filled(_cols * _rows, 0);
  final _longs = List<int>.filled(_cols * _rows, 0);
  int _swipes = 0;
  final List<(Offset, Offset)> _trails = [];
  Offset? _panStart;
  Offset? _panLast;

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _ticker.start();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_runner == null) {
      _runner = SandboxRunner(
        viewId: View.of(context).viewId,
        sizeOf: () => _size,
      )..addListener(_onRunner);
      if (widget.autoStart) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _start());
      }
    }
  }

  void _onRunner() {
    if (mounted) setState(() {});
  }

  void _start() {
    final r = routineStore.byId(widget.routineId);
    if (r != null) _runner?.start(r);
  }

  void _reset() => setState(() {
        _taps.fillRange(0, _taps.length, 0);
        _longs.fillRange(0, _longs.length, 0);
        _swipes = 0;
        _trails.clear();
      });

  @override
  void dispose() {
    _ticker.dispose();
    _runner?.removeListener(_onRunner);
    _runner?.dispose();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _size = MediaQuery.sizeOf(context);
    final routine = routineStore.byId(widget.routineId);
    final runner = _runner!;
    final scheme = Theme.of(context).colorScheme;
    final totalTaps = _taps.fold<int>(0, (a, b) => a + b);
    final totalLongs = _longs.fold<int>(0, (a, b) => a + b);

    return Scaffold(
      body: Stack(
        children: [
          // 1) 터치를 받는 테스트 타일들 + 스와이프 감지
          Positioned.fill(
            child: GestureDetector(
              onPanStart: (d) => _panStart = _panLast = d.localPosition,
              onPanUpdate: (d) => _panLast = d.localPosition,
              onPanEnd: (_) {
                if (_panStart != null && _panLast != null) {
                  setState(() {
                    _swipes++;
                    _trails.add((_panStart!, _panLast!));
                    if (_trails.length > 12) _trails.removeAt(0);
                  });
                }
                _panStart = _panLast = null;
              },
              child: Column(
                children: [
                  for (var r = 0; r < _rows; r++)
                    Expanded(
                      child: Row(
                        children: [
                          for (var c = 0; c < _cols; c++)
                            Expanded(child: _tile(r * _cols + c, scheme)),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
          // 2) 스와이프 흔적 · 단계 마커 · 터치 효과 (터치 통과)
          Positioned.fill(
            child: IgnorePointer(
              child: CustomPaint(
                painter: _FxPainter(
                  trails: List.of(_trails),
                  effects: List.of(runner.effects),
                  color: scheme.tertiary,
                ),
                foregroundPainter: _markers && routine != null
                    ? StepsPainter(
                        steps: routine.steps,
                        highlight: runner.currentIndex,
                        color: scheme.primary.withValues(alpha: 0.8),
                      )
                    : null,
              ),
            ),
          ),
          // 3) 컨트롤 HUD
          SafeArea(
            child: Align(
              alignment: Alignment.bottomCenter,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Material(
                  elevation: 6,
                  color: scheme.surfaceContainerHigh.withValues(alpha: 0.95),
                  borderRadius: BorderRadius.circular(20),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          routine?.name ?? '루틴 없음',
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
                        Text(
                          '${runner.running ? '실행 중 · 단계 ${runner.currentIndex + 1} · 루프 ${runner.loop + 1}' : '정지됨'}\n'
                          '탭 $totalTaps · 길게 $totalLongs · 스와이프 $_swipes · 주입 ${runner.injected}',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton.filled(
                              tooltip: runner.running ? '정지' : '실행',
                              onPressed: routine == null
                                  ? null
                                  : (runner.running ? runner.stop : _start),
                              icon: Icon(runner.running
                                  ? Icons.stop
                                  : Icons.play_arrow),
                            ),
                            IconButton(
                              tooltip: '마커 표시',
                              onPressed: () =>
                                  setState(() => _markers = !_markers),
                              icon: Icon(_markers
                                  ? Icons.visibility
                                  : Icons.visibility_off),
                            ),
                            IconButton(
                              tooltip: '카운터 초기화',
                              onPressed: _reset,
                              icon: const Icon(Icons.restart_alt),
                            ),
                            IconButton(
                              tooltip: '닫기',
                              onPressed: () => Navigator.pop(context),
                              icon: const Icon(Icons.close),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _tile(int i, ColorScheme scheme) {
    final hits = _taps[i] + _longs[i];
    final base = (i + i ~/ _cols).isEven
        ? scheme.surfaceContainerLow
        : scheme.surfaceContainer;
    return Material(
      color: Color.lerp(base, scheme.primaryContainer,
          (hits / 20).clamp(0.0, 1.0)),
      child: InkWell(
        onTap: () => setState(() => _taps[i]++),
        onLongPress: () => setState(() => _longs[i]++),
        child: Center(
          child: Text(
            hits == 0 ? '' : '${_taps[i]}${_longs[i] > 0 ? '\n⏺${_longs[i]}' : ''}',
            textAlign: TextAlign.center,
            style: TextStyle(
                color: scheme.onPrimaryContainer, fontWeight: FontWeight.bold),
          ),
        ),
      ),
    );
  }
}

class _FxPainter extends CustomPainter {
  _FxPainter(
      {required this.trails, required this.effects, required this.color});

  final List<(Offset, Offset)> trails;
  final List<TouchFx> effects;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final trailPaint = Paint()
      ..color = color.withValues(alpha: 0.35)
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round;
    for (final (a, b) in trails) {
      canvas.drawLine(a, b, trailPaint);
      drawArrowHead(canvas, a, b, color.withValues(alpha: 0.35));
    }
    for (final fx in effects) {
      final t = (fx.ageMs / (fx.longPress ? 1200 : 600)).clamp(0.0, 1.0);
      if (t >= 1) continue;
      final p = Paint()
        ..color = color.withValues(alpha: 1 - t)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4;
      if (fx.end != null) {
        canvas.drawLine(fx.start, fx.end!, p);
        drawArrowHead(canvas, fx.start, fx.end!, color.withValues(alpha: 1 - t));
      } else {
        canvas.drawCircle(fx.start, 10 + 30 * t, p);
        canvas.drawCircle(
            fx.start, 6, Paint()..color = color.withValues(alpha: 1 - t));
      }
    }
  }

  @override
  bool shouldRepaint(_FxPainter old) => true;
}

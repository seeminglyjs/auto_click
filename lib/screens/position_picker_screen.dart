import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/action_step.dart';
import '../widgets/steps_painter.dart';

enum PickMode { point, area, line }

typedef PickResult = ({double x, double y, double x2, double y2});

/// 전체 화면에서 탭(지점) 또는 드래그(영역 / 스와이프)로 좌표를 지정한다.
class PositionPickerScreen extends StatefulWidget {
  const PositionPickerScreen({
    super.key,
    required this.mode,
    required this.initial,
    this.otherSteps = const [],
  });

  final PickMode mode;
  final PickResult initial;

  /// 참고용으로 흐리게 보여줄 같은 루틴의 다른 단계.
  final List<ActionStep> otherSteps;

  static Future<PickResult?> open(BuildContext context,
      {required PickMode mode,
      required PickResult initial,
      List<ActionStep> otherSteps = const []}) {
    return Navigator.of(context).push<PickResult>(MaterialPageRoute(
      fullscreenDialog: true,
      builder: (_) => PositionPickerScreen(
          mode: mode, initial: initial, otherSteps: otherSteps),
    ));
  }

  @override
  State<PositionPickerScreen> createState() => _PositionPickerScreenState();
}

class _PositionPickerScreenState extends State<PositionPickerScreen> {
  late Offset _a = Offset(widget.initial.x, widget.initial.y);
  late Offset _b = Offset(widget.initial.x2, widget.initial.y2);

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
  }

  @override
  void dispose() {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  Offset _norm(Offset p, Size s) => Offset(
      (p.dx / s.width).clamp(0.0, 1.0), (p.dy / s.height).clamp(0.0, 1.0));

  String get _hint => switch (widget.mode) {
        PickMode.point => '누를 위치를 탭하세요',
        PickMode.area => '드래그해서 랜덤으로 누를 영역을 지정하세요',
        PickMode.line => '스와이프할 방향으로 드래그하세요',
      };

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: Colors.black87,
      body: Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTapUp: widget.mode == PickMode.point
                  ? (d) => setState(() => _a = _norm(d.localPosition, size))
                  : null,
              onPanStart: widget.mode == PickMode.point
                  ? null
                  : (d) => setState(() {
                        _a = _norm(d.localPosition, size);
                        _b = _a;
                      }),
              onPanUpdate: widget.mode == PickMode.point
                  ? null
                  : (d) =>
                      setState(() => _b = _norm(d.localPosition, size)),
              child: CustomPaint(
                painter: _PickPainter(
                  mode: widget.mode,
                  a: _a,
                  b: _b,
                  color: scheme.primary,
                  others: widget.otherSteps,
                ),
              ),
            ),
          ),
          SafeArea(
            child: Align(
              alignment: Alignment.topCenter,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Material(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(12),
                  child: Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    child: Text(
                      '$_hint\n'
                      '(${(_a.dx * 100).toStringAsFixed(1)}%, ${(_a.dy * 100).toStringAsFixed(1)}%)'
                      '${widget.mode == PickMode.point ? '' : ' → (${(_b.dx * 100).toStringAsFixed(1)}%, ${(_b.dy * 100).toStringAsFixed(1)}%)'}',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white),
                    ),
                  ),
                ),
              ),
            ),
          ),
          SafeArea(
            child: Align(
              alignment: Alignment.bottomCenter,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    OutlinedButton(
                      style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          backgroundColor: Colors.black54),
                      onPressed: () => Navigator.pop(context),
                      child: const Text('취소'),
                    ),
                    const SizedBox(width: 12),
                    FilledButton(
                      onPressed: () => Navigator.pop<PickResult>(context, (
                        x: _a.dx,
                        y: _a.dy,
                        x2: widget.mode == PickMode.point ? _a.dx : _b.dx,
                        y2: widget.mode == PickMode.point ? _a.dy : _b.dy,
                      )),
                      child: const Text('확인'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _PickPainter extends CustomPainter {
  _PickPainter({
    required this.mode,
    required this.a,
    required this.b,
    required this.color,
    required this.others,
  });

  final PickMode mode;
  final Offset a;
  final Offset b;
  final Color color;
  final List<ActionStep> others;

  @override
  void paint(Canvas canvas, Size size) {
    // 10% 격자
    final grid = Paint()
      ..color = Colors.white12
      ..strokeWidth = 1;
    for (var i = 1; i < 10; i++) {
      canvas.drawLine(Offset(size.width * i / 10, 0),
          Offset(size.width * i / 10, size.height), grid);
      canvas.drawLine(Offset(0, size.height * i / 10),
          Offset(size.width, size.height * i / 10), grid);
    }
    StepsPainter(steps: others, color: Colors.white38).paint(canvas, size);

    final pa = Offset(a.dx * size.width, a.dy * size.height);
    final pb = Offset(b.dx * size.width, b.dy * size.height);
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3;
    final fill = Paint()..color = color.withValues(alpha: 0.3);
    switch (mode) {
      case PickMode.point:
        canvas.drawCircle(pa, 24, fill);
        canvas.drawCircle(pa, 24, stroke);
        canvas.drawLine(pa - const Offset(34, 0), pa + const Offset(34, 0),
            stroke..strokeWidth = 1);
        canvas.drawLine(pa - const Offset(0, 34), pa + const Offset(0, 34),
            stroke);
      case PickMode.area:
        final r = Rect.fromPoints(pa, pb);
        canvas.drawRect(r, fill);
        canvas.drawRect(r, stroke);
      case PickMode.line:
        canvas.drawLine(pa, pb, stroke..strokeWidth = 5);
        canvas.drawCircle(pa, 14, fill);
        drawArrowHead(canvas, pa, pb, color);
    }
  }

  @override
  bool shouldRepaint(_PickPainter old) => old.a != a || old.b != b;
}

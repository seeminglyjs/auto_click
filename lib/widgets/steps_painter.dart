import 'dart:math';

import 'package:flutter/material.dart';

import '../models/action_step.dart';

/// 루틴 단계를 화면 위에 번호 마커로 그린다 (테스트 패드 / 위치 지정 화면).
class StepsPainter extends CustomPainter {
  StepsPainter({
    required this.steps,
    this.highlight = -1,
    this.color = const Color(0xFF26A69A),
    this.highlightColor = const Color(0xFFFF7043),
  });

  final List<ActionStep> steps;
  final int highlight;
  final Color color;
  final Color highlightColor;

  @override
  void paint(Canvas canvas, Size size) {
    for (var i = 0; i < steps.length; i++) {
      final s = steps[i];
      if (s.type == StepType.wait) continue;
      final c = i == highlight ? highlightColor : color;
      final a = Offset(s.x * size.width, s.y * size.height);
      final b = Offset(s.x2 * size.width, s.y2 * size.height);
      final stroke = Paint()
        ..color = c
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3;
      final fill = Paint()..color = c.withValues(alpha: 0.25);

      switch (s.type) {
        case StepType.areaTap:
          final rect = Rect.fromPoints(a, b);
          canvas.drawRect(rect, fill);
          canvas.drawRect(rect, stroke);
          _label(canvas, rect.center, '${i + 1}', c);
        case StepType.swipe:
          canvas.drawLine(a, b, stroke..strokeWidth = 4);
          drawArrowHead(canvas, a, b, c);
          canvas.drawCircle(a, 18, fill);
          _label(canvas, a, '${i + 1}', c);
        default:
          canvas.drawCircle(a, 20, fill);
          canvas.drawCircle(a, 20, stroke);
          if (s.type == StepType.longPress) {
            canvas.drawCircle(a, 27, stroke..strokeWidth = 2);
          }
          if (s.type == StepType.doubleTap) {
            canvas.drawCircle(a, 13, stroke..strokeWidth = 2);
          }
          _label(canvas, a, '${i + 1}', c);
      }
    }
  }

  void _label(Canvas canvas, Offset center, String text, Color c) {
    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(
            color: Colors.white,
            fontSize: 13,
            fontWeight: FontWeight.bold,
            shadows: [Shadow(color: c, blurRadius: 4)]),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, center - Offset(tp.width / 2, tp.height / 2));
  }

  @override
  bool shouldRepaint(StepsPainter old) =>
      old.steps != steps || old.highlight != highlight || old.color != color;
}

void drawArrowHead(Canvas canvas, Offset a, Offset b, Color c) {
  final angle = atan2(b.dy - a.dy, b.dx - a.dx);
  const len = 16.0;
  final path = Path()
    ..moveTo(b.dx, b.dy)
    ..lineTo(b.dx - len * cos(angle - 0.45), b.dy - len * sin(angle - 0.45))
    ..lineTo(b.dx - len * cos(angle + 0.45), b.dy - len * sin(angle + 0.45))
    ..close();
  canvas.drawPath(path, Paint()..color = c);
}

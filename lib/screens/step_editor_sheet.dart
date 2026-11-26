import 'package:flutter/material.dart';

import '../models/action_step.dart';
import '../widgets/num_field.dart';
import '../widgets/step_icon.dart';
import 'position_picker_screen.dart';

/// 단계 하나를 편집하는 바텀시트. 저장하면 수정된 [ActionStep] 을 반환한다.
class StepEditorSheet extends StatefulWidget {
  const StepEditorSheet({super.key, required this.initial, this.otherSteps = const []});

  final ActionStep initial;
  final List<ActionStep> otherSteps;

  static Future<ActionStep?> open(BuildContext context, ActionStep step,
      {List<ActionStep> otherSteps = const []}) {
    return showModalBottomSheet<ActionStep>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      useSafeArea: true,
      builder: (_) => StepEditorSheet(initial: step, otherSteps: otherSteps),
    );
  }

  @override
  State<StepEditorSheet> createState() => _StepEditorSheetState();
}

class _StepEditorSheetState extends State<StepEditorSheet> {
  late ActionStep _s = widget.initial;
  late final _label = TextEditingController(text: widget.initial.label);

  @override
  void dispose() {
    _label.dispose();
    super.dispose();
  }

  void _setType(StepType t) {
    if (t == _s.type) return;
    final d = ActionStep.create(t);
    setState(() {
      _s = _s.copyWith(type: t, durationMs: d.durationMs);
      // 끝 좌표가 필요한 타입으로 바뀌었는데 시작=끝이면 기본값을 넣는다.
      if (t.hasEnd && _s.x == _s.x2 && _s.y == _s.y2) {
        _s = _s.copyWith(x: d.x, y: d.y, x2: d.x2, y2: d.y2);
      }
    });
  }

  Future<void> _pick() async {
    final mode = switch (_s.type) {
      StepType.areaTap => PickMode.area,
      StepType.swipe => PickMode.line,
      _ => PickMode.point,
    };
    final r = await PositionPickerScreen.open(context,
        mode: mode,
        initial: (x: _s.x, y: _s.y, x2: _s.x2, y2: _s.y2),
        otherSteps: widget.otherSteps);
    if (r != null && mounted) {
      setState(() => _s = _s.copyWith(x: r.x, y: r.y, x2: r.x2, y2: r.y2));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = _s.type;
    final endLabel = t == StepType.swipe ? '끝' : '오른쪽 아래';
    final startLabel = t == StepType.areaTap ? '왼쪽 위' : (t == StepType.swipe ? '시작' : '');
    return Padding(
      padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('단계 편집', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final type in StepType.values)
                  ChoiceChip(
                    avatar: Icon(stepIcon(type), size: 18),
                    label: Text(type.label),
                    selected: t == type,
                    onSelected: (_) => _setType(type),
                  ),
              ],
            ),
            if (t.hasPoint) ...[
              const SizedBox(height: 20),
              Row(
                children: [
                  Text('위치 (화면 비율 %)',
                      style: Theme.of(context).textTheme.titleSmall),
                  const Spacer(),
                  FilledButton.tonalIcon(
                    onPressed: _pick,
                    icon: const Icon(Icons.ads_click),
                    label: const Text('화면에서 지정'),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Wrap(spacing: 12, runSpacing: 12, children: [
                _pct('$startLabel X'.trim(), _s.x, (v) => _s = _s.copyWith(x: v)),
                _pct('$startLabel Y'.trim(), _s.y, (v) => _s = _s.copyWith(y: v)),
                if (t.hasEnd) ...[
                  _pct('$endLabel X', _s.x2, (v) => _s = _s.copyWith(x2: v)),
                  _pct('$endLabel Y', _s.y2, (v) => _s = _s.copyWith(y2: v)),
                ],
              ]),
            ],
            const SizedBox(height: 20),
            Text('타이밍', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 12),
            Wrap(spacing: 12, runSpacing: 12, children: [
              NumField(
                label: t.durationLabel,
                value: _s.durationMs,
                min: 1,
                max: 60000,
                onChanged: (v) =>
                    setState(() => _s = _s.copyWith(durationMs: v.toInt())),
              ),
              NumField(
                label: '다음 단계까지 (ms)',
                value: _s.delayAfterMs,
                max: 3600000,
                onChanged: (v) =>
                    setState(() => _s = _s.copyWith(delayAfterMs: v.toInt())),
              ),
              NumField(
                label: '이 단계 반복 횟수',
                value: _s.repeat,
                min: 1,
                max: 100000,
                onChanged: (v) =>
                    setState(() => _s = _s.copyWith(repeat: v.toInt())),
              ),
            ]),
            const SizedBox(height: 16),
            TextField(
              controller: _label,
              decoration: const InputDecoration(
                labelText: '메모 (선택)',
                border: OutlineInputBorder(),
                isDense: true,
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () => Navigator.pop(
                    context, _s.copyWith(label: _label.text.trim())),
                child: const Text('저장'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _pct(String label, double v, void Function(double) set) => NumField(
        key: ValueKey(label),
        label: label,
        value: double.parse((v * 100).toStringAsFixed(1)),
        decimal: true,
        max: 100,
        width: 120,
        onChanged: (n) => setState(() => set(n.toDouble() / 100)),
      );
}

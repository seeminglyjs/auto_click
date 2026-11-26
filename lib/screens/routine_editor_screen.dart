import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/action_step.dart';
import '../models/routine.dart';
import '../services/native_bridge.dart';
import '../services/routine_store.dart';
import '../services/run_actions.dart';
import '../utils/ids.dart';
import '../widgets/num_field.dart';
import '../widgets/step_icon.dart';
import 'step_editor_sheet.dart';

/// 루틴(프리셋) 편집 화면. 모든 변경은 즉시 저장된다.
class RoutineEditorScreen extends StatefulWidget {
  const RoutineEditorScreen({super.key, required this.routineId});

  final String routineId;

  @override
  State<RoutineEditorScreen> createState() => _RoutineEditorScreenState();
}

class _RoutineEditorScreenState extends State<RoutineEditorScreen> {
  late final TextEditingController _name = TextEditingController(
      text: routineStore.byId(widget.routineId)?.name ?? '');

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Routine? get _r => routineStore.byId(widget.routineId);

  void _save(Routine r) => routineStore.upsert(r);

  void _setSteps(List<ActionStep> steps) {
    final r = _r;
    if (r != null) _save(r.copyWith(steps: steps));
  }

  Future<void> _addStep() async {
    final r = _r;
    if (r == null) return;
    final type = await showModalBottomSheet<StepType>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final t in StepType.values)
              ListTile(
                leading: Icon(stepIcon(t)),
                title: Text(t.label),
                subtitle: Text(_typeHelp(t)),
                onTap: () => Navigator.pop(ctx, t),
              ),
          ],
        ),
      ),
    );
    if (type == null || !mounted) return;
    final edited = await StepEditorSheet.open(context, ActionStep.create(type),
        otherSteps: r.steps);
    if (edited == null) return;
    final cur = _r;
    if (cur != null) _setSteps([...cur.steps, edited]);
  }

  String _typeHelp(StepType t) => switch (t) {
        StepType.tap => '한 지점을 누릅니다',
        StepType.doubleTap => '한 지점을 빠르게 두 번 누릅니다',
        StepType.longPress => '한 지점을 지정한 시간만큼 누르고 있습니다',
        StepType.areaTap => '사각형 영역 안 무작위 지점을 누릅니다',
        StepType.swipe => '시작점에서 끝점까지 밀어냅니다',
        StepType.wait => '아무것도 하지 않고 기다립니다',
      };

  Future<void> _editStep(int i) async {
    final r = _r;
    if (r == null) return;
    final others = [...r.steps]..removeAt(i);
    final edited =
        await StepEditorSheet.open(context, r.steps[i], otherSteps: others);
    if (edited == null) return;
    final cur = _r;
    if (cur == null) return;
    final idx = cur.steps.indexWhere((s) => s.id == edited.id);
    if (idx < 0) return;
    _setSteps([...cur.steps]..[idx] = edited);
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: routineStore,
      builder: (context, _) {
        final r = _r;
        if (r == null) {
          return const Scaffold(body: Center(child: Text('삭제된 루틴입니다.')));
        }
        return Scaffold(
          appBar: AppBar(
            title: const Text('루틴 편집'),
            actions: [
              IconButton(
                tooltip: 'JSON 복사 (공유/백업)',
                icon: const Icon(Icons.ios_share),
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(
                      text: RoutineStore.encode(r.toJson())));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                        content: Text('루틴 JSON 을 클립보드에 복사했습니다.')));
                  }
                },
              ),
            ],
          ),
          body: ReorderableListView.builder(
            buildDefaultDragHandles: false,
            padding: const EdgeInsets.only(bottom: 24),
            header: _header(r),
            footer: Padding(
              padding: const EdgeInsets.all(16),
              child: OutlinedButton.icon(
                onPressed: _addStep,
                icon: const Icon(Icons.add),
                label: const Text('단계 추가'),
              ),
            ),
            itemCount: r.steps.length,
            onReorderItem: (from, to) {
              final list = [...r.steps];
              list.insert(to, list.removeAt(from));
              _setSteps(list);
            },
            itemBuilder: (context, i) => _stepTile(r, i),
          ),
          bottomNavigationBar: _bottomBar(r),
        );
      },
    );
  }

  Widget _header(Routine r) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            controller: _name,
            decoration: const InputDecoration(
              labelText: '루틴 이름',
              border: OutlineInputBorder(),
            ),
            onChanged: (v) {
              final cur = _r;
              if (cur != null) _save(cur.copyWith(name: v));
            },
          ),
          const SizedBox(height: 16),
          Card.outlined(
            margin: EdgeInsets.zero,
            child: ExpansionTile(
              shape: const Border(),
              leading: const Icon(Icons.tune),
              title: const Text('반복 · 랜덤 설정'),
              subtitle: Text(
                  '${r.loopSummary} · 시작 지연 ${r.startDelayMs}ms'
                  '${r.maxRunSeconds > 0 ? ' · 최대 ${r.maxRunSeconds}초' : ''}'),
              childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              children: [
                Wrap(
                  spacing: 12,
                  runSpacing: 16,
                  children: [
                    NumField(
                      label: '루프 횟수',
                      helper: '0 = 무한 반복',
                      value: r.loopCount,
                      onChanged: (v) =>
                          _save(r.copyWith(loopCount: v.toInt())),
                    ),
                    NumField(
                      label: '루프 간격 (ms)',
                      value: r.loopIntervalMs,
                      onChanged: (v) =>
                          _save(r.copyWith(loopIntervalMs: v.toInt())),
                    ),
                    NumField(
                      label: '시작 지연 (ms)',
                      helper: '앱 전환할 시간',
                      value: r.startDelayMs,
                      onChanged: (v) =>
                          _save(r.copyWith(startDelayMs: v.toInt())),
                    ),
                    NumField(
                      label: '최대 실행 (초)',
                      helper: '0 = 제한 없음',
                      value: r.maxRunSeconds,
                      onChanged: (v) =>
                          _save(r.copyWith(maxRunSeconds: v.toInt())),
                    ),
                    NumField(
                      label: '좌표 흔들기 (±px)',
                      helper: '사람처럼 약간씩 빗나감',
                      value: r.randomOffsetPx,
                      max: 500,
                      onChanged: (v) =>
                          _save(r.copyWith(randomOffsetPx: v.toInt())),
                    ),
                    NumField(
                      label: '랜덤 추가 대기 (ms)',
                      helper: '매 단계 0~n ms 추가',
                      value: r.randomDelayMs,
                      onChanged: (v) =>
                          _save(r.copyWith(randomDelayMs: v.toInt())),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              Text('단계 ${r.steps.length}개', style: theme.textTheme.titleMedium),
              const Spacer(),
              if (NativeBridge.isSupported)
                Text('패널로 열면 실제 화면 위에서 추가',
                    style: theme.textTheme.bodySmall),
            ],
          ),
          if (r.steps.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text(
                '아직 단계가 없습니다. 아래 “단계 추가” 를 누르세요.',
                style: theme.textTheme.bodyMedium
                    ?.copyWith(color: theme.colorScheme.outline),
              ),
            ),
        ],
      ),
    );
  }

  Widget _stepTile(Routine r, int i) {
    final s = r.steps[i];
    return ListTile(
      key: ValueKey(s.id),
      contentPadding: const EdgeInsets.only(left: 16, right: 4),
      leading: CircleAvatar(
        child: Stack(
          alignment: Alignment.center,
          children: [
            Icon(stepIcon(s.type), size: 20),
            Positioned(
              right: -2,
              bottom: -2,
              child: Text('${i + 1}',
                  style: const TextStyle(
                      fontSize: 10, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      ),
      title: Text(s.label.isEmpty ? s.type.label : '${s.type.label} · ${s.label}'),
      subtitle: Text(s.summary),
      onTap: () => _editStep(i),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            tooltip: '단계 복제',
            icon: const Icon(Icons.copy, size: 20),
            onPressed: () => _setSteps(
                [...r.steps]..insert(i + 1, s.copyWith(id: newId()))),
          ),
          IconButton(
            tooltip: '삭제',
            icon: const Icon(Icons.delete_outline, size: 20),
            onPressed: () => _setSteps([...r.steps]..removeAt(i)),
          ),
          ReorderableDragStartListener(
            index: i,
            child: const Padding(
              padding: EdgeInsets.all(8),
              child: Icon(Icons.drag_handle),
            ),
          ),
        ],
      ),
    );
  }

  Widget _bottomBar(Routine r) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () => openSandbox(context, r.id),
                icon: const Icon(Icons.science_outlined),
                label: const Text('테스트 패드'),
              ),
            ),
            if (NativeBridge.isSupported) ...[
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.tonalIcon(
                  onPressed: () => openOverlay(context, r),
                  icon: const Icon(Icons.picture_in_picture_alt),
                  label: const Text('패널로 열기'),
                ),
              ),
            ],
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton.icon(
                onPressed: () => runRoutine(context, r),
                icon: const Icon(Icons.play_arrow),
                label: const Text('실행'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

import 'package:auto_click/models/action_step.dart';
import 'package:auto_click/models/routine.dart';
import 'package:auto_click/models/templates.dart';
import 'package:auto_click/services/routine_store.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('routine JSON round trip keeps every field', () {
    final r = Routine(
      id: 'r1',
      name: '테스트',
      loopCount: 0,
      randomOffsetPx: 5,
      steps: [
        ActionStep.create(StepType.swipe).copyWith(id: 's1', repeat: 3),
        ActionStep.create(StepType.areaTap).copyWith(id: 's2', label: 'A'),
      ],
    );
    final back = Routine.fromJson(r.toJson());
    expect(back.toJson(), r.toJson());
  });

  test('import accepts a single routine or a list', () {
    final one = RoutineStore.encode(routineTemplates[1].build().toJson());
    final many = RoutineStore.encode(
        [for (final t in routineTemplates) t.build().toJson()]);
    expect(RoutineStore.decodeRoutines(one), hasLength(1));
    expect(RoutineStore.decodeRoutines(many), hasLength(routineTemplates.length));
  });

  test('coordinates are clamped to the screen', () {
    final s = ActionStep.fromJson({'type': 'tap', 'x': 1.7, 'y': -2});
    expect(s.x, 1.0);
    expect(s.y, 0.0);
  });

  test('duplicate issues new ids', () {
    final r = routineTemplates.last.build();
    final d = r.duplicate();
    expect(d.id, isNot(r.id));
    expect(d.steps.first.id, isNot(r.steps.first.id));
    expect(d.steps.length, r.steps.length);
  });
}

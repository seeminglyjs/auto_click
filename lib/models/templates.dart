import '../utils/ids.dart';
import 'action_step.dart';
import 'routine.dart';

/// 새 루틴을 만들 때 고를 수 있는 기본 프리셋 템플릿.
class RoutineTemplate {
  const RoutineTemplate(this.title, this.description, this.build);

  final String title;
  final String description;
  final Routine Function() build;
}

final routineTemplates = <RoutineTemplate>[
  RoutineTemplate('빈 루틴', '단계를 직접 추가합니다.',
      () => Routine(id: newId(), name: '새 루틴')),
  RoutineTemplate(
    '한 지점 연타',
    '화면 중앙을 0.1초 간격으로 계속 탭합니다.',
    () => Routine(
      id: newId(),
      name: '한 지점 연타',
      loopCount: 0,
      loopIntervalMs: 0,
      steps: [ActionStep.create(StepType.tap).copyWith(delayAfterMs: 100)],
    ),
  ),
  RoutineTemplate(
    '영역 랜덤 클릭',
    '지정한 사각형 안의 무작위 지점을 랜덤 간격으로 50회 탭합니다.',
    () => Routine(
      id: newId(),
      name: '영역 랜덤 클릭',
      loopCount: 50,
      loopIntervalMs: 0,
      randomDelayMs: 400,
      steps: [ActionStep.create(StepType.areaTap).copyWith(delayAfterMs: 600)],
    ),
  ),
  RoutineTemplate(
    '피드 넘기기',
    '위로 스와이프 후 3초 대기를 무한 반복합니다.',
    () => Routine(
      id: newId(),
      name: '피드 넘기기',
      loopCount: 0,
      loopIntervalMs: 0,
      steps: [
        ActionStep.create(StepType.swipe).copyWith(delayAfterMs: 0),
        ActionStep.create(StepType.wait).copyWith(durationMs: 3000),
      ],
    ),
  ),
  RoutineTemplate(
    '탭 → 대기 → 스와이프',
    '여러 동작을 조합한 예시 루틴입니다.',
    () => Routine(
      id: newId(),
      name: '조합 루틴 예시',
      loopCount: 5,
      steps: [
        ActionStep.create(StepType.tap)
            .copyWith(x: 0.5, y: 0.35, label: '버튼 A'),
        ActionStep.create(StepType.wait).copyWith(durationMs: 800),
        ActionStep.create(StepType.doubleTap).copyWith(x: 0.5, y: 0.55),
        ActionStep.create(StepType.swipe)
            .copyWith(x: 0.8, y: 0.5, x2: 0.2, y2: 0.5),
      ],
    ),
  ),
];

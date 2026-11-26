import '../utils/ids.dart';
import 'action_step.dart';

/// 여러 단계를 순서대로 실행하는 루틴 = 저장 가능한 프리셋.
class Routine {
  const Routine({
    required this.id,
    required this.name,
    this.steps = const [],
    this.loopCount = 1,
    this.loopIntervalMs = 500,
    this.startDelayMs = 1500,
    this.maxRunSeconds = 0,
    this.randomOffsetPx = 0,
    this.randomDelayMs = 0,
    this.updatedAt = 0,
  });

  final String id;
  final String name;
  final List<ActionStep> steps;

  /// 전체 루틴 반복 횟수. 0이면 정지할 때까지 무한 반복.
  final int loopCount;

  /// 루프 한 바퀴가 끝난 뒤 다음 바퀴까지 대기.
  final int loopIntervalMs;

  /// 실행 버튼을 누른 뒤 실제 시작까지 대기 (앱 전환 시간).
  final int startDelayMs;

  /// 최대 실행 시간(초). 0이면 제한 없음.
  final int maxRunSeconds;

  /// 매 터치마다 좌표에 더해지는 무작위 오프셋 (±px).
  final int randomOffsetPx;

  /// 매 단계 대기 시간에 더해지는 무작위 추가 시간 (0 ~ n ms).
  final int randomDelayMs;

  final int updatedAt;

  Routine copyWith({
    String? id,
    String? name,
    List<ActionStep>? steps,
    int? loopCount,
    int? loopIntervalMs,
    int? startDelayMs,
    int? maxRunSeconds,
    int? randomOffsetPx,
    int? randomDelayMs,
    int? updatedAt,
  }) =>
      Routine(
        id: id ?? this.id,
        name: name ?? this.name,
        steps: steps ?? this.steps,
        loopCount: loopCount ?? this.loopCount,
        loopIntervalMs: loopIntervalMs ?? this.loopIntervalMs,
        startDelayMs: startDelayMs ?? this.startDelayMs,
        maxRunSeconds: maxRunSeconds ?? this.maxRunSeconds,
        randomOffsetPx: randomOffsetPx ?? this.randomOffsetPx,
        randomDelayMs: randomDelayMs ?? this.randomDelayMs,
        updatedAt: updatedAt ?? this.updatedAt,
      );

  /// 새 ID로 복제 (단계 ID도 새로 발급).
  Routine duplicate() => copyWith(
        id: newId(),
        name: '$name (복사)',
        steps: [for (final s in steps) s.copyWith(id: newId())],
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      );

  String get loopSummary => loopCount == 0 ? '무한 반복' : '$loopCount회 반복';

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'steps': [for (final s in steps) s.toJson()],
        'loopCount': loopCount,
        'loopIntervalMs': loopIntervalMs,
        'startDelayMs': startDelayMs,
        'maxRunSeconds': maxRunSeconds,
        'randomOffsetPx': randomOffsetPx,
        'randomDelayMs': randomDelayMs,
        'updatedAt': updatedAt,
      };

  factory Routine.fromJson(Map<String, dynamic> j) {
    int i(String k, int def) => (j[k] as num?)?.toInt() ?? def;
    return Routine(
      id: (j['id'] as String?) ?? newId(),
      name: (j['name'] as String?) ?? '이름 없는 루틴',
      steps: [
        for (final s in (j['steps'] as List?) ?? const [])
          ActionStep.fromJson(Map<String, dynamic>.from(s as Map)),
      ],
      loopCount: i('loopCount', 1),
      loopIntervalMs: i('loopIntervalMs', 500),
      startDelayMs: i('startDelayMs', 1500),
      maxRunSeconds: i('maxRunSeconds', 0),
      randomOffsetPx: i('randomOffsetPx', 0),
      randomDelayMs: i('randomDelayMs', 0),
      updatedAt: i('updatedAt', 0),
    );
  }
}

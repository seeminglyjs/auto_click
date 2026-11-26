import '../utils/ids.dart';

/// 루틴을 구성하는 동작 종류.
///
/// 좌표는 모두 화면 크기 대비 0.0 ~ 1.0 비율(정규화 좌표)로 저장한다.
/// 그래서 해상도가 다른 기기에서도 같은 프리셋을 쓸 수 있다.
enum StepType {
  tap('tap', '탭'),
  doubleTap('doubleTap', '더블 탭'),
  longPress('longPress', '길게 누르기'),
  areaTap('areaTap', '영역 랜덤 탭'),
  swipe('swipe', '스와이프'),
  wait('wait', '대기');

  const StepType(this.key, this.label);

  final String key;
  final String label;

  static StepType fromKey(String? key) =>
      values.firstWhere((e) => e.key == key, orElse: () => StepType.tap);

  bool get hasPoint => this != StepType.wait;

  /// 끝 좌표(x2, y2)를 사용하는지 여부.
  bool get hasEnd => this == StepType.swipe || this == StepType.areaTap;

  String get durationLabel => switch (this) {
        StepType.tap || StepType.doubleTap || StepType.areaTap => '누르는 시간 (ms)',
        StepType.longPress => '누르고 있는 시간 (ms)',
        StepType.swipe => '스와이프 시간 (ms)',
        StepType.wait => '대기 시간 (ms)',
      };
}

class ActionStep {
  const ActionStep({
    required this.id,
    required this.type,
    this.x = 0.5,
    this.y = 0.5,
    this.x2 = 0.5,
    this.y2 = 0.5,
    this.durationMs = 40,
    this.delayAfterMs = 500,
    this.repeat = 1,
    this.label = '',
  });

  /// 타입별 기본값으로 새 단계를 만든다.
  factory ActionStep.create(StepType type) => switch (type) {
        StepType.tap => ActionStep(id: newId(), type: type),
        StepType.doubleTap => ActionStep(id: newId(), type: type),
        StepType.longPress =>
          ActionStep(id: newId(), type: type, durationMs: 800),
        StepType.areaTap => ActionStep(
            id: newId(), type: type, x: 0.35, y: 0.4, x2: 0.65, y2: 0.6),
        StepType.swipe => ActionStep(
            id: newId(),
            type: type,
            x: 0.5,
            y: 0.7,
            x2: 0.5,
            y2: 0.3,
            durationMs: 300),
        StepType.wait => ActionStep(
            id: newId(), type: type, durationMs: 1000, delayAfterMs: 0),
      };

  final String id;
  final StepType type;

  /// 시작 좌표 (영역 탭이면 좌상단).
  final double x;
  final double y;

  /// 끝 좌표 (스와이프 끝 / 영역 우하단).
  final double x2;
  final double y2;

  /// 누르는 시간 · 스와이프 시간 · 대기 시간.
  final int durationMs;

  /// 이 단계를 수행한 뒤 다음 단계까지 기다리는 시간.
  final int delayAfterMs;

  /// 이 단계를 연속으로 반복하는 횟수.
  final int repeat;

  /// 사용자가 붙이는 메모.
  final String label;

  ActionStep copyWith({
    String? id,
    StepType? type,
    double? x,
    double? y,
    double? x2,
    double? y2,
    int? durationMs,
    int? delayAfterMs,
    int? repeat,
    String? label,
  }) =>
      ActionStep(
        id: id ?? this.id,
        type: type ?? this.type,
        x: x ?? this.x,
        y: y ?? this.y,
        x2: x2 ?? this.x2,
        y2: y2 ?? this.y2,
        durationMs: durationMs ?? this.durationMs,
        delayAfterMs: delayAfterMs ?? this.delayAfterMs,
        repeat: repeat ?? this.repeat,
        label: label ?? this.label,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'type': type.key,
        'x': x,
        'y': y,
        'x2': x2,
        'y2': y2,
        'durationMs': durationMs,
        'delayAfterMs': delayAfterMs,
        'repeat': repeat,
        'label': label,
      };

  factory ActionStep.fromJson(Map<String, dynamic> j) {
    double d(String k, double def) => (j[k] as num?)?.toDouble() ?? def;
    int i(String k, int def) => (j[k] as num?)?.toInt() ?? def;
    return ActionStep(
      id: (j['id'] as String?) ?? newId(),
      type: StepType.fromKey(j['type'] as String?),
      x: d('x', 0.5).clamp(0.0, 1.0),
      y: d('y', 0.5).clamp(0.0, 1.0),
      x2: d('x2', 0.5).clamp(0.0, 1.0),
      y2: d('y2', 0.5).clamp(0.0, 1.0),
      durationMs: i('durationMs', 40),
      delayAfterMs: i('delayAfterMs', 500),
      repeat: i('repeat', 1),
      label: (j['label'] as String?) ?? '',
    );
  }

  String get summary {
    String p(double v) => '${(v * 100).toStringAsFixed(1)}%';
    final base = switch (type) {
      StepType.tap || StepType.doubleTap => '(${p(x)}, ${p(y)})',
      StepType.longPress => '(${p(x)}, ${p(y)}) · $durationMs ms',
      StepType.areaTap => '(${p(x)}, ${p(y)}) ~ (${p(x2)}, ${p(y2)})',
      StepType.swipe =>
        '(${p(x)}, ${p(y)}) → (${p(x2)}, ${p(y2)}) · $durationMs ms',
      StepType.wait => '$durationMs ms 대기',
    };
    final rep = repeat > 1 ? ' ×$repeat' : '';
    final after = delayAfterMs > 0 ? ' · 다음까지 $delayAfterMs ms' : '';
    return '$base$rep$after';
  }
}

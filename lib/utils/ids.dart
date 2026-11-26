import 'dart:math';

final _rng = Random();

/// 짧고 충돌 가능성이 낮은 ID 생성기.
String newId() =>
    '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}'
    '${_rng.nextInt(0x7fffffff).toRadixString(36)}';

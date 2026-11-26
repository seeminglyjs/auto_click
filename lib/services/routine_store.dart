import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/routine.dart';
import '../models/templates.dart';
import 'native_bridge.dart';

/// 프리셋(루틴) 저장소. shared_preferences 에 JSON 으로 보관한다.
class RoutineStore extends ChangeNotifier {
  static const _key = 'routines_v1';
  static const _seededKey = 'seeded_v1';

  final List<Routine> _items = [];
  SharedPreferences? _prefs;

  List<Routine> get routines => List.unmodifiable(_items);

  Routine? byId(String id) {
    for (final r in _items) {
      if (r.id == id) return r;
    }
    return null;
  }

  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();
    _readFromDisk();
    if (_items.isEmpty && !(_prefs!.getBool(_seededKey) ?? false)) {
      // 첫 실행이면 예시 루틴 하나를 넣어둔다.
      _items.add(routineTemplates.last.build());
      await _prefs!.setBool(_seededKey, true);
      await _persist();
    }
    notifyListeners();
  }

  void _readFromDisk() {
    final raw = _prefs!.getString(_key);
    _items.clear();
    if (raw != null) {
      try {
        _items.addAll(decodeRoutines(raw));
      } catch (e) {
        debugPrint('routine load failed: $e');
      }
    }
  }

  /// 디스크에서 다시 읽는다. Android 오버레이 패널이 같은 저장소에 직접
  /// 저장하므로, 앱으로 돌아올 때 호출해 패널에서 한 변경을 반영한다.
  Future<void> reload() async {
    final prefs = _prefs;
    if (prefs == null) return;
    await prefs.reload();
    _readFromDisk();
    notifyListeners();
  }

  /// 추가 또는 갱신. [fromNative] 는 Android 오버레이에서 넘어온 변경.
  Future<void> upsert(Routine routine, {bool fromNative = false}) async {
    final r = fromNative
        ? routine
        : routine.copyWith(updatedAt: DateTime.now().millisecondsSinceEpoch);
    final idx = _items.indexWhere((e) => e.id == r.id);
    if (idx >= 0) {
      _items[idx] = r;
    } else {
      _items.insert(0, r);
    }
    notifyListeners();
    await _persist();
    if (!fromNative) await NativeBridge.instance.syncIfOverlay(r);
  }

  Future<void> delete(String id) async {
    _items.removeWhere((e) => e.id == id);
    notifyListeners();
    await _persist();
    await NativeBridge.instance.removeFromOverlay(id);
  }

  Future<Routine> duplicate(Routine r) async {
    final copy = r.duplicate();
    await upsert(copy);
    return copy;
  }

  /// JSON(루틴 하나 또는 배열)을 가져온다. 가져온 루틴 수를 반환.
  Future<int> importJson(String text) async {
    final list = decodeRoutines(text);
    for (final r in list) {
      // 같은 ID가 이미 있으면 복제본으로 추가해 기존 것을 덮어쓰지 않는다.
      await upsert(byId(r.id) == null ? r : r.duplicate());
    }
    return list.length;
  }

  static List<Routine> decodeRoutines(String text) {
    final decoded = jsonDecode(text);
    final list = decoded is List ? decoded : [decoded];
    return [
      for (final e in list) Routine.fromJson(Map<String, dynamic>.from(e as Map)),
    ];
  }

  static String encode(Object value) =>
      const JsonEncoder.withIndent('  ').convert(value);

  Future<void> _persist() async {
    await _prefs?.setString(
        _key, jsonEncode([for (final r in _items) r.toJson()]));
  }
}

final routineStore = RoutineStore();

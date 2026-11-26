import 'package:flutter/material.dart';

import '../models/routine.dart';
import '../utils/ids.dart';
import 'routine_store.dart';
import '../screens/sandbox_screen.dart';
import 'native_bridge.dart';

/// 화면들이 공통으로 쓰는 실행 동작.

void _snack(BuildContext context, String msg) => ScaffoldMessenger.of(context)
  ..hideCurrentSnackBar()
  ..showSnackBar(SnackBar(content: Text(msg)));

/// Android 접근성 권한이 없으면 안내 후 설정으로 보낸다. 권한이 있으면 true.
Future<bool> ensureAccessibility(BuildContext context) async {
  final bridge = NativeBridge.instance;
  if (await bridge.refreshServiceStatus()) return true;
  if (!context.mounted) return false;
  final go = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      icon: const Icon(Icons.accessibility_new),
      title: const Text('접근성 권한이 필요해요'),
      content: const Text(
        '다른 앱 위에서 탭·스와이프를 하려면 접근성 서비스를 켜야 합니다.\n\n'
        '설정 › 접근성 › 설치된 앱 › Auto Click › 사용\n\n'
        '“제한된 설정” 이라며 켜지지 않으면: 설정 › 애플리케이션 › Auto Click › '
        '오른쪽 위 ⋮ › “제한된 설정 허용” 후 다시 시도하세요.\n\n'
        '이 앱은 화면 내용을 읽지 않고, 지정한 위치에 터치만 보냅니다.',
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('나중에')),
        TextButton(
            onPressed: () {
              Navigator.pop(ctx, false);
              bridge.openAppSettings();
            },
            child: const Text('앱 정보')),
        FilledButton.icon(
            onPressed: () => Navigator.pop(ctx, true),
            icon: const Icon(Icons.open_in_new),
            label: const Text('권한 설정하기')),
      ],
    ),
  );
  if (go == true) await bridge.openAccessibilitySettings();
  return false;
}

/// 루틴 실행: Android 는 다른 앱 위에서, 그 외는 테스트 패드에서.
Future<void> runRoutine(BuildContext context, Routine r) async {
  if (r.steps.isEmpty) {
    _snack(context, '단계를 먼저 추가하세요.');
    return;
  }
  if (!NativeBridge.isSupported) {
    openSandbox(context, r.id, autoStart: true);
    return;
  }
  if (!await ensureAccessibility(context)) return;
  final ok = await NativeBridge.instance.runRoutine(r);
  if (!ok && context.mounted) _snack(context, '실행하지 못했습니다. 접근성 서비스를 확인하세요.');
}

/// Android 플로팅 패널을 띄워 다른 앱 위에서 위치를 지정한다.
Future<void> openOverlay(BuildContext context, Routine r) async {
  if (!await ensureAccessibility(context)) return;
  final ok = await NativeBridge.instance.showOverlay(r);
  if (!ok && context.mounted) _snack(context, '오버레이를 띄우지 못했습니다.');
}

void openSandbox(BuildContext context, String routineId,
    {bool autoStart = false}) {
  Navigator.of(context).push(MaterialPageRoute(
    fullscreenDialog: true,
    builder: (_) => SandboxScreen(routineId: routineId, autoStart: autoStart),
  ));
}

/// 홈의 “플로팅 패널 열기”: 가장 최근 루틴으로 패널을 연다 (없으면 새로 만든다).
Future<void> openPanelQuick(BuildContext context) async {
  final list = routineStore.routines;
  var r = list.isNotEmpty ? list.first : null;
  if (r == null) {
    r = Routine(id: newId(), name: '새 루틴', loopCount: 0, loopIntervalMs: 0);
    await routineStore.upsert(r);
  }
  if (context.mounted) await openOverlay(context, r);
}

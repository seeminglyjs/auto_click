import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'services/native_bridge.dart';
import 'services/routine_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await routineStore.load();
  NativeBridge.instance.onRoutineFromNative =
      (r) => routineStore.upsert(r, fromNative: true);
  runApp(const AutoClickApp());
}

class AutoClickApp extends StatefulWidget {
  const AutoClickApp({super.key});

  @override
  State<AutoClickApp> createState() => _AutoClickAppState();
}

class _AutoClickAppState extends State<AutoClickApp>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _syncNative();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _syncNative();
  }

  /// 앱으로 돌아올 때 권한 상태와 오버레이에서 수정된 루틴을 가져온다.
  Future<void> _syncNative() async {
    if (!NativeBridge.isSupported) return;
    final bridge = NativeBridge.instance;
    await bridge.refreshServiceStatus();
    final changed = await bridge.takeOverlayRoutine();
    if (changed != null) await routineStore.upsert(changed, fromNative: true);
  }

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF00897B);
    return MaterialApp(
      title: 'Auto Click',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: seed, useMaterial3: true),
      darkTheme: ThemeData(
          colorSchemeSeed: seed,
          brightness: Brightness.dark,
          useMaterial3: true),
      home: const HomeScreen(),
    );
  }
}

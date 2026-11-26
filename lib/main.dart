import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'screens/guide_screen.dart';
import 'screens/home_screen.dart';
import 'services/native_bridge.dart';
import 'services/routine_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await routineStore.load();
  final prefs = await SharedPreferences.getInstance();
  final onboarded = prefs.getBool(onboardedKey) ?? false;
  NativeBridge.instance.onRoutineFromNative =
      (r) => routineStore.upsert(r, fromNative: true);
  runApp(AutoClickApp(showGuide: !onboarded));
}

class AutoClickApp extends StatefulWidget {
  const AutoClickApp({super.key, this.showGuide = false});

  final bool showGuide;

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

  /// 앱으로 돌아올 때: 권한 상태 갱신 + 패널에서 저장한 프리셋 다시 읽기.
  Future<void> _syncNative() async {
    if (!NativeBridge.isSupported) return;
    await NativeBridge.instance.refreshServiceStatus();
    await routineStore.reload();
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
      home: widget.showGuide
          ? const GuideScreen(firstRun: true)
          : const HomeScreen(),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/routine.dart';
import '../models/templates.dart';
import '../services/native_bridge.dart';
import '../services/routine_store.dart';
import '../services/run_actions.dart';
import 'guide_screen.dart';
import 'routine_editor_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  void _openEditor(BuildContext context, String id) =>
      Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => RoutineEditorScreen(routineId: id)));

  Future<void> _create(BuildContext context) async {
    final tpl = await showModalBottomSheet<RoutineTemplate>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text('새 루틴 만들기',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
            ),
            for (final t in routineTemplates)
              ListTile(
                leading: const Icon(Icons.auto_awesome_motion),
                title: Text(t.title),
                subtitle: Text(t.description),
                onTap: () => Navigator.pop(ctx, t),
              ),
          ],
        ),
      ),
    );
    if (tpl == null) return;
    final r = tpl.build();
    await routineStore.upsert(r);
    if (context.mounted) _openEditor(context, r.id);
  }

  Future<void> _import(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim() ?? '';
    if (text.isEmpty) {
      messenger.showSnackBar(
          const SnackBar(content: Text('클립보드가 비어 있습니다.')));
      return;
    }
    try {
      final n = await routineStore.importJson(text);
      messenger.showSnackBar(SnackBar(content: Text('루틴 $n개를 가져왔습니다.')));
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('올바른 루틴 JSON 이 아닙니다.')));
    }
  }

  Future<void> _exportAll(BuildContext context) async {
    await Clipboard.setData(ClipboardData(
        text: RoutineStore.encode(
            [for (final r in routineStore.routines) r.toJson()])));
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('전체 프리셋 JSON 을 클립보드에 복사했습니다.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Auto Click'),
        actions: [
          IconButton(
            tooltip: '사용 방법',
            icon: const Icon(Icons.help_outline),
            onPressed: () => openGuide(context),
          ),
          if (NativeBridge.isSupported)
            IconButton(
              tooltip: '권한 설정',
              icon: const Icon(Icons.admin_panel_settings_outlined),
              onPressed: () => showPermissionSheet(context),
            ),
          PopupMenuButton<String>(
            onSelected: (v) => switch (v) {
              'import' => _import(context),
              'export' => _exportAll(context),
              _ => null,
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'import',
                child: ListTile(
                  leading: Icon(Icons.content_paste_go),
                  title: Text('클립보드에서 가져오기'),
                ),
              ),
              PopupMenuItem(
                value: 'export',
                child: ListTile(
                  leading: Icon(Icons.backup_outlined),
                  title: Text('전체 프리셋 내보내기'),
                ),
              ),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _create(context),
        icon: const Icon(Icons.add),
        label: const Text('새 루틴'),
      ),
      body: ListenableBuilder(
        listenable: routineStore,
        builder: (context, _) {
          final list = routineStore.routines;
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
            children: [
              const QuickStartCard(),
              const SizedBox(height: 16),
              Text('프리셋 ${list.length}개',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              if (list.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(32),
                  child: Center(child: Text('“새 루틴” 으로 첫 프리셋을 만들어 보세요.')),
                ),
              for (final r in list)
                _RoutineCard(
                  routine: r,
                  onOpen: () => _openEditor(context, r.id),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _RoutineCard extends StatelessWidget {
  const _RoutineCard({required this.routine, required this.onOpen});

  final Routine routine;
  final VoidCallback onOpen;

  Future<void> _menu(BuildContext context, String v) async {
    switch (v) {
      case 'edit':
        onOpen();
      case 'dup':
        await routineStore.duplicate(routine);
      case 'sandbox':
        openSandbox(context, routine.id);
      case 'copy':
        await Clipboard.setData(
            ClipboardData(text: RoutineStore.encode(routine.toJson())));
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('JSON 을 클립보드에 복사했습니다.')));
        }
      case 'delete':
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('루틴 삭제'),
            content: Text('“${routine.name}” 을(를) 삭제할까요?'),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx, false),
                  child: const Text('취소')),
              FilledButton(
                  onPressed: () => Navigator.pop(ctx, true),
                  child: const Text('삭제')),
            ],
          ),
        );
        if (ok == true) await routineStore.delete(routine.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onOpen,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 4, 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  CircleAvatar(
                    backgroundColor: scheme.primaryContainer,
                    child: Text('${routine.steps.length}',
                        style: TextStyle(color: scheme.onPrimaryContainer)),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(routine.name,
                            style: Theme.of(context).textTheme.titleMedium,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis),
                        Text(
                          '${routine.steps.length}단계 · ${routine.loopSummary}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  PopupMenuButton<String>(
                    onSelected: (v) => _menu(context, v),
                    itemBuilder: (_) => const [
                      PopupMenuItem(value: 'edit', child: Text('앱에서 편집')),
                      PopupMenuItem(value: 'sandbox', child: Text('테스트 패드에서 실행')),
                      PopupMenuItem(value: 'dup', child: Text('복제')),
                      PopupMenuItem(value: 'copy', child: Text('JSON 복사')),
                      PopupMenuItem(value: 'delete', child: Text('삭제')),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  FilledButton.tonalIcon(
                    onPressed: () => runRoutine(context, routine),
                    icon: const Icon(Icons.play_arrow, size: 18),
                    label: const Text('실행'),
                  ),
                  if (NativeBridge.isSupported)
                    OutlinedButton.icon(
                      onPressed: () => openOverlay(context, routine),
                      icon: const Icon(Icons.picture_in_picture_alt, size: 18),
                      label: const Text('이 루틴으로 패널 열기'),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
/// 홈 맨 위 “시작 가이드” 카드. 지금 해야 할 다음 행동을 한 가지씩 보여준다.
class QuickStartCard extends StatelessWidget {
  const QuickStartCard({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    if (!NativeBridge.isSupported) {
      return Card.filled(
        color: scheme.secondaryContainer,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('앱 안에서 루틴 만들기 · 테스트',
                  style: theme.textTheme.titleMedium),
              const SizedBox(height: 6),
              const Text(
                'iOS · 웹은 OS 보안 정책상 다른 앱을 누를 수 없어요. 루틴 작성 · 프리셋 관리와 '
                '테스트 패드 실행은 모두 가능하고, 다른 앱 위 자동 클릭은 Android 에서 지원됩니다.',
              ),
              const SizedBox(height: 10),
              OutlinedButton.icon(
                onPressed: () => openGuide(context),
                icon: const Icon(Icons.help_outline),
                label: const Text('사용 방법 보기'),
              ),
            ],
          ),
        ),
      );
    }
    final bridge = NativeBridge.instance;
    return ValueListenableBuilder<bool>(
      valueListenable: bridge.serviceEnabled,
      builder: (context, enabled, _) => ValueListenableBuilder<bool>(
        valueListenable: bridge.overlayVisible,
        builder: (context, panelOpen, _) => ValueListenableBuilder<bool>(
          valueListenable: bridge.running,
          builder: (context, running, _) {
            return Card.filled(
              color: enabled
                  ? scheme.primaryContainer
                  : scheme.surfaceContainerHigh,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(children: [
                      Text('시작 가이드', style: theme.textTheme.titleMedium),
                      const Spacer(),
                      TextButton.icon(
                        onPressed: () => openGuide(context),
                        icon: const Icon(Icons.help_outline, size: 18),
                        label: const Text('사용 방법'),
                      ),
                    ]),
                    _GuideStep(
                      number: 1,
                      done: enabled,
                      title: '접근성 권한 켜기',
                      desc: enabled ? '켜져 있어요' : '다른 앱을 누르기 위해 필요한 유일한 권한',
                      action: enabled
                          ? null
                          : FilledButton.icon(
                              onPressed: bridge.openAccessibilitySettings,
                              icon: const Icon(Icons.open_in_new, size: 18),
                              label: const Text('권한 설정하기'),
                            ),
                    ),
                    _GuideStep(
                      number: 2,
                      done: panelOpen,
                      title: '플로팅 패널 열기',
                      desc: panelOpen
                          ? (running ? '루틴 실행 중' : '다른 앱 위에 패널이 떠 있어요')
                          : '빈 패널로 새 작업을 시작해요. 저장된 루틴은 아래 목록의 “이 루틴으로 패널 열기”',
                      action: !enabled
                          ? null
                          : panelOpen
                              ? Wrap(spacing: 8, children: [
                                  if (running)
                                    FilledButton(
                                        onPressed: bridge.stopRoutine,
                                        child: const Text('정지')),
                                  OutlinedButton(
                                      onPressed: bridge.hideOverlay,
                                      child: const Text('패널 닫기')),
                                ])
                              : FilledButton.icon(
                                  onPressed: () => openPanelQuick(context),
                                  icon: const Icon(
                                      Icons.picture_in_picture_alt,
                                      size: 18),
                                  label: const Text('플로팅 패널 열기'),
                                ),
                    ),
                    const _GuideStep(
                      number: 3,
                      done: false,
                      title: '누를 앱에서 ＋ 추가 → ▶ 실행',
                      desc: '위치를 찍으면 번호 마커가 생겨요. 마커를 누르면 클릭 주기 · 반복 설정, '
                          '끌면 위치 이동. 저장 버튼을 누르면 이름을 정해 프리셋으로 저장됩니다.',
                    ),
                    if (!enabled)
                      Padding(
                        padding: const EdgeInsets.only(left: 40, top: 4),
                        child: Wrap(
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            Text('“제한된 설정” 때문에 안 켜지면 ',
                                style: theme.textTheme.bodySmall),
                            InkWell(
                              onTap: bridge.openAppSettings,
                              child: Text('앱 정보',
                                  style: theme.textTheme.bodySmall?.copyWith(
                                      color: scheme.primary,
                                      decoration: TextDecoration.underline)),
                            ),
                            Text(' › ⋮ › 제한된 설정 허용',
                                style: theme.textTheme.bodySmall),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _GuideStep extends StatelessWidget {
  const _GuideStep({
    required this.number,
    required this.done,
    required this.title,
    required this.desc,
    this.action,
  });

  final int number;
  final bool done;
  final String title;
  final String desc;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 14,
            backgroundColor: done ? const Color(0xFF2E7D32) : scheme.primary,
            child: done
                ? const Icon(Icons.check, size: 16, color: Colors.white)
                : Text('$number',
                    style: TextStyle(
                        color: scheme.onPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: theme.textTheme.titleSmall
                        ?.copyWith(fontWeight: FontWeight.bold)),
                Text(desc, style: theme.textTheme.bodySmall),
                if (action != null) ...[
                  const SizedBox(height: 8),
                  action!,
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

void openGuide(BuildContext context) => Navigator.of(context)
    .push(MaterialPageRoute(builder: (_) => const GuideScreen()));

/// 언제든 열 수 있는 권한 설정 바로가기 시트.
Future<void> showPermissionSheet(BuildContext context) async {
  final bridge = NativeBridge.instance;
  await bridge.refreshServiceStatus();
  if (!context.mounted) return;
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (ctx) => SafeArea(
      child: ValueListenableBuilder<bool>(
        valueListenable: bridge.serviceEnabled,
        builder: (ctx, enabled, _) => Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(enabled ? Icons.check_circle : Icons.error_outline,
                  color: enabled
                      ? Colors.green
                      : Theme.of(ctx).colorScheme.error),
              title: Text(enabled ? '접근성 서비스: 켜짐' : '접근성 서비스: 꺼짐'),
              subtitle: const Text('다른 앱 위 자동 클릭에 필요한 유일한 권한입니다.'),
            ),
            const Divider(),
            ListTile(
              leading: const Icon(Icons.accessibility_new),
              title: const Text('접근성 권한 설정 바로가기'),
              subtitle: const Text('Auto Click 항목으로 바로 이동합니다 (기기마다 목록이 열릴 수 있음)'),
              trailing: const Icon(Icons.open_in_new),
              onTap: () {
                Navigator.pop(ctx);
                bridge.openAccessibilitySettings();
              },
            ),
            ListTile(
              leading: const Icon(Icons.app_settings_alt),
              title: const Text('앱 정보 열기'),
              subtitle: const Text('⋮ › “제한된 설정 허용” (사이드로딩 설치 시 필요)'),
              trailing: const Icon(Icons.open_in_new),
              onTap: () {
                Navigator.pop(ctx);
                bridge.openAppSettings();
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    ),
  );
}

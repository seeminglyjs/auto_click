import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/native_bridge.dart';
import 'home_screen.dart';

const onboardedKey = 'onboarded_v1';

/// 처음 실행 시(그리고 ? 버튼으로 언제든) 보여주는 사용 방법 안내.
class GuideScreen extends StatefulWidget {
  const GuideScreen({super.key, this.firstRun = false});

  final bool firstRun;

  @override
  State<GuideScreen> createState() => _GuideScreenState();
}

class _GuideScreenState extends State<GuideScreen> {
  final _page = PageController();
  int _index = 0;

  List<Widget> get _pages => NativeBridge.isSupported
      ? const [_IntroPage(), _PermissionPage(), _PanelPage(), _FlowPage()]
      : const [_IntroPage(), _WebFlowPage(), _TestPadPage()];

  Future<void> _finish() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(onboardedKey, true);
    if (!mounted) return;
    if (widget.firstRun) {
      Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const HomeScreen()));
    } else {
      Navigator.of(context).pop();
    }
  }

  @override
  void dispose() {
    _page.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = _pages;
    final last = _index == pages.length - 1;
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: _finish,
                child: Text(widget.firstRun ? '건너뛰기' : '닫기'),
              ),
            ),
            Expanded(
              child: PageView(
                controller: _page,
                onPageChanged: (i) => setState(() => _index = i),
                children: pages,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
              child: Row(
                children: [
                  for (var i = 0; i < pages.length; i++)
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      margin: const EdgeInsets.only(right: 6),
                      width: i == _index ? 22 : 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: i == _index
                            ? scheme.primary
                            : scheme.outlineVariant,
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  const Spacer(),
                  FilledButton(
                    onPressed: last
                        ? _finish
                        : () => _page.nextPage(
                            duration: const Duration(milliseconds: 280),
                            curve: Curves.easeOut),
                    child: Text(last ? '시작하기' : '다음'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── 공통 레이아웃 ─────────────────────────────────────────────────────────────

class _GuidePage extends StatelessWidget {
  const _GuidePage({
    required this.icon,
    required this.step,
    required this.title,
    required this.body,
    this.children = const [],
  });

  final IconData icon;
  final String? step;
  final String title;
  final String body;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: CircleAvatar(
            radius: 36,
            backgroundColor: theme.colorScheme.primaryContainer,
            child: Icon(icon, size: 36, color: theme.colorScheme.primary),
          ),
        ),
        const SizedBox(height: 20),
        if (step != null)
          Text(step!,
              style: theme.textTheme.labelLarge
                  ?.copyWith(color: theme.colorScheme.primary)),
        Text(title,
            style: theme.textTheme.headlineSmall
                ?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 10),
        Text(body, style: theme.textTheme.bodyLarge?.copyWith(height: 1.5)),
        const SizedBox(height: 20),
        ...children,
      ],
    );
  }
}

/// 패널 버튼처럼 생긴 원형 아이콘 + 설명 한 줄.
class GuideRow extends StatelessWidget {
  const GuideRow(this.icon, this.title, this.desc,
      {super.key, this.color, this.number});

  final IconData icon;
  final String title;
  final String desc;
  final Color? color;
  final int? number;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: color ?? const Color(0xFF33393E),
            child: number != null
                ? Text('$number',
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.bold))
                : Icon(icon, color: Colors.white, size: 20),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: theme.textTheme.titleSmall
                        ?.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(height: 2),
                Text(desc, style: theme.textTheme.bodyMedium),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

const _teal = Color(0xFF26A69A);
const _green = Color(0xFF2E7D32);

// ── 페이지들 ──────────────────────────────────────────────────────────────────

class _IntroPage extends StatelessWidget {
  const _IntroPage();

  @override
  Widget build(BuildContext context) {
    return _GuidePage(
      icon: Icons.touch_app,
      step: null,
      title: NativeBridge.isSupported ? '어느 앱 위에서든\n자동 클릭' : 'Auto Click',
      body: NativeBridge.isSupported
          ? '누를 위치를 한 번 찍어두면 Auto Click 이 대신 반복해서 눌러줍니다. '
              '게임, 출석 체크, 피드 넘기기 등 반복 작업에 쓰세요.'
          : '탭 · 영역 · 스와이프를 조합한 루틴을 만들고 프리셋으로 저장합니다.\n\n'
              '※ iOS · 웹은 OS 보안 정책상 다른 앱을 누를 수 없어서, 앱 안의 테스트 패드에서만 '
              '실행됩니다. 다른 앱 위 자동 클릭은 Android 에서 사용하세요.',
      children: const [
        GuideRow(Icons.touch_app, '지점 탭', '한 위치를 정해진 간격으로 계속 누르기'),
        GuideRow(Icons.highlight_alt, '영역 랜덤 탭', '사각형 안 무작위 위치를 눌러 자연스럽게'),
        GuideRow(Icons.swipe, '스와이프', '화면을 밀어서 넘기기 · 스크롤'),
        GuideRow(Icons.repeat, '루틴 · 프리셋', '여러 동작을 순서대로 묶고 저장해서 재사용'),
      ],
    );
  }
}

class _PermissionPage extends StatelessWidget {
  const _PermissionPage();

  @override
  Widget build(BuildContext context) {
    final bridge = NativeBridge.instance;
    return _GuidePage(
      icon: Icons.accessibility_new,
      step: 'STEP 1',
      title: '접근성 권한 켜기',
      body: 'Android 에서 다른 앱을 대신 누르려면 접근성 서비스가 필요합니다. '
          '필요한 권한은 이것 하나뿐이며, 화면 내용은 읽거나 저장하지 않습니다.',
      children: [
        ValueListenableBuilder<bool>(
          valueListenable: bridge.serviceEnabled,
          builder: (context, on, _) => Card.filled(
            color: on
                ? Theme.of(context).colorScheme.primaryContainer
                : Theme.of(context).colorScheme.surfaceContainerHigh,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Icon(on ? Icons.check_circle : Icons.radio_button_unchecked,
                        color: on ? _green : null),
                    const SizedBox(width: 8),
                    Text(on ? '권한이 켜져 있어요' : '아직 꺼져 있어요',
                        style: Theme.of(context).textTheme.titleMedium),
                  ]),
                  if (!on) ...[
                    const SizedBox(height: 12),
                    const Text('설정 화면에서 Auto Click 을 찾아 “사용” 을 켜고 돌아오세요.'),
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: bridge.openAccessibilitySettings,
                      icon: const Icon(Icons.open_in_new),
                      label: const Text('권한 설정하기'),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      '“제한된 설정” 이라며 켜지지 않으면 앱 정보 › 오른쪽 위 ⋮ › '
                      '“제한된 설정 허용” 후 다시 시도하세요.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    TextButton.icon(
                      onPressed: bridge.openAppSettings,
                      icon: const Icon(Icons.app_settings_alt),
                      label: const Text('앱 정보 열기'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _PanelPage extends StatelessWidget {
  const _PanelPage();

  @override
  Widget build(BuildContext context) {
    return const _GuidePage(
      icon: Icons.picture_in_picture_alt,
      step: 'STEP 2',
      title: '플로팅 패널',
      body: '홈의 “플로팅 패널 열기” 를 누르면 다른 앱 위에 아래 버튼들이 떠 있습니다.',
      children: [
        GuideRow(Icons.play_arrow, '실행 / 정지', '루틴을 순서대로 반복합니다. 다시 누르면 정지.',
            color: _green),
        GuideRow(Icons.add, '추가', '탭 · 길게 · 영역 · 스와이프 · 대기 중 골라 화면에서 위치 지정',
            color: _teal),
        GuideRow(Icons.settings, '설정', '반복 횟수 · 간격 · 랜덤, 저장된 프리셋 불러오기 · 새 작업'),
        GuideRow(Icons.save, '저장', '이름을 정해 프리셋으로 저장 (불러온 프리셋은 바로 저장)'),
        GuideRow(Icons.visibility, '마커', '화면의 번호 마커 보이기 / 숨기기'),
        GuideRow(Icons.home, '앱', 'Auto Click 앱으로 돌아오기'),
        GuideRow(Icons.drag_indicator, '맨 위 손잡이', '끌면 패널 이동, 누르면 접기 / 펴기'),
      ],
    );
  }
}

class _FlowPage extends StatelessWidget {
  const _FlowPage();

  @override
  Widget build(BuildContext context) {
    return const _GuidePage(
      icon: Icons.route,
      step: 'STEP 3',
      title: '루틴 만들고 실행하기',
      body: '처음엔 이 순서대로 해보세요.',
      children: [
        GuideRow(Icons.circle, '패널 열기', '홈에서 “플로팅 패널 열기” → 빈 패널이 떠요. 자동으로 누를 앱을 엽니다.',
            number: 1, color: _teal),
        GuideRow(Icons.circle, '위치 추가', '패널의 ＋ 추가 → “탭” → 누를 곳을 터치. 번호 마커가 생깁니다.',
            number: 2, color: _teal),
        GuideRow(Icons.circle, '주기 설정', '번호 마커를 누르면 클릭 간격 · 반복 횟수를 바꿀 수 있어요. '
            '마커를 끌면 위치가 옮겨집니다.', number: 3, color: _teal),
        GuideRow(Icons.circle, '실행', '▶ 실행 → 정해진 간격으로 반복해서 누릅니다. 다시 누르면 정지.',
            number: 4, color: _teal),
        GuideRow(Icons.circle, '저장', '저장 → 이름 입력 → 프리셋 저장. 다음엔 홈 목록에서 ▶ 실행이나 “이 루틴으로 패널 열기”.',
            number: 5, color: _teal),
      ],
    );
  }
}

class _WebFlowPage extends StatelessWidget {
  const _WebFlowPage();

  @override
  Widget build(BuildContext context) {
    return const _GuidePage(
      icon: Icons.route,
      step: 'STEP 1',
      title: '루틴 만들기',
      body: '홈의 “새 루틴” 으로 템플릿을 고르거나 빈 루틴을 만드세요.',
      children: [
        GuideRow(Icons.circle, '단계 추가', '“단계 추가” → 동작 종류 선택', number: 1, color: _teal),
        GuideRow(Icons.circle, '위치 지정', '“화면에서 지정” 으로 전체 화면에서 탭 / 드래그',
            number: 2, color: _teal),
        GuideRow(Icons.circle, '주기 설정', '다음 단계까지 간격 · 반복 횟수 · 루프 횟수 설정',
            number: 3, color: _teal),
      ],
    );
  }
}

class _TestPadPage extends StatelessWidget {
  const _TestPadPage();

  @override
  Widget build(BuildContext context) {
    return const _GuidePage(
      icon: Icons.science_outlined,
      step: 'STEP 2',
      title: '테스트 패드에서 실행',
      body: '▶ 실행을 누르면 테스트 패드가 열리고, 루틴이 화면 타일을 실제로 누릅니다. '
          '타일 숫자로 몇 번 눌렸는지 확인할 수 있어요.',
      children: [
        GuideRow(Icons.play_arrow, '실행 / 정지', '아래 컨트롤 바에서 시작 · 정지', color: _green),
        GuideRow(Icons.visibility, '마커', '단계 위치 표시 켜기 / 끄기'),
        GuideRow(Icons.restart_alt, '초기화', '타일 카운터를 0 으로'),
      ],
    );
  }
}

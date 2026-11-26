# Auto Click

Flutter로 만든 자동 클릭 앱입니다. 지점 탭, 영역 랜덤 탭, 길게 누르기, 스와이프, 대기를 조합해 **루틴**을 만들고, **프리셋**으로 저장해 반복 실행합니다.

## 플랫폼별 지원 범위

| 기능 | Android | iOS | Web |
| --- | :---: | :---: | :---: |
| 루틴 / 프리셋 작성 · 저장 · 가져오기 · 내보내기 | ✅ | ✅ | ✅ |
| 테스트 패드 (앱 내부 터치 주입으로 루틴 검증) | ✅ | ✅ | ✅ |
| **다른 앱 위에서 자동 클릭** | ✅ (접근성 권한) | ❌ | ❌ |
| 플로팅 패널 (다른 앱 위에서 위치 지정 · 실행 · 정지) | ✅ | ❌ | ❌ |

> **iOS · 웹이 안 되는 이유**: iOS는 다른 앱에 터치를 보내는 공개 API가 없습니다(App Store 정책으로도 금지). 브라우저는 보안 샌드박스 때문에 다른 탭이나 앱을 조작할 수 없습니다. 그래서 이 두 플랫폼에서는 루틴 작성과 프리셋 관리, 그리고 앱 내부 테스트 패드 실행만 할 수 있습니다.

## 주요 기능

- **단계 종류**: 탭 · 더블 탭 · 길게 누르기 · 영역 랜덤 탭 · 스와이프 · 대기
- **루틴 설정**: 루프 횟수(0 = 무한), 루프 간격, 시작 지연, 최대 실행 시간, 단계별 반복 횟수
- **사람처럼 보이게**: 좌표 흔들기(±px), 랜덤 추가 대기(ms)
- **좌표 지정**
  - 앱 안: 전체 화면 위치 지정기(탭, 드래그로 영역 · 스와이프 지정, 10% 격자 표시)
  - Android: 실제 대상 앱 위에서 플로팅 패널의 `＋ 탭 / ◎ 길게 / ▭ 영역 / ↕ 스와이프`로 바로 기록. 길게 누른 시간과 스와이프 속도까지 기록됩니다.
- **프리셋**: 자동 저장, 복제, 템플릿(연타 / 영역 랜덤 / 피드 넘기기 / 조합 예시), JSON 클립보드 내보내기 · 가져오기
- **권한 바로가기**: "권한 설정하기"를 누르면 Auto Click 접근성 항목으로 바로 이동합니다. 기기가 허용하지 않으면 접근성 목록에서 해당 항목을 강조하고, 그것도 안 되면 앱 정보 화면을 엽니다. 홈 화면 오른쪽 위 🛡 아이콘으로 언제든 다시 열 수 있습니다.

## Android 사용법

1. 앱 실행 → **권한 설정하기** → Auto Click 켜기
   - "제한된 설정" 때문에 켜지지 않으면(APK 직접 설치 시): **앱 정보 열기** → 오른쪽 위 ⋮ → **제한된 설정 허용**을 한 뒤 다시 켭니다.
2. 루틴 편집 화면에서 **오버레이 편집**을 누르면 앱이 뒤로 가고 플로팅 패널이 뜹니다.
3. 자동 클릭할 앱을 열고, 패널에서 `＋ 탭` 등을 눌러 위치를 기록합니다. 기록한 단계는 번호 마커로 표시됩니다.
4. `▶ 실행`으로 시작하고 `■ 정지`로 멈춥니다. `⌂ 앱 열기`를 누르면 앱으로 돌아오며, 패널에서 추가한 단계가 프리셋에 자동 반영됩니다.
5. 패널 제목줄(⠿)을 드래그하면 패널이 이동하고, 탭하면 접히거나 펼쳐집니다.

필요한 권한은 **접근성 서비스 하나**뿐입니다. 오버레이는 `TYPE_ACCESSIBILITY_OVERLAY`를 쓰므로 "다른 앱 위에 표시" 권한이 따로 필요하지 않습니다. 서비스는 화면 내용을 읽지 않고(`canRetrieveWindowContent=false`) `dispatchGesture`로 터치만 보냅니다.

## 실행 · 빌드

```bash
flutter pub get
flutter run -d <android-device>   # Android
flutter run -d chrome             # Web
flutter run -d <ios-device>       # iOS (macOS + Xcode 필요)

flutter build apk --release
flutter build web
flutter build ios
```

> Windows에서 프로젝트 경로가 길면 셰이더 컴파일 단계에서 `Could not write file` 오류가 날 수 있습니다(MAX_PATH 260자 제한). 짧은 경로(예: `C:\src\auto_click`)로 옮겨서 빌드하세요.

## 프로젝트 구조

```
lib/
  main.dart                      앱 진입점, 앱 복귀 시 네이티브와 동기화
  models/                        ActionStep · Routine · 템플릿 (JSON 직렬화)
  services/
    routine_store.dart           프리셋 저장소 (shared_preferences)
    native_bridge.dart           Android MethodChannel 브리지
    sandbox_runner.dart          앱 내부 포인터 이벤트 주입 실행기 (iOS/Web/Android 공통)
    run_actions.dart             실행 / 오버레이 / 권한 안내
  screens/                       홈 · 루틴 편집 · 단계 편집 · 위치 지정 · 테스트 패드
  widgets/
android/app/src/main/kotlin/com/seeminglyjs/auto_click/
  MainActivity.kt                MethodChannel 처리, 권한 설정 화면 바로가기
  AutoClickService.kt            접근성 서비스 + 오버레이 관리
  RoutineRunner.kt               dispatchGesture 기반 루틴 실행기
  OverlayViews.kt                플로팅 패널 · 단계 마커 · 위치 기록 레이어
```

## 프리셋 JSON 형식

좌표는 화면 크기 대비 0 ~ 1 비율이라 해상도가 다른 기기에서도 같은 프리셋을 쓸 수 있습니다. 화면 방향은 기록할 때와 같아야 합니다.

```json
{
  "name": "피드 넘기기",
  "loopCount": 0,
  "loopIntervalMs": 0,
  "startDelayMs": 1500,
  "maxRunSeconds": 0,
  "randomOffsetPx": 0,
  "randomDelayMs": 0,
  "steps": [
    { "type": "swipe", "x": 0.5, "y": 0.7, "x2": 0.5, "y2": 0.3, "durationMs": 300, "delayAfterMs": 0, "repeat": 1 },
    { "type": "wait", "durationMs": 3000 }
  ]
}
```

`type`: `tap` · `doubleTap` · `longPress` · `areaTap`(x,y = 좌상단, x2,y2 = 우하단) · `swipe` · `wait`

## 주의

게임이나 서비스에 따라 자동 입력을 이용약관으로 금지하는 경우가 있습니다. 사용 전에 대상 앱의 정책을 확인하세요.

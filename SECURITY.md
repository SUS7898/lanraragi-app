# Security Policy

## 범위와 위협 모델
- 앱은 사용자의 NAS에 있는 LANraragi와만 통신한다(API 키는 그 호스트에만 전송). 그 외 외부 통신은
  GitHub API/Release 자산(업데이트)뿐이다.
- 로컬에 저장되는 민감 정보는 API 키 하나이며 Android Keystore(AES-256-GCM)로 암호화된다.
- 업데이트 APK는 SHA-256 체크섬과 서명 인증서 일치(실행 중인 앱과 동일)까지 검증한 뒤 설치한다.

## 자동 점검
`.github/workflows/security.yml` — CodeQL, Trivy(의존성/시크릿/설정), gitleaks, Android Lint, mobsfscan,
Dependency Review. `release.yml`은 Trivy에서 수정판이 있는 CRITICAL/HIGH 취약점이 있으면 릴리스를 중단한다.

## 수용한 위험 (의도된 설계, 스캐너가 경고함)
| 항목 | 이유 | 완화책 |
|---|---|---|
| 평문 HTTP 허용 (`cleartextTrafficPermitted="true"`) | NAS의 LANraragi는 보통 `http://192.168.x.x:3000` | 설정의 "평문 HTTP 허용" 스위치로 런타임 차단 가능, UI에 http 경고 표시 |
| 사용자 설치 CA 신뢰 (`<certificates src="user"/>`) | 자체 서명 HTTPS NAS 지원 | 사용자가 기기에 CA를 직접 설치해야만 적용됨 |
| 루트 탐지·SSL 피닝·스크린샷 방지·SafetyNet 없음 (mobsfscan INFO) | 개인용 뷰어; 서버 주소는 사용자가 정하므로 피닝 불가 | 해당 없음 |
| mobsfscan `android_task_hijacking2` | 오탐: 매니페스트에 `<uses-sdk>`가 없어 targetSdk 26으로 가정(실제 35, Gradle 설정). 규칙은 targetSdk<29 전용 | `taskAffinity=""` + 기본 launchMode 적용 완료 |

`.mobsf` 파일에서 위 ERROR 규칙들을 무시 처리하고 그 근거를 적어 두었다(워크플로는 `mobsfscan -c .mobsf`로 실행).

## v0.1.1 코드 검토 (2026-10-07, 로컬 세션) — 기능·편의·보안

### 보안
- 새로 추가된 서버 쓰기(평점 태그, 즐겨찾기 카테고리, 카테고리 생성)는 모두 기존 인터셉터를 거치므로 API 키는 여전히
  설정된 서버 호스트에만 전송된다. 메타데이터 PUT은 form body로 보내 URL/프록시 로그에 태그·제목이 남지 않는다.
- 크래시 로그는 앱 전용 저장소(`files/crash/`)에만 쓰고, 공유는 사용자가 직접 ACTION_SEND로 할 때만 나간다.
  예외 메시지에 서버 호스트명이 포함될 수 있으니 공유 전에 내용을 확인한다. API 키는 예외 메시지에 들어가지 않는다.
- 매니페스트의 `FileProvider`는 코드에서 전혀 쓰이지 않아(설치는 `PackageInstaller` 세션) 제거했다 → 노출 컴포넌트는 런처
  Activity 하나와 비공개 `InstallResultReceiver`뿐.
- 새 의존성 `sh.calvin.reorderable`(순수 Kotlin/Compose, 네트워크·권한 없음). Trivy/Dependency Review 대상에 자동 포함.
- 변경 없음(재확인): Keystore 암호화 API 키, 체크섬+서명 일치 검증 업데이트, 평문 HTTP 런타임 차단 스위치, `allowBackup=false`.

### 기능·편의 (실기기 피드백 4건 + 자체 발견)
- 탭 존이 화면 전체가 아닌 **표시된 이미지** 기준으로 바뀌어 가로 태블릿/레터박스에서 "가운데가 아닌데 메뉴가 뜨는" 문제를 없앴다.
- 뒤로 가기: 필터 초기화 → 탭 복귀 → 종료의 3단계. 검색 직후 뒤로 가기로 앱이 꺼지지 않는다.
- 카테고리: 이름순(📌 먼저, 자연수 정렬)이 기본, 드래그 수동 순서는 기기에만 저장된다(서버에 순서 개념이 없음).
- 평점·즐겨찾기는 서버(태그/카테고리)에 저장되어 폰·태블릿·웹 UI가 같은 값을 본다. 대신 API 키가 없으면 읽기만 된다.
- "N점 이상" 필터는 전체 결과를 한 번에 받아 거르므로(서버에 OR 검색이 없음) 아카이브가 수만 개면 첫 응답이 느릴 수 있다.
- 리더에서 바텀시트/목차가 열린 동안 키보드 방향키는 여전히 페이지를 넘긴다(의도적 단순화).

## v0.1.5 변경의 보안 검토 (2026-10-18)
- **동기화 파일**: 사용자가 고른 문서(SAF)에만 쓰며 앱이 임의 위치에 접근하지 않는다. 파일에는 서버 주소·API 키를 넣지 않는다
  (읽기 기록의 작품 제목·arcid, 설정, 검색어는 들어간다 — 클라우드 폴더를 쓰면 그 제공자에 저장된다는 점을 UI에 명시).
  가져온 JSON은 알려진 필드만 역직렬화하고(`ignoreUnknownKeys`), 열거형은 모르는 값이면 기존 값을 유지한다.
- **FileProvider 재도입**: 페이지 공유 전용, `cache/share/`만 노출, `exported=false` + 일회성 URI 권한. 공유 전마다 디렉터리를 비운다.
- **저장**: MediaStore(Android 10+)로 Pictures/LRR Viewer에만 쓰며 저장소 권한을 요청하지 않는다(Android 9는 저장 비활성).
- 새 서버 쓰기(썸네일 지정, 별점)는 모두 기존 인터셉터를 거쳐 API 키가 설정한 서버 호스트에만 간다.

## v0.1.6 변경의 보안 검토 (2026-10-18)
- **앱 잠금**: 앱 화면을 가리는 UI 잠금이다(데이터 암호화가 아님). API 키는 원래대로 Keystore로 암호화되어 있다. 화면 잠금이 사라진 기기에서는
  사용자를 가두지 않도록 잠금 화면이 바로 열린다(fail-open) — 기기 화면 잠금이 이미 1차 방어선이라는 전제. 잠금 중 하드웨어 키 입력 차단.
- 새 권한 `USE_BIOMETRIC`/`USE_FINGERPRINT`(androidx.biometric 병합, 일반 권한). 새 의존성 `androidx.biometric:biometric:1.1.0`(Google, 네트워크 없음).
- **최근 앱에서 숨기기**: `FLAG_SECURE`로 최근 앱 미리보기와 스크린샷을 막는다(mobsfscan INFO "스크린샷 방지" 항목을 선택적으로 해소).
- 잠금·숨기기 설정은 동기화 파일에 넣지 않는다(기기별 보안 설정).

## v0.1.7 전체 코드 검토 (2026-10-22)
독립 검토로 찾은 보안 관련 항목과 조치:
- **앱 잠금 우회**: 잠금 전에 열려 있던 바텀 시트(페이지 격자·페이지 메뉴)가 잠금 화면 위에 남아 인증 없이 페이지 공유가 가능했다 → 잠금 화면을
  전체 화면 Dialog 창으로 바꿔 모든 창 위에 두고 뒤로 가기를 막음, 잠금 중 업데이트 다이얼로그 억제.
- **평문 HTTP 허용 동기화**: 한 기기에서 끄면 다른 기기(LAN http)의 연결이 모두 막히는 가용성 문제 → 기기별 설정.
- 확인 결과 문제없음: API 키는 설정한 서버 호스트에만 전송(리다이렉트 시 OkHttp가 제거), 동기화/백업/로그/크래시 로그에 키 없음,
  FileProvider는 `cache/share/`만, 수신기 비공개, 업데이트는 SHA-256 + 패키지명 + versionCode + 서명자 집합 검증, PendingIntent 플래그 정상,
  SAF 영구 권한 취득/해제 정상, BiometricPrompt 인증 조합 정상.
- 새 권한 `UPDATE_PACKAGES_WITHOUT_USER_ACTION`(일반, API 31+): 이 앱이 설치자인 경우에만 자기 자신을 확인 없이 업데이트할 수 있게 함.
  업데이트 APK 검증(체크섬·서명)은 그대로이므로 공격 면이 늘지 않는다.

## 취약점 제보
이 저장소의 Security Advisories(비공개) 또는 이슈로 제보해 주세요. 영향 범위와 재현 방법을 포함하면 좋습니다.

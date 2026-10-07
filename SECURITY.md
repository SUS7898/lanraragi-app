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

## 취약점 제보
이 저장소의 Security Advisories(비공개) 또는 이슈로 제보해 주세요. 영향 범위와 재현 방법을 포함하면 좋습니다.

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

## 취약점 제보
이 저장소의 Security Advisories(비공개) 또는 이슈로 제보해 주세요. 영향 범위와 재현 방법을 포함하면 좋습니다.

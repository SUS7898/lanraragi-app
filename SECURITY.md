# Security Policy

## 범위와 위협 모델
- 앱은 사용자의 NAS에 있는 LANraragi와만 통신한다(API 키는 그 호스트에만 전송). 그 외 외부 통신은
  GitHub API/Release 자산(업데이트)뿐이다.
- 로컬에 저장되는 민감 정보는 API 키 하나이며 Android Keystore(AES-256-GCM)로 암호화된다.
- 업데이트 APK는 SHA-256 체크섬과 서명 인증서 일치(실행 중인 앱과 동일)까지 검증한 뒤 설치한다.

## 자동 점검
`.github/workflows/security.yml` — CodeQL, Trivy(의존성/시크릿/설정), gitleaks, Android Lint, mobsfscan,
Dependency Review. `release.yml`은 Trivy에서 수정판이 있는 CRITICAL/HIGH 취약점이 있으면 릴리스를 중단한다.

## 취약점 제보
이 저장소의 Security Advisories(비공개) 또는 이슈로 제보해 주세요. 영향 범위와 재현 방법을 포함하면 좋습니다.

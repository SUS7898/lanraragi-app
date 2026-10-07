# 진행 상황 (PROGRESS)

마지막 갱신: 2026-10-07 (세션 1) — 코드 완성, CI/보안 스캔 녹색. 다음은 사용자 작업(키스토어·Secrets) 후 첫 릴리스.

## 상태 요약
- [x] 요구사항 분석, Mihon 오류 원인 확인, LANraragi API 소스 확인
- [x] 프로젝트 골격 (Gradle 8.14.3 wrapper, AGP 8.10.1, Kotlin 2.1.21, Compose BOM 2025.06.01)
- [x] 데이터 계층: 설정(DataStore+Keystore 암호화), API 클라이언트, 관대한 파싱, 읽기 기록
- [x] UI: 첫 실행 서버 설정, 서재(검색/필터/카테고리/정렬/무한스크롤/무작위), 기록, 상세(태그/목차/탄코본), 설정
- [x] 뷰어: 스트리밍 + 미리받기, LTR/RTL/세로/웹툰, 줌, 탭존, 볼륨키, 슬라이더, 목차, 진행률 동기화
- [x] 자체 업데이트: GitHub Releases 확인 → SHA-256 → 서명 인증서 검증 → PackageInstaller
- [x] 단위 테스트 (파싱 회귀, URL, API MockWebServer, 버전, 체크섬)
- [x] GitHub Actions: CI / Release(서명+체크섬+Trivy 게이트) / Security(CodeQL·Trivy·gitleaks·Lint·mobsfscan)
- [x] 문서: CLAUDE.md, DESIGN, TROUBLESHOOTING, PROGRESS, README, SECURITY
- [x] CI에서 컴파일·테스트·린트 통과 확인 (run 37568970519)
- [x] Security Scan 전 작업 통과 확인 (run 37569641741: CodeQL·Trivy·gitleaks·Lint·mobsfscan ✅)
- [ ] 사용자: 키스토어 생성 + GitHub Secrets 등록 (`scripts/generate-keystore.sh`, README §릴리스)
- [ ] 첫 태그 `v0.1.0` 릴리스 → 실기기 설치 테스트(Galaxy 폰/태블릿)
- [ ] 실기기 피드백 반영 (제스처 감도, 웹툰 모드 줌, 탭존 비율 등)

## 다음 세션이 할 일 (우선순위)
1. `CI` 워크플로 상태 확인. 실패 시 로그 → 수정 → push. 결과를 TROUBLESHOOTING §C에 기록.
2. 사용자가 secrets를 넣었는지 확인 후 `v0.1.0` 태그로 `Release` 워크플로 실행.
3. 실기기 테스트 결과를 받아 버그 수정, PROGRESS 갱신.

## 검증 이력
| 날짜 | 무엇을 | 결과 |
|---|---|---|
| 2026-10-07 | 초기 코드 작성 (로컬 빌드 불가) | CI 대기 |
| 2026-10-07 | CI run 37568970519 (커밋 96b5e8a) | ✅ 테스트·린트·디버그 APK 성공 |
| 2026-10-07 | Security Scan run 37568970490 | CodeQL/Lint/gitleaks/mobsfscan ✅, Trivy ❌(빌드 도구 netty 오탐 → C-3로 수정) |
| 2026-10-07 | CI run 37569641719 + Security Scan run 37569641741 (커밋 a4412e2) | ✅ 전부 성공 |
| 2026-10-07 | CI run 37570391069 + Security Scan run 37570391094 (커밋 53e882a, 태스크 하이재킹 수정 후) | ✅ 전부 성공, mobsfscan ERROR 0 |

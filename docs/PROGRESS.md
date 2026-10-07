# 진행 상황 (PROGRESS)

마지막 갱신: 2026-10-07 (세션 2, 로컬 PC) — 로컬 clone·빌드 환경 구성 완료(23개 테스트 통과, 디버그 APK). 다음은 사용자 작업(키스토어·Secrets) → 첫 릴리스 → 실기기 테스트.

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
- [x] 로컬 PC(Windows) 빌드 환경 구성: 한글 경로·인자 파일 인코딩·hosts 역조회 문제 해결(TROUBLESHOOTING §F), `app/schemas/…/1.json` 생성
- [ ] 사용자: 키스토어 생성 + GitHub Secrets 등록 (`scripts/generate-keystore.sh`, README §릴리스)
- [ ] 첫 태그 `v0.1.0` 릴리스 → 실기기 설치 테스트(Galaxy 폰/태블릿)
- [ ] 실기기 피드백 반영 (제스처 감도, 웹툰 모드 줌, 탭존 비율 등)

## 다음 세션이 할 일 (우선순위)
1. 사용자가 키스토어·secrets를 넣었는지 확인 후 `v0.1.0` 태그로 `Release` 워크플로 실행.
2. `docs/TEST_CHECKLIST.md`로 실기기 테스트(릴리스 빌드) → 실패 항목 수정 → TROUBLESHOOTING 기록.
3. `docs/BACKLOG.md` P0 항목(Galaxy 자동 차단 안내, 웹툰 스트립 처리)부터 착수. 그 다음 P1.

- [x] 개선 백로그 검토·정리 (`docs/BACKLOG.md`), 실기기 테스트 체크리스트 (`docs/TEST_CHECKLIST.md`)
- [x] 구조 개편(조기 교체): Room 기록 저장소(`serverId`+`arcid`), Coil 3, 타입 안전 라우트, ViewModel 의존성 축소, 리더 파일 분리 — DESIGN §3/§9, TROUBLESHOOTING §E
- [x] 구조 개편 커밋의 CI/Security Scan 통과 확인 (run 37589754882 / 37589754810, 첫 시도에 전부 성공)

## 검증 이력
| 날짜 | 무엇을 | 결과 |
|---|---|---|
| 2026-10-07 | 초기 코드 작성 (로컬 빌드 불가) | CI 대기 |
| 2026-10-07 | CI run 37568970519 (커밋 96b5e8a) | ✅ 테스트·린트·디버그 APK 성공 |
| 2026-10-07 | Security Scan run 37568970490 | CodeQL/Lint/gitleaks/mobsfscan ✅, Trivy ❌(빌드 도구 netty 오탐 → C-3로 수정) |
| 2026-10-07 | CI run 37569641719 + Security Scan run 37569641741 (커밋 a4412e2) | ✅ 전부 성공 |
| 2026-10-07 | CI run 37570391069 + Security Scan run 37570391094 (커밋 53e882a, 태스크 하이재킹 수정 후) | ✅ 전부 성공 |
| 2026-10-07 | CI run 37571175724 + Security Scan run 37571175711 (커밋 0658f85) | ✅ 전부 성공, mobsfscan ERROR 0(로컬 검증), Lint 0 errors/19 warnings → C-9로 정리 |
| 2026-10-07 | CI run 37589754882 + Security Scan run 37589754810 (커밋 15b4031, 구조 개편: Room/Coil 3/타입 안전 라우트) | ✅ 전부 성공 |
| 2026-10-07 | 로컬 PC 첫 빌드 (`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`, Windows 11/JDK 17) | ✅ 23 tests 통과, app-debug.apk 생성 — §F-1~F-3 해결 후 |
| 2026-10-07 | CI run 37615134952 + Security Scan run 37615134928 (커밋 ce74b24, 로컬 환경 정리·LrrApiTest 호스트 수정·Room 스키마 커밋) | ✅ 전부 성공 |
| 2026-10-07 | Release run 37617782057 (태그 v0.1.0 push) | ❌ Secrets 미완(비밀번호 2개) → 사용자 등록 |
| 2026-10-07 | Release run 37618539572 (workflow_dispatch, main ff 후) | ❌ 서명 빌드·Trivy 통과, `Verify APK signature`의 v2 grep 오탐 → C-10으로 수정 |

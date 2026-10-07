# LRR Viewer — 작업 지침 (Claude Code / 새 세션용)

NAS의 LANraragi 컨테이너 전용 Android 뷰어 앱(Kotlin + Jetpack Compose). Google Play 미배포,
GitHub Releases 기반 자체 업데이트, CI에서 서명·보안 스캔.

**새 세션에서 작업을 시작하기 전에 반드시 읽을 것:**
1. `docs/PROGRESS.md` — 현재 상태, 완료/미완료 항목, 다음 할 일
2. `docs/TROUBLESHOOTING.md` — 이미 겪은 문제와 해결책 (같은 오류 반복 금지)
3. `docs/DESIGN.md` — 아키텍처, 결정 사항과 이유, LANraragi API 메모

## 절대 규칙
- 문제를 해결하면 **같은 커밋에서** `docs/TROUBLESHOOTING.md`에 증상/원인/해결을 추가한다.
- 설계를 바꾸면 `docs/DESIGN.md`를 갱신한다. 단계가 끝나면 `docs/PROGRESS.md`를 갱신한다.
- 서명 키(`*.jks`, `keystore.properties`)는 절대 커밋하지 않는다 (`.gitignore` 참조).
- 커밋 메시지·코드 주석에 모델 식별자를 넣지 않는다.
- 숫자/불리언 JSON 필드는 반드시 `LenientXxxSerializer`로 파싱한다 (Mihon 확장이 깨진 원인).

## 빌드 / 검증 방법
- **Claude Code 클라우드 세션에서는 로컬 Gradle 빌드가 불가능하다.** `dl.google.com`
  (Android SDK, Google Maven)이 네트워크 정책으로 차단됨. 우회 시도(ghcr/Docker Hub 이미지,
  Maven 미러)도 모두 차단 → 시간 낭비하지 말 것. 자세한 내용은 `docs/TROUBLESHOOTING.md` §B.
- 검증 루프: 브랜치에 push → GitHub Actions `CI` 워크플로 결과 확인
  (`mcp__github__actions_list` / `actions_get` / `get_job_logs`), 실패 로그로 수정 → 다시 push.
  `workflow_dispatch`로 수동 실행도 가능 (`CI`, `Security Scan`, `Release`).
- 로컬 PC(Android Studio)에서는 `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- 릴리스: `git tag vX.Y.Z && git push origin vX.Y.Z` → `Release` 워크플로가 서명 APK +
  `SHA256SUMS.txt`를 GitHub Release에 올림 → 앱이 자동 감지. 저장소 secrets 필요
  (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

## 코드 지도
```
app/src/main/kotlin/com/sus7898/lrrviewer/
  App.kt, AppGraph.kt          Application + 수동 DI(서비스 로케이터)
  MainActivity.kt              Compose 호스트, 볼륨키 → ReaderKeyEvents
  data/AppSettings.kt          설정 데이터 클래스 + enum (ReadingMode 등)
  data/SettingsRepository.kt   DataStore 저장, API 키는 SecureStore로 암호화
  data/SecureStore.kt          Android Keystore AES-GCM
  data/ReadingHistoryRepository.kt  로컬 읽기 기록/진행률(JSON in DataStore)
  data/api/LrrApi.kt           LANraragi API 클라이언트 (OkHttp + kotlinx.serialization)
  data/api/Models.kt           Archive/ServerInfo/… (관대한 파싱)
  data/api/LenientSerializers.kt  "" / "3" / null / "none" 모두 허용하는 직렬화기
  data/api/LrrUrls.kt          베이스 URL 정규화, 페이지 경로 → 절대 URL(서브패스 대응)
  data/api/LrrAuthInterceptor.kt  Authorization: Bearer base64(apiKey) (서버 호스트에만), HTTP 차단 옵션
  update/UpdateManager.kt      GitHub Releases 확인 → 다운로드(SHA-256) → 서명 인증서 검증 → PackageInstaller
  update/InstallResultReceiver.kt  설치 세션 결과 수신(사용자 확인 화면 띄움)
  ui/AppRoot.kt                NavHost(setup/home/archive/reader) + 자동 업데이트 다이얼로그
  ui/home/, ui/library/, ui/history/, ui/detail/, ui/settings/, ui/reader/
app/src/test/…                 JVM 단위 테스트(MockWebServer 포함)
.github/workflows/ci.yml       테스트·린트·디버그 APK
.github/workflows/release.yml  태그 → 서명 릴리스 APK + 체크섬 + Trivy 게이트
.github/workflows/security.yml CodeQL·Trivy·gitleaks·Android Lint·mobsfscan·Dependency Review
```

## 버전 핀 (의도적으로 고정 — 함부로 올리지 말 것, 올리면 CI로 검증)
AGP 8.10.1 · Kotlin 2.1.21 · Gradle 8.14.3 · Compose BOM 2025.06.01 · Coil 2.7.0 ·
telephoto 0.16.0 (zoomable-image-coil, Coil2용) · OkHttp 4.12.0 · kotlinx.serialization 1.8.1
compileSdk/targetSdk 35, minSdk 26. 자세한 이유는 `docs/DESIGN.md` §버전.

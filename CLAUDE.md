# LRR Viewer — 작업 지침 (Claude Code / 새 세션용)

NAS의 LANraragi 컨테이너 전용 Android 뷰어 앱(Kotlin + Jetpack Compose). Google Play 미배포,
GitHub Releases 기반 자체 업데이트, CI에서 서명·보안 스캔.

**새 세션에서 작업을 시작하기 전에 반드시 읽을 것:**
1. `docs/PROGRESS.md` — 현재 상태, 완료/미완료 항목, 다음 할 일
2. `docs/TROUBLESHOOTING.md` — 이미 겪은 문제와 해결책 (같은 오류 반복 금지)
3. `docs/DESIGN.md` — 아키텍처, 결정 사항과 이유, LANraragi API 메모
4. `docs/BACKLOG.md` — 검토 완료된 개선 항목(우선순위·설계 힌트 포함). 새 기능은 여기서 골라 시작
5. `docs/TEST_CHECKLIST.md` — 실기기 수동 테스트 절차(릴리스 빌드로)

## 절대 규칙
- 문제를 해결하면 **같은 커밋에서** `docs/TROUBLESHOOTING.md`에 증상/원인/해결을 추가한다.
- 설계를 바꾸면 `docs/DESIGN.md`를 갱신한다. 단계가 끝나면 `docs/PROGRESS.md`를 갱신한다.
- 백로그 항목을 구현하면 `docs/BACKLOG.md`에서 체크하고, 새 아이디어는 구현 대신 백로그에 먼저 적는다.
- 서명 키(`*.jks`, `keystore.properties`)는 절대 커밋하지 않는다 (`.gitignore` 참조).
- 커밋 메시지·코드 주석에 모델 식별자를 넣지 않는다.
- 숫자/불리언 JSON 필드는 반드시 `LenientXxxSerializer`로 파싱한다 (Mihon 확장이 깨진 원인).
- 평점은 서버 태그 `rating:N`, 즐겨찾기는 서버 북마크 카테고리다. 메타데이터 PUT은 title/tags/summary를 항상 함께 보낸다(덮어쓰기 API).
- 한국어가 바로 뒤에 오는 문자열 템플릿은 `"${n}점"`처럼 중괄호 필수 (`"$n점"`은 식별자 `n점`으로 해석됨).
- 새 화면은 `ui/AppRoot.kt`에 `@Serializable` 라우트로 추가한다(문자열 라우트 금지). 로컬 저장은 Room 엔티티에 `serverId` 컬럼 필수.
- Room 엔티티를 바꾸면 version 증가 + Migration/AutoMigration, 로컬 빌드로 `app/schemas/` 갱신 후 커밋.

## 빌드 / 검증 방법
- **Claude Code 클라우드 세션에서는 로컬 Gradle 빌드가 불가능하다.** `dl.google.com`
  (Android SDK, Google Maven)이 네트워크 정책으로 차단됨. 우회 시도(ghcr/Docker Hub 이미지,
  Maven 미러)도 모두 차단 → 시간 낭비하지 말 것. 자세한 내용은 `docs/TROUBLESHOOTING.md` §B.
- 검증 루프: 브랜치에 push → GitHub Actions `CI` 워크플로 결과 확인
  (`mcp__github__actions_list` / `actions_get` / `get_job_logs`), 실패 로그로 수정 → 다시 push.
  `workflow_dispatch`로 수동 실행도 가능 (`CI`, `Security Scan`, `Release`).
- 로컬 PC(Android Studio)에서는 `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
  Windows에서는 PowerShell에서 `.\gradlew.bat …`(Git Bash의 `./gradlew`는 한글 경로가 깨짐). 한글 경로·hosts 광고 차단과
  관련된 함정과 사용자 전역 `~/.gradle/gradle.properties` 설정은 `docs/TROUBLESHOOTING.md` §F.
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
  data/db/AppDatabase.kt       Room v3: reading_progress / category_order / page_info + DAO. 스키마 JSON은 app/schemas/ (로컬 빌드 후 커밋)
  data/ReadingProgressRepository.kt  로컬 읽기 기록/진행률/작품별 읽기 방향 (Room 위)
  data/CategoryOrderRepository.kt    카테고리 수동 순서 (Room category_order), data/CategorySorting.kt 정렬 모드·자연수 정렬
  data/FavoritesRepository.kt        즐겨찾기 = 서버 북마크 카테고리(멤버 집합 공유, 🔑 토글)
  CrashLog.kt                        마지막 미처리 예외를 files/crash/에 저장 (설정 → 정보)
  data/PageImageStore.kt             페이지 파일 = Coil 디스크 캐시: 프리페치(DiskOnlyDecoder)·치수(inJustDecodeBounds)·영역 디코딩(타일 LRU)
  data/PageInfoRepository.kt         페이지 치수 캐시 (Room page_info) — 웹툰 타일링·모드 제안·(향후) 양면 보기의 기반
  data/api/LrrApi.kt           LANraragi API 클라이언트 (OkHttp + kotlinx.serialization)
  data/api/Models.kt           Archive/ServerInfo/… (관대한 파싱)
  data/api/LenientSerializers.kt  "" / "3" / null / "none" 모두 허용하는 직렬화기
  data/api/LrrUrls.kt          베이스 URL 정규화, 페이지 경로 → 절대 URL(서브패스 대응)
  data/api/LrrAuthInterceptor.kt  Authorization: Bearer base64(apiKey) (서버 호스트에만), HTTP 차단 옵션
  update/UpdateManager.kt      GitHub Releases 확인 → 다운로드(SHA-256) → 서명 인증서 검증 → PackageInstaller
  update/InstallResultReceiver.kt  설치 세션 결과 수신(사용자 확인 화면 띄움)
  ui/AppRoot.kt                타입 안전 라우트(SetupRoute/HomeRoute/ArchiveRoute/ReaderRoute) + 자동 업데이트 다이얼로그
  ui/home/, ui/library/, ui/history/, ui/detail/, ui/settings/ (CategoryOrderScreen = 드래그 순서 편집, sh.calvin.reorderable)
  ui/reader/ReaderScreen.kt    호스트(크롬·시스템 UI·키) / PagedReader.kt / WebtoonReader.kt(+WebtoonLayout.kt 행·타일 계산) / ReaderGestures.kt / ReaderViewModel.kt(Deps)
app/src/test/…                 JVM 단위 테스트(MockWebServer 포함)
.github/workflows/ci.yml       테스트·린트·디버그 APK
.github/workflows/release.yml  태그 → 서명 릴리스 APK + 체크섬 + Trivy 게이트
.github/workflows/security.yml CodeQL·Trivy·gitleaks·Android Lint·mobsfscan·Dependency Review
```

## 버전 핀 (의도적으로 고정 — 함부로 올리지 말 것, 올리면 CI로 검증)
AGP 8.10.1 · Kotlin 2.1.21 · KSP 2.1.21-2.0.1 · Gradle 8.14.3 · Compose BOM 2025.06.01 · Coil **3.2.0** ·
telephoto 0.16.0 (zoomable-image-**coil3**) · Room 2.7.1 · OkHttp 4.12.0 · kotlinx.serialization 1.8.1
compileSdk/targetSdk 35, minSdk 28 (signingInfo·longVersionCode가 API 28). 자세한 이유는 `docs/DESIGN.md` §버전.

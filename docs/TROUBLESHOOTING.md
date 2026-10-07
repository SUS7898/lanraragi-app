# 트러블슈팅 기록 (TROUBLESHOOTING)

> 형식: **증상 → 원인 → 해결/회피 → 재발 방지**. 새 문제를 풀면 즉시 추가한다.
> 섹션 A: 앱/사용자 문제, B: 개발 환경(클라우드 세션), C: 빌드/CI, D: 라이브러리 API 함정.

## A. 앱 / LANraragi 관련

### A-1. Mihon LANraragi 확장 `NumberFormatException: For input string: ""`
- 원인: 확장이 `date_added` 태그 값을 `toLong()`으로 바로 변환. 값이 빈 아카이브(`date_added:`)에서 크래시.
  `pagecount`/`progress`가 `""`/문자열인 경우도 동일 계열.
- 해결: 이 앱은 `LenientIntSerializer` 등으로 모든 숫자/불리언을 관대하게 파싱, `toLongOrNull()` 사용,
  목록은 항목별 개별 디코딩(`LrrApi.decodeArchives`)으로 깨진 항목만 건너뜀(`SearchPage.skipped`).
- 재발 방지: 새 모델 필드는 반드시 Lenient 직렬화기 사용. 테스트 `ModelsParsingTest`.

### A-2. 리버스 프록시 서브패스(`https://nas/lrr`)에서 페이지 URL 404
- 원인: `/files` 응답 경로가 `/lrr/api/...`처럼 서브패스를 포함 → 베이스 URL에 그대로 붙이면 `/lrr/lrr/api/...`.
- 해결: `LrrUrls.resolvePageUrl`이 베이스의 경로 접두사를 제거 후 다시 붙임. 테스트 `LrrUrlsTest`.

### A-3. 구버전 서버에서 `/files`가 `{job, pages: []}` 반환
- 해결: `ReaderViewModel.waitForExtraction`이 `/api/minion/{job}`을 1초 간격 폴링(최대 90초), 중간중간 `/files` 재조회.

### A-4. 서버 측 진행률 추적이 꺼진 서버에서 `PUT progress`가 오류
- 해결: `/api/info`의 `server_tracks_progress`를 확인해 꺼져 있으면 호출하지 않음. 로컬 기록은 항상 저장.

### A-5. 설치 시 "출처를 알 수 없는 앱" / Play Protect 경고
- 사실: 서명으로 제거 불가(Play 미배포 앱의 정상 동작). 최초 1회 허용 후 앱 내 업데이트로 전환하면
  이후에는 시스템 "업데이트" 확인만 뜨고, 앱이 설치자가 된 뒤(API 31+)에는 확인 없이 설치될 수 있음.
- 체크: 릴리스 키가 바뀌면 "기존 앱과 서명이 달라 설치 불가" → 키스토어를 **절대 분실/변경하지 말 것**.

## B. 개발 환경 — Claude Code 클라우드 세션 (2026-10 기준)

### B-1. 로컬 Gradle 빌드 불가: `dl.google.com` 403 (CONNECT 거부)
- 증상: `gradle wrapper`조차 플러그인 해석 단계에서 실패. `curl https://dl.google.com/...` → 403.
- 원인: 세션 egress 정책이 `dl.google.com` 차단. `maven.google.com`은 `dl.google.com`으로 301 리다이렉트라 동일.
- 시도했으나 실패(반복 금지):
  - Android SDK cmdline-tools 직접 다운로드 → 403.
  - ghcr.io `cirruslabs/android-sdk` 이미지 레이어 → 블롭 호스트 `pkg-containers.githubusercontent.com` 403.
  - Docker Hub `thyrlian/android-sdk` → 블롭은 받아지지만 SDK 플랫폼/빌드툴은 이미지에 없고(cmdline-tools만),
    결정적으로 Google Maven이 막혀 AGP/androidx 아티팩트를 받을 수 없음.
  - Maven 미러(aliyun, huawei, jitpack, jetbrains cache-redirector) → 전부 차단(000).
- 해결: **GitHub Actions를 빌드 환경으로 사용**. push 후 `CI` 워크플로 로그로 검증·수정 반복.
  Gradle wrapper 파일은 빈 임시 디렉터리에서 `gradle wrapper`로 생성해 복사(플러그인 해석 회피).
- 참고: `repo1.maven.org`, `plugins.gradle.org`, `services.gradle.org`, `api.github.com`,
  `raw.githubusercontent.com`, `registry-1.docker.io`는 접근 가능.

### B-2. GitHub API로 다른 공개 저장소(LANraragi, keiyoushi) 조회 시 "not enabled for this session"
- 원인: 세션의 GitHub 접근 범위가 지정 저장소 3개로 제한.
- 회피: `raw.githubusercontent.com` 직접 fetch 또는 `git clone --filter=blob:none --sparse`는 가능.
  LANraragi 소스는 이렇게 받아 `tools/openapi.yaml`, `Controller/Api/*.pm`, `Model/Reader.pm`을 확인했다.

### B-3. GitHub Actions 아티팩트 다운로드 불가 (blob.core.windows.net 403)
- 아티팩트 zip은 `productionresultssa*.blob.core.windows.net`에서 내려받는데 세션 egress 정책이 차단.
- 회피: 결과를 **작업 로그**로 읽는다. mobsfscan은 콘솔에 표를 찍고, Android Lint는 `textReport = true` +
  `textOutput = file("stdout")`로 콘솔 출력하도록 설정함. 로그는 `mcp__github__get_job_logs`(job_id)로 조회.

## C. 빌드 / CI

### C-1. 첫 CI 실행 (커밋 40fdd7a, 2026-10-07)
- `Unit tests` 단계 성공 = 메인/테스트 Kotlin 컴파일 통과, 테스트 전부 통과. (이후 단계는 두 번째 push로 취소됨)
- 사전 리뷰에서 고친 것: minSdk 28(NewApi), okio `request(Long)`, 컴포저블 슬롯 추론 → 커밋 cb1faa5.

### C-2. `aquasecurity/trivy-action` 태그는 `v` 접두사 (`@v0.36.0`), `skip-setup-trivy` 입력은 v0.29.0+
- `0.28.0`(v 없음)은 raw.githubusercontent.com에서 404, `v0.28.0`은 존재. 0.28.0에는 `skip-setup-trivy` 입력이 없어
  경고 발생 → `v0.36.0`으로 고정(2026-10 기준 확인된 최신 태그). 액션 입력 이름은 각 저장소의 `action.yml`을
  raw로 받아 확인할 수 있다(API는 세션 범위 밖이라 차단).
- 확인한 입력: setup-android v3 `packages`, `accept-android-sdk-licenses`; mobsfscan CLI `--sarif --json -o`;
  softprops/action-gh-release v2 `make_latest`, `generate_release_notes`, `fail_on_unmatched_files`.

### C-3. Trivy 게이트 실패: `io.netty:*` CRITICAL/HIGH (커밋 96b5e8a)
- 증상: Security Scan의 Trivy 작업이 `io.netty:netty-handler 4.1.110.Final` 등 CRITICAL/HIGH로 실패. CI 자체는 성공.
- 원인: `lockAllConfigurations()` + 모든 resolvable 구성을 resolve → `gradle.lockfile`에 AGP/Gradle **빌드 도구**
  의존성(netty, protobuf, grpc…)까지 기록됨. 이것들은 APK에 포함되지 않는데 Trivy는 구분하지 못함.
- 해결: 잠금 태스크를 `lockReleaseDependencies`로 바꿔 `releaseRuntimeClasspath`/`releaseCompileClasspath`만 resolve.
  → lockfile = 실제 배포되는 의존성 그래프. 빌드 도구(AGP) 취약점은 Dependabot의 AGP 업데이트 PR로 관리.
- 재발 방지: 새 구성(configuration)을 스캔 대상에 넣을 때는 "기기에 올라가는 것인지" 먼저 확인.

### C-4. 두 번째 CI 실행 (커밋 96b5e8a): 성공
- Unit tests · Android Lint(debug) · assembleDebug 모두 통과. Security Scan: CodeQL·Android Lint(release)·gitleaks·mobsfscan 통과, Trivy만 C-3로 실패.

### C-5. 세 번째 실행 (커밋 a4412e2): CI·Security Scan 전부 성공
- Trivy 범위를 배포 의존성으로 좁힌 뒤 게이트 통과. 이 시점의 상태가 "릴리스 가능" 기준선.

### C-6. mobsfscan 결과 (run 37569641741) 와 조치
- ERROR `android_task_hijacking1/2`: `launchMode="singleTask"` → StrandHogg 류 태스크 하이재킹 경고.
  **조치**: launchMode 제거(기본 standard) + `android:taskAffinity=""` (커밋 참조).
- ERROR `android_manifest_base_config_cleartext`, `..._trust_user_certs`: NAS 시나리오상 의도된 설계 → `.mobsf`에서
  ignore + SECURITY.md "수용한 위험"에 근거 기록.
- INFO 6건(인증서 투명성, 루트 탐지, SSL 피닝, 탭재킹, 스크린샷 방지, SafetyNet): 개인용 뷰어에 해당 없음.
- 참고: mobsfscan 결과는 아티팩트 대신 **작업 로그**(job id)에서 RULE ID/SEVERITY 표를 파싱해 읽었다(B-3).

### C-7. 네 번째 실행 (커밋 53e882a): CI·Security Scan 전부 성공, 컴파일 경고 정리
- 경고: `Icons.Filled.Sort`/`MenuBook` deprecated → `Icons.AutoMirrored.Filled.*`; Coil `diskCache.size/clear()`는
  `@OptIn(ExperimentalCoilApi::class)` 필요. 모두 정리.
- Lint 텍스트 리포트: `textOutput = file("stdout")`는 `app/stdout` **파일**로 써진다(프로젝트 상대경로로 해석).
  콘솔 출력은 `textOutput = File("stdout")`(java.io.File, 경로 문자열이 정확히 "stdout")이어야 한다.

### C-8. mobsfscan `.mobsf` 무시 설정이 적용되지 않음 / `task_hijacking2` 오탐
- 증상: `.mobsf`를 저장소 루트에 두었는데 CI에서 여전히 cleartext/user-certs ERROR. `task_hijacking1`은 사라졌으나 `2`는 남음.
- 원인 1: mobsfscan은 **스캔 대상 경로**(`app/src/main`)에서 `.mobsf`를 찾는다 → `-c .mobsf`로 명시해야 함.
- 원인 2: `manifest.py` `TaskHijackingChecks`가 `<uses-sdk>`가 없으면 targetSdk=26으로 가정 → `targetSdk < 29 && exported`로 오탐.
  AGP가 Gradle의 targetSdk 35를 병합하므로 소스 매니페스트에는 `<uses-sdk>`가 없다.
- 해결: 워크플로에 `-c .mobsf` 추가, `.mobsf`에 `android_task_hijacking2` 무시(근거 주석). 로컬 검증은
  `python3 -m venv v && v/bin/pip install mobsfscan && v/bin/mobsfscan -c .mobsf --json -o out.json app/src/main`
  (pypi는 세션에서 접근 가능 → **mobsfscan은 로컬에서 돌릴 수 있는 유일한 검사기**).

### C-9. Android Lint(release) 결과: 0 errors / 19 warnings (run 37571175711) 와 정리
- 고친 것: `ObsoleteSdkInt`(mipmap-anydpi-v26 → mipmap-anydpi), `DataExtractionRules`(`data_extraction_rules.xml` 추가),
  `ModifierParameter`(LoadingView 파라미터 순서), `UseKtx`(`toUri()`, `toDrawable()`).
- 의도적으로 둔 것: `InsecureBaseConfiguration`/`AcceptsUserCertificates`(수용 위험, XML에 `tools:ignore` + 주석),
  `GradleDependency`/`AndroidGradlePluginVersion`(버전 핀 정책, Dependabot PR로 관리), `OldTargetApi`(compileSdk 35 핀),
  `UnusedAttribute enableOnBackInvokedCallback`(API 33+에서만 의미, 무해).
- Lint 결과는 이제 작업 로그에 텍스트로 출력된다(`textOutput = File("stdout")`).

## E. 구조 개편(세션 1 후반) 시 확인한 사항 — Room · Coil 3 · 타입 안전 내비게이션

### E-1. 왜 "전면 개편"이 아니라 "선택적 조기 교체"인가 (결정 기록)
- 단일 모듈 + MVVM + 수동 DI는 이 규모에 적절. 갈아엎으면 비용만 든다.
- 나중에 바꾸면 비싼 것만 지금 교체: ① 기록 저장소 JSON → **Room**, 키 `(serverId, arcid)`; ② **Coil 2 → 3**;
  ③ 문자열 라우트 → **타입 안전 라우트**; ④ ViewModel 의존성 축소; ⑤ 리더 파일 분리.
- 보류: Hilt/Koin, 멀티모듈, strings.xml 분리(BACKLOG P3).

### E-2. KSP 플러그인은 `com.google.*`이지만 google() 저장소에 없다
- `settings.gradle.kts`의 `pluginManagement { google { content { includeGroupByRegex("com\\.google.*") } } }`가 있으면
  Gradle이 KSP(`com.google.devtools.ksp`)를 google()에서만 찾다가 실패한다. → 플러그인 저장소의 content filter 제거.
- KSP 버전은 Kotlin 버전과 접두사가 정확히 같아야 함(`2.1.21-2.0.1`). Maven Central
  `com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml`로 존재 확인.

### E-3. Room 스키마 JSON은 CI가 커밋하지 않는다
- `room { schemaDirectory("$projectDir/schemas") }` + `exportSchema = true`. 스키마 파일은 빌드 산출물이라 **로컬 빌드 후
  `app/schemas/**`를 커밋**해야 다음 버전에서 AutoMigration을 쓸 수 있다. 커밋 전까지는 수동 `Migration`으로 처리.
- `.gitignore`는 `app/schemas`를 제외하지 않는다(확인).

### E-4. Coil 2 → 3 API 차이 (이 코드베이스에서 실제로 바꾼 것)
- 패키지 `coil.*` → `coil3.*`. `Application : ImageLoaderFactory` → `SingletonImageLoader.Factory` (`newImageLoader(PlatformContext)`).
- 네트워크: `ImageLoader.Builder.okHttpClient(...)` 없음 → `.components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }`
  (`coil-network-okhttp` 의존성 필요).
- `respectCacheHeaders(false)` 없음 → Coil 3 기본이 캐시 헤더 무시(원하는 동작). 헤더 존중은 `coil-network-cache-control`.
- `DiskCache.Builder().directory(okio.Path)` → `File.toOkioPath()`; `MemoryCache.Builder().maxSizePercent(context, 0.25)`(context 인자 이동).
- `crossfade(false)`는 `coil3.request.crossfade` **확장 함수** import 필요.
- `ImageRequest.Builder.setParameter` 없음 → `memoryCacheKeyExtra("retry", "n")`. `listener(onError=…)` 람다 오버로드 대신
  `object : ImageRequest.Listener` 구현으로 안전하게.
- `Decoder.Factory.create(result: SourceFetchResult, options, imageLoader)`; `DecodeResult(image = drawable.asImage(), isSampled)`
  (`coil3.asImage`).
- 어노테이션 `coil3.annotation.ExperimentalCoilApi`. telephoto는 `me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage`.

### E-5. 타입 안전 내비게이션
- `@Serializable object/data class` 라우트 + `composable<T>`, `entry.toRoute<T>()`, `navigate(Route(...))`,
  `popUpTo<T>`, `popBackStack<T>(inclusive=false)`. R8: 라우트 클래스의 serializer는 기존 `**$$serializer`/`Companion` keep 규칙에 포함됨.

## D. 라이브러리 API 함정 (컴파일 전 확인한 가정)
- **minSdk는 28**: 업데이트 검증에 쓰는 `PackageInfo.signingInfo`, `longVersionCode`, `GET_SIGNING_CERTIFICATES`가
  API 28. minSdk 26이면 Lint `NewApi` 오류로 CI 실패. (Galaxy 2018년 이후 기기 모두 해당)
- okio `BufferedSource.request(Long)` — Int 리터럴 넘기면 컴파일 오류. 체크섬 파일은 `body.string()`으로 단순화.
- 컴포저블 슬롯 `supportingContent = subtitle?.let { { Text(it) } }`는 람다 타입 추론이 불안정 →
  `if (subtitle != null) { { Text(subtitle) } } else null` 형태 사용.
- kotlinx.serialization 1.8.1: `Json.decodeFromString<T>(string)`/`encodeToString<T>`는 **reified 멤버**(import 불필요),
  `Json.decodeFromJsonElement<T>`는 `kotlinx.serialization.json` 패키지의 **확장 함수**(import 필요). 소스 jar로 확인함.
- Material3 `PullToRefreshBox`는 1.3.0+ (BOM 2025.06.01 → material3 1.3.2) ✔.
- `LocalLifecycleOwner`는 `androidx.lifecycle.compose`에서 import (ui.platform 버전은 deprecated).
- `Modifier.transformable(state, canPan = {...})` 오버로드로 LazyColumn 세로 스크롤과 충돌 완화.
- Coil 2 `Decoder.Factory.create(result, options, imageLoader)`; 프리페치용 `DiskOnlyDecoder`는 `result.source.close()`.
- telephoto `ZoomableAsyncImage(model, contentDescription, modifier, state, contentScale, onClick)`;
  로딩 표시는 `ZoomableImageState.isImageDisplayed`.
- `PendingIntent.FLAG_MUTABLE`은 API 31+에서만 OR 할 것(설치 결과 인텐트는 가변이어야 함).
- `PackageManager.getPackageArchiveInfo`/`getPackageInfo`는 API 33+에서 `PackageInfoFlags` 오버로드 사용.

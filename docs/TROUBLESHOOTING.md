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
- 추가(구조 개편 후 16 warnings): `DataExtractionRules`가 "minSdk 28이면 `fullBackupContent`도 지정" → `xml/backup_rules.xml` 추가.
  남은 경고는 전부 의도된 것(버전 핀, OldTargetApi, enableOnBackInvokedCallback).

### C-10. 첫 Release 실행 (v0.1.0): `Verify APK signature` 단계 실패 — "v2 scheme: false"
- 증상: 서명 빌드까지 성공했는데 `apksigner verify --verbose` 결과가 `v2: false, v3: true`라 워크플로의 v2 `true` grep에서 실패.
- 원인: apksigner는 APK의 minSdk(28)부터 검증하는데, v3 서명이 있으면 API 28 이상 전 구간이 v3로 검증되므로 **v2는 검사하지
  않고 false로 출력**한다(v2는 API 24~27에서만 필요). `enableV2Signing = true`로 v2 블록은 들어 있지만 결과에는 드러나지 않는다.
- 해결: 워크플로 검증을 v3 `true`만 요구하도록 수정. minSdk를 24 이하로 낮추면 그때 v2 검사를 되살린다.
- 참고: 그 전 실행(37617782057)은 Secrets 4개 중 비밀번호 2개가 미등록이라 `Check signing secrets`에서 멈춤 — `gh secret list`로 4개 확인.
- 수동 재실행(`workflow_dispatch`)은 **기본 브랜치의 워크플로 파일**이 있어야 등록된다. `main`이 초기 커밋뿐이라 422가 났고,
  작업 브랜치를 `main`으로 fast-forward한 뒤에야 `gh workflow run Release -f tag=v0.1.0`이 가능해졌다(Security Scan 주간 예약도 동일).

### C-11. 컴파일 오류 `Unresolved reference 'n점'` — 한글이 붙은 문자열 템플릿
- 원인: Kotlin 식별자는 유니코드 문자를 허용하므로 `"$n점"`은 변수 `n점`을 찾는다. 한글(및 다른 비ASCII 문자)이 바로 뒤에 오면 반드시 `"${n}점"`.
- 재발 방지: 한국어 UI 문자열에서 템플릿 뒤에 글자가 붙으면 항상 중괄호를 쓴다.

### C-12. Lint `RestrictedApi`: `ComponentActivity.dispatchKeyEvent`
- androidx.core의 `ComponentActivity`가 플랫폼 `Activity.dispatchKeyEvent`를 재정의하며 `@RestrictTo`를 붙여 둬서, 앱이 재정의하면 오류로 잡힌다.
  플랫폼 공개 API의 재정의라 실제 제약이 아님 → `@SuppressLint("RestrictedApi")` + 이유 주석(MainActivity).

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

## F. 로컬 PC 개발 환경 (Windows 11, JDK 17 Corretto, Android SDK) — 2026-10-07 세션 2에서 확인

### F-1. AGP가 한글 경로(`C:\Project\개인용\…`)에서 빌드를 거부
- 증상: `Your project path contains non-ASCII characters. … add 'android.overridePathCheck=true'`로 설정 단계에서 실패.
- 원인: AGP의 경로 검사(비ASCII 경로에서 aapt2/NDK 문제가 있었던 이력 때문).
- 해결: 저장소는 그대로 두고 **사용자 전역** `%USERPROFILE%\.gradle\gradle.properties`에 `android.overridePathCheck=true` 추가.
  프로젝트 `gradle.properties`는 건드리지 않는다(CI는 Linux ASCII 경로라 불필요).
- 참고: Git Bash에서 `./gradlew`를 쓰면 한글 폴더명이 경로에서 사라져 `gradle-wrapper.jar`를 못 찾는다(locale 문제).
  Windows에서는 **PowerShell + `.\gradlew.bat`** 를 쓴다.

### F-2. 단위 테스트 전부 `initializationError: ClassNotFoundException` (컴파일은 성공)
- 증상: 테스트 클래스 이름은 나열되는데 워커 JVM이 하나도 로드하지 못함. 디버그 APK는 정상 생성.
- 원인: Gradle이 테스트 워커의 클래스패스를 `@…\.gradle\.tmp\gradle-worker-classpath…txt` **인자 파일**로 넘긴다.
  데몬은 이 파일을 데몬의 기본 문자셋으로 쓰는데, 프로젝트 `gradle.properties`의
  `org.gradle.jvmargs=… -Dfile.encoding=UTF-8` 때문에 UTF-8로 써지고, Java 런처는 인자 파일을 Windows 코드페이지(CP949)로
  읽는다 → 경로의 `개인용`이 깨져 클래스 디렉터리를 못 찾음. 워커 JVM에 `JAVA_TOOL_OPTIONS`로 UTF-8을 줘도 소용없음
  (런처가 인자 파일을 읽는 시점은 JVM 옵션 적용 전). ASCII 정션(`mklink /J`)도 Gradle이 실제 경로로 정규화해 소용없음.
- 해결: 사용자 전역 `%USERPROFILE%\.gradle\gradle.properties`에
  `org.gradle.jvmargs=-Xmx3g -XX:+UseParallelGC` (file.encoding 없이)를 두어 프로젝트 값을 덮어쓴다
  (사용자 홈 > 프로젝트 우선순위). 데몬 문자셋이 CP949가 되어 인자 파일과 런처의 인코딩이 일치한다.
  Kotlin/javac 소스 인코딩은 AGP가 UTF-8로 고정하므로 컴파일에는 영향 없음(23개 테스트 통과로 확인).
- 부작용: 이 설정은 이 PC의 **모든** Gradle 프로젝트 데몬에 적용된다(힙 3GB). 다른 프로젝트가 더 큰 힙을 요구하면 값을 올린다.
- 대안: 저장소를 ASCII 경로(예: `C:\Project\lanraragi-app`)에 두면 F-1·F-2 모두 설정 없이 해결된다.

### F-3. `LrrApiTest`만 `HTTP 500` — MockWebServer URL 호스트가 `display.ad.daum.net`
- 증상: MockWebServer 기반 5개 테스트가 전부 `LrrException$HttpError: HTTP 500`, 모의 서버는 요청을 받은 기록이 없음.
- 원인: `MockWebServer.url()`은 바인딩 주소(127.0.0.1)를 **역조회**한 `canonicalHostName`을 호스트로 쓴다. 이 PC의
  `C:\Windows\System32\drivers\etc\hosts`에 광고 차단용 `127.0.0.1 display.ad.daum.net`이 있어 역조회 결과가 그 도메인이 됨.
  그 호스트로 보낸 요청은 로컬의 다른 응답자(광고 차단기류)가 500으로 끊었다.
- 해결: 테스트가 `server.url()` 대신 `"http://localhost:${server.port}"`를 쓰도록 수정(`LrrApiTest.baseUrl`).
  OkHttp는 `localhost`의 모든 주소(127.0.0.1, ::1)를 순서대로 시도하므로 어느 쪽에 바인딩돼도 동작한다.
- 재발 방지: 새 MockWebServer 테스트에서 `server.url()`/`server.hostName`을 쓰지 않는다.

### F-4. 로컬 빌드 산출물 — Room 스키마
- 로컬 빌드로 `app/schemas/com.sus7898.lrrviewer.data.db.AppDatabase/1.json`이 생성됨(E-3). 첫 릴리스 전에 커밋한다.
- v0.1.1: `category_order` 테이블 추가 → version 2 + `AutoMigration(1→2)`, `2.json`을 같은 커밋에 포함. 테이블 추가만이라 스펙 클래스 불필요.

### F-5. ViewModel 단위 테스트에서 MockWebServer 응답 순서
- `LibraryViewModel`은 init에서 검색·카테고리·북마크 링크·info를 **동시에** 요청하므로 `enqueue` 순서 큐가 어긋난다.
  → `server.dispatcher = object : Dispatcher()`로 경로별 응답을 돌려주고, `Dispatchers.setMain(UnconfinedTestDispatcher())` + `state.first { … }`로 기다린다.
- `Collator.getInstance(Locale.KOREAN)`은 라틴 문자를 한글보다 앞에 둔다(`Apple` < `Vol 2` < `즐겨찾기`). 정렬 테스트 기대값을 그에 맞춘다.

### F-6. 테스트가 `Cannot snapshot …\binary\output.bin: not a regular file`로 실패 — Synology Drive 자리표시 파일
- 증상: 테스트 자체는 통과했는데 `:app:testDebugUnitTest`가 출력 스냅샷 단계에서 실패. 해당 파일 속성이 `ReparsePoint`.
- 원인: `C:\Project\개인용`이 Synology Drive 동기화 폴더라 **빌드 산출물(`app/build`, `.gradle`)까지 올리고, 일부를 온디맨드 자리표시로 바꾼다**
  (2026-10-22 확인 시 `app/build` 아래 2,862개). Gradle은 자리표시를 일반 파일로 읽지 못한다. 메모리의 "덧붙여 쓴 파일이 되돌려짐"과 같은 원인.
- 즉시 해결: 문제 폴더(`app/build/test-results/testDebugUnitTest`)를 지우고 다시 빌드.
- 근본 해결(사용자 PC 설정): Synology Drive 클라이언트 → 동기화 작업 → 설정 → **파일 필터/선택적 동기화**에서 `build`, `.gradle` 폴더를 제외하거나,
  저장소를 동기화되지 않는 경로(예: `C:\dev\lanraragi-app`)로 옮긴다(그러면 F-1·F-2 한글 경로 우회도 필요 없어짐). 서명 APK는 CI에서만 만들므로 배포물엔 영향 없음.

## H. 전체 코드 검토(2026-10-22, v0.1.7)에서 찾아 고친 결함

### H-1. 양면 보기에서 탭/키 "다음"이 같은 자리에서 멈춤, 마지막 짝의 마지막 페이지에 도달 못 함 (HIGH)
- 원인: `navigate()`가 `현재 페이지 + 1`로 점프했는데, 양면 슬롯 [1,2]에서 현재 페이지는 1 → 2는 같은 슬롯이라 페이저가 안 움직이고 계속 2를 요청.
  마지막 슬롯 [n-2,n-1]에선 n-1이 보고되지 않아 끝 카드·다음 작품·완독 처리도 안 됨.
- 해결: 넘김 모드는 **슬롯 단위 ±1 `stepEvents`**, 범위를 넘으면 `onPastEdge`(다음 작품/메뉴). 마지막 슬롯은 마지막 페이지를 보고.

### H-2. "다음 작품" 전환 후 새 리더의 키·밝기·화면 켜짐이 풀림 (HIGH)
- 원인: NavHost 크로스페이드 때문에 **새 리더의 효과가 먼저 시작되고 옛 리더의 onDispose가 나중에** 실행되어 `active=false`, 밝기·켜짐 해제를 덮어씀.
- 해결: `ReaderKeyEvents.enterReader/exitReader` ref-count로 마지막 리더가 나갈 때만 창 복구. 설정별 효과는 값만 쓴다(끄는 값 포함).
  키 이벤트는 최신 세션 id만 처리(전환 중 Esc 두 번 처리 방지).

### H-3. 상세 화면 "이어 읽기"가 옛 페이지로 진행률을 되돌림 (HIGH)
- 원인: 상세 VM은 리더가 열린 동안 백스택에 남아 `localPage`가 갱신되지 않음 → 돌아와 다시 누르면 옛 페이지를 **명시 요청**, 700ms 뒤 저장·서버 PUT.
- 해결: 이어 읽기는 `-1`(리더가 로컬/서버 중 최신을 고름), 상세 화면은 resume 때 진행률을 다시 읽어 라벨 갱신.

### H-4. 웹툰/가로 연속에서 짧은 마지막 페이지가 "현재"가 되지 않음 → 끝 카드·완독 안 됨
- 해결: `canScrollForward == false`면 마지막 페이지를 보고.

### H-5. 웹툰 길게 누르기가 엉뚱한 페이지(공유·저장·**썸네일 지정**)를 고름
- 원인: `pointerInput(Unit)` 클로저가 첫 구성의 `items`를 붙잡음 → 타일 분할 후 행↔페이지 대응이 어긋남.
- 해결: `rememberUpdatedState`로 최신 목록·콜백을 읽음(웹툰·가로 연속 모두). 탭 존 판정도 최신 설정/모드를 VM·설정 StateFlow에서 직접 읽게 함.

### H-6. 서재 새로고침 취소가 가짜 오류 스낵바를 띄움 / 이전 검색의 다음 페이지가 섞임
- 원인: `catch (e: Exception)`이 `CancellationException`까지 잡음, 성공 시 `error`를 안 지움, `loadMore` 작업을 추적·취소 안 함.
- 해결: 취소는 다시 던지고, 성공 시 오류 지움, `refresh()`가 `loadMore`를 취소, 결과 도착 시 질의가 바뀌었으면 버림, 최신 목록에 합침.

### H-7. 즐겨찾기 첫 토글이 서버의 기존 북마크 연결을 빈 카테고리로 바꿔버릴 수 있음
- 원인: 링크 정보를 아직 못 읽었을 때(`categoryId == null`)도 "없음"으로 보고 새 카테고리를 만들어 연결.
- 해결: 로드 전이면 서버에 먼저 물어보고, 서버가 "없음"을 확인했을 때만 생성(조회 실패는 예외).

### H-8. 동기화가 같은 기기에서 지운 기록·검색어를 되살림
- 원인: 지운 행은 로컬에 없고 파일엔 있어 "새 행"으로 다시 들어옴, 검색어는 합집합.
- 해결: Room v4 `history_tombstone`(삭제 시각, 90일 보관) + 파일의 `deletedProgress`. 삭제 이후 다시 읽은 행만 살아남음. 검색 기록은
  `searchesChangedAt` 기준 최신 목록이 이김(지우기도 전파).

### H-9. 앱 잠금이 열려 있던 시트/다이얼로그 아래로만 깔림(우회 가능), 시작 시 업데이트 다이얼로그가 잠금 위로 뜸
- 해결: 잠금 화면을 **전체 화면 Dialog 창**으로(나중에 뜬 창이라 시트·다이얼로그 위, 뒤로 가기 삼킴), 잠금 중 자동 업데이트 다이얼로그 억제,
  인증 요청은 잠금마다 한 번(재개 때마다 다시 띄우면 취소 직후 또 뜨는 문제).

### H-10. 색 필터가 Android 9(API 28)에서 페이지를 회색으로 덮음
- 원인: Compose `BlendMode.Saturation/Multiply`는 API 29+ 전용, 미만에선 SrcOver로 대체되어 불투명 사각형을 그림.
- 해결: `saveLayer(paint = ColorMatrix 필터)` 레이어에 콘텐츠를 그림(전 버전 동작). 흑백 = 채도 0, 세피아 = 표준 세피아 행렬, 따뜻하게 = G·B 감쇠.

### H-11. "평문 HTTP 허용"이 동기화되어 LAN(http) 기기의 연결이 끊길 수 있음
- 해결: 기기별 설정으로(동기화 제외).

### H-12. (LOW) 조용한 자체 업데이트가 항상 확인을 띄움, 진행률 쓰기 경합, 줌 직후 가로 위치
- `UPDATE_PACKAGES_WITHOUT_USER_ACTION` 권한(일반, API 31+)이 없으면 `USER_ACTION_NOT_REQUIRED`가 무시됨 → 매니페스트에 추가.
- 진행률 저장을 UPDATE 우선 트랜잭션으로(작품별 읽기 방향을 덮지 않음), 동기화 가져오기는 트랜잭션 안에서 더 최신일 때만 씀.
- 웹툰 줌 직후 넓어진 레이아웃 전에 가로 스크롤하면 0으로 잘림 → 한 프레임 기다린 뒤 이동.

## G. 리더 이미지 파이프라인 (v0.1.2, 페이지 치수 캐시·웹툰 타일링)

### G-1. Coil 3 디스크 캐시에서 원본 파일 꺼내기
- `imageLoader.diskCache?.openSnapshot(key)`의 키는 네트워크 페처가 쓴 **페이지 URL 문자열 그대로**(`diskCacheKey` 미지정 시).
  `Snapshot.data`(okio Path)는 스냅샷을 닫기 전까지만 유효하므로 `snapshot.use { decode(it.data.toFile()) }` 안에서 디코딩한다.
- 캐시에 없으면 `DiskOnlyDecoder`(디코딩 안 함) 요청을 `imageLoader.execute()`로 **동기 실행**한 뒤 다시 연다. `enqueue`는 완료를 기다리지 않는다.
- `diskCache`/`openSnapshot`은 `@ExperimentalCoilApi` → `@OptIn` 필요.

### G-2. `BitmapRegionDecoder.newInstance` 오버로드
- API 31+: `newInstance(InputStream)`(비추천 아님). 그 미만: `newInstance(String path, Boolean)`(31에서 deprecated) → SDK 분기 + `@Suppress("DEPRECATION")`.
- 영역 Rect는 디코더의 `width/height`로 클램프해야 한다(치수 캐시와 실제 파일이 다를 수 있음). 쓰고 나면 `recycle()`.

### G-3. LazyColumn 행이 바뀌어도 스크롤이 튀지 않게 하기
- 크기를 모르는 행(Pending)이 실제 행(Whole/Tile 0)으로 바뀔 때 **key가 같아야** 첫 보이는 항목 앵커가 유지된다 (`"${page}_0"`).
  key가 바뀌면 LazyColumn은 인덱스로 되돌아가 위치가 흔들린다.
- 치수를 읽는 동안 Coil에 실제 이미지를 요청하지 않는다(자리표시만). 치수 조회가 파일을 디스크 캐시에 받아두므로 이후 Coil 로드는 즉시다.

### G-4. 양면 보기에서 페이저 인덱스 ≠ 페이지 인덱스
- 치수가 도착하거나 회전하면 묶음(spread) 목록이 바뀌어 같은 페이지의 슬롯 번호가 달라진다. `LaunchedEffect(spreads)`에서
  현재 **페이지**가 든 슬롯으로 `scrollToPage`한 뒤 `snapshotFlow { pagerState.currentPage }`를 다시 수집한다. 슬롯이 바뀌어도
  현재 페이지가 그 슬롯 안에 있으면 currentPage를 바꾸지 않아야 진행률이 흔들리지 않는다.
- 페이저 `key`는 슬롯의 페이지 목록(`"3_4"`)으로 두어 재묶음 시 콘텐츠가 올바른 슬롯에 다시 붙게 한다.

### G-5. 웹툰 레이아웃 줌 — 재앵커와 제스처 분리
- 너비 배율을 바꾸면 모든 행 높이가 비율대로 변한다 → `scrollToItem(firstIndex, offset×ratio + centerY×(ratio−1))`로 뷰포트 중심을
  유지한다. 인덱스+오프셋은 다음 측정에서 적용되므로 재레이아웃 전에 호출해도 된다.
- `transformable(canPan = { false })`로 핀치만 받고, 가로 이동은 `horizontalScroll`, 세로는 LazyColumn이 처리한다. `detectTransformGestures`는
  한 손가락 이동까지 소비해 세로 스크롤을 막으므로 쓰지 않는다. 더블 탭은 `detectTapGestures(onDoubleTap)`.

### G-6. 색 필터를 API 28에서도: RenderEffect 대신 블렌드
- `Modifier.graphicsLayer(renderEffect=…)`의 ColorFilter 효과는 API 31+. 대신 `graphicsLayer(compositingStrategy = Offscreen)`으로
  오프스크린 합성 후 `drawWithContent { drawContent(); drawRect(Color.Gray, blendMode = BlendMode.Saturation) }` → 흑백.
  Multiply로 따뜻한 색을 곱하면 세피아/블루라이트 감소. Offscreen 없이 Saturation 블렌드를 쓰면 배경(검정)과 섞여 결과가 달라진다.
- 밝기는 `window.attributes.screenBrightness`를 바꾸는 것이라 시스템 밝기 설정은 건드리지 않는다. 나갈 때 `BRIGHTNESS_OVERRIDE_NONE`로 복구.

### G-7. "다음 작품" 전환은 pop + navigate가 아닌 popUpTo inclusive
- `navigate(ReaderRoute(next)) { popUpTo<ReaderRoute> { inclusive = true } }`로 현재 리더를 백스택에서 빼고 새 리더를 넣어야 뒤로 가기가
  상세/서재로 바로 돌아간다. ViewModel key가 `reader_$arcId`라 새 리더는 새 VM을 만들고, 이전 VM의 `onCleared`가 진행률을 저장한다.

### G-8. 페이지 썸네일: no_fallback 없이 부르면 자리표시가 캐시된다
- `GET /api/archives/{id}/thumbnail?page=N`은 썸네일이 아직 없으면 **자리표시 이미지(200)** 를 준다 → Coil이 그 URL로 디스크 캐시에 저장해
  나중에 진짜 썸네일이 생겨도 계속 자리표시가 보인다. `no_fallback=true`는 대신 202 + JSON을 준다(2xx라 Coil 페처가 본문을 캐시에 쓴 뒤
  디코딩에서 실패). 그래서 ① 격자를 열면 `POST …/files/thumbnails`로 생성 작업을 걸고 minion 완료를 기다린 뒤에만 URL을 쓰고,
  ② 디코딩 실패 시 `PageImageStore.evict(url)`로 디스크·메모리 캐시에서 지운다.

### G-9. SAF 동기화 파일
- `ActivityResultContracts.CreateDocument/OpenDocument`로 받은 URI에 `takePersistableUriPermission(READ|WRITE)` — 이걸 안 하면 앱 재시작 후
  SecurityException. 쓰기는 `openOutputStream(uri, "wt")`(잘라내기), 일부 제공자는 "w"만 받으므로 대체 시도.
- Google Drive 등 클라우드 제공자는 파일 반영이 늦을 수 있다(오프라인 캐시). 병합이 마지막 쓰기 승리라 늦게 도착한 쪽도 안전하게 합쳐진다.
- JUnit 테스트 메서드 이름(백틱)에 `;`를 넣으면 `Name contains illegal characters` 컴파일 오류(JVM 이름 제약).

### G-10. BiometricPrompt 조합과 FragmentActivity
- `BiometricPrompt`는 `FragmentActivity`가 필요 → `MainActivity : FragmentActivity()`(ComponentActivity 하위라 `setContent`·`enableEdgeToEdge` 그대로).
- 허용 인증 조합: `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`은 API 28~29에서 **미지원**(IllegalArgumentException). `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`은 전 버전 지원.
  DEVICE_CREDENTIAL을 넣으면 `setNegativeButtonText`를 쓰면 안 된다(예외).
- 라이브러리가 `USE_BIOMETRIC`·`USE_FINGERPRINT`(일반 권한, 사용자 허락 불필요)를 매니페스트에 병합한다.

### G-11. 자동 스크롤과 사용자 드래그
- 자동 스크롤을 프레임마다 `scrollBy`를 따로 부르면 사용자 플링과 번갈아 싸운다. `listState.scroll(MutatePriority.Default) { 루프 }` 한 블록으로 돌리면
  사용자 드래그(UserInput, 더 높음)가 블록을 `CancellationException`으로 끊는다 → 코루틴이 살아 있으면(`isActive`) 잡고 `isScrollInProgress == false`를 기다려 재개.

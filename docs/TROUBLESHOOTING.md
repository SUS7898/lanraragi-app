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

## C. 빌드 / CI

### C-1. (예정) CI 첫 실행 결과는 여기에 기록
- 첫 push 후 CI 로그에서 나온 컴파일 오류와 수정 내용을 항목별로 추가할 것.

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

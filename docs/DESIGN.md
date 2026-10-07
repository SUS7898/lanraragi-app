# 설계 문서 (DESIGN)

> 결정 사항은 "무엇을 / 왜 / 대안" 형식으로 기록한다. 바꾸면 여기부터 고친다.

## 1. 목표와 범위
- 사용자의 NAS에서 컨테이너로 도는 **LANraragi** 전용 Android 뷰어 (Galaxy 폰/태블릿).
- Mihon + LANraragi 확장에서 일부 아카이브가 `NumberFormatException: For input string: ""`로
  열리지 않는 문제를 근본적으로 피한다.
- Google Play 미배포. 설치 경고 최소화(일관된 릴리스 키 서명, v2/v3), 앱 내 업데이트.
- **스트리밍 열람**: 아카이브 전체 다운로드 없이 페이지 단위로 받아오고, 몇 장 미리 받기.
- CI에 보안 취약점 점검 단계 포함.

## 2. Mihon 오류의 원인 (조사 결과)
keiyoushi `extensions-source` LANraragi 확장 `LANraragi.kt` 277행:
```kotlin
val date = 1000 * (getNSTag(arc.tags, "date_added")?.first()?.toLong() ?: 0)
```
태그가 `date_added:` 처럼 값이 비어 있으면 `"".toLong()` → NumberFormatException.
그 외에도 `pagecount`/`progress`가 `""`나 문자열로 오는 서버/플러그인 조합이 있다.
→ 이 앱은 모든 숫자/불리언을 `LenientXxxSerializer`로 파싱하고, 검색 목록은 항목별로
개별 디코딩해서 **깨진 항목 하나가 목록 전체를 막지 않게** 한다 (`LrrApi.decodeArchives`).
회귀 테스트: `ModelsParsingTest`, `LrrApiTest`.

## 3. 아키텍처
- 단일 모듈 `:app`, Kotlin 2.1 + Jetpack Compose(Material3), 수동 DI(`AppGraph`).
- 상태: `StateFlow` + `ViewModel`. 설정은 DataStore(Preferences). 읽기 기록은 DataStore에 JSON.
- 네트워크: OkHttp 단일 클라이언트. `LrrAuthInterceptor`가 **설정된 서버 호스트에만**
  `Authorization: Bearer base64(apiKey)` 부여(GitHub 등 다른 호스트로 키 유출 방지),
  "평문 HTTP 허용" 설정을 런타임에 강제.
- 이미지: Coil 2 + telephoto(`ZoomableAsyncImage`, 대형 이미지 서브샘플링/핀치 줌).
  Coil 디스크 캐시(기본 512MB, 설정 가능)에 페이지를 저장 → 재열람 시 네트워크 0.
  `respectCacheHeaders(false)`: LRR가 캐시 헤더를 주지 않기 때문.
- 미리 받기: 다음 N장(기본 3)을 `DiskOnlyDecoder`(디코딩 안 함)로 enqueue → 비트맵 메모리
  낭비 없이 디스크 캐시만 채움.
- 내비게이션: Navigation Compose. 라우트 `setup`, `home`(탭: 서재/기록/설정), `archive/{id}`,
  `reader/{id}?page={page}`.
- 하드웨어 볼륨키: `MainActivity.onKeyDown/Up` → `ReaderKeyEvents`(SharedFlow) → 리더.

## 4. LANraragi API 메모 (소스 `tools/openapi.yaml`, `Controller/Api/*.pm` 확인)
| 용도 | 엔드포인트 | 비고 |
|---|---|---|
| 서버 정보 | `GET /api/info` | `server_tracks_progress`, `nofun_mode`, `archives_per_page`; 구버전은 불리언이 0/1 |
| 검색 | `GET /api/search?filter&category&start&sortby&order&newonly&untaggedonly&hidecompleted&groupby_tanks` | 응답 `{data:[Archive], recordsFiltered, recordsTotal}`; `start`는 오프셋, 페이지 크기는 서버 설정 |
| 무작위 | `GET /api/search/random?count&filter&category&newonly&untaggedonly&groupby_tanks` | 응답 `{data:[...]}` |
| 메타데이터 | `GET /api/archives/{id}/metadata` | `toc`는 `[{name,page}]` |
| 페이지 목록 | `GET /api/archives/{id}/files[?force=true]` | 최신: `{pages:[...]}` 동기 반환. 구버전: `{job, pages:[]}` → `/api/minion/{job}` 폴링 |
| 페이지 이미지 | `GET /api/archives/{id}/page?path=...` | `files`가 준 경로(`./api/...`)를 그대로 사용. 리버스프록시 서브패스 포함됨 → `LrrUrls.resolvePageUrl` |
| 썸네일 | `GET /api/archives/{id}/thumbnail`, `GET /api/tankoubons/{id}/thumbnail` | |
| 진행률 | `PUT /api/archives/{id}/progress/{page}` | **1-based**. 서버가 진행률 추적 꺼져 있으면 오류 → 무시 |
| NEW 플래그 | `DELETE/PUT /api/archives/{id}/isnew` | |
| 카테고리 | `GET /api/categories` | `pinned`가 0/1 또는 문자열 |
| 탄코본 | `GET /api/tankoubons/{id}/full` | `{result:{id,name,summary,tags,archives,full_data:[Archive]}}` |
| 인증 | 헤더 `Authorization: Bearer base64(api_key)` | No-Fun 모드면 모든 API에 필요 |

`arcid`가 `TANK_`로 시작하면 탄코본(묶음). 검색 결과에 섞여 나옴(`groupby_tanks=true`).

## 5. 뷰어 기능 (구현됨)
읽기 방향 LTR/RTL/세로 넘김/웹툰(연속 스크롤) · 핀치/더블탭 줌(telephoto) · 이미지 맞춤
(화면/가로/세로) · 배경색 · 탭 존(가장자리 이전/다음, 가운데 메뉴) · 볼륨키 · 화면 켜짐 유지 ·
회전 잠금 · 페이지 슬라이더(RTL 반전) · 목차(TOC) 점프 · 페이지별 재시도 · 미리 받기 ·
로컬+서버 진행률 저장/이어 읽기 · NEW 자동 해제 · 몰입 모드(시스템 바 숨김).

## 6. 자체 업데이트 설계
1. `GET https://api.github.com/repos/{owner}/{repo}/releases/latest` (하루 1회 자동 + 수동).
2. 태그 `vX.Y.Z` → `Version` 비교 (`BuildConfig.VERSION_NAME`).
3. APK 자산 스트리밍 다운로드하며 SHA-256 계산 → `SHA256SUMS.txt`(또는 GitHub 자산 `digest`)와 대조.
   체크섬이 없으면 설치 거부.
4. `PackageManager.getPackageArchiveInfo(GET_SIGNING_CERTIFICATES)`로 패키지명·versionCode·
   **서명 인증서 SHA-256이 실행 중인 앱과 동일**한지 확인.
5. `PackageInstaller` 세션으로 설치. `USER_ACTION_NOT_REQUIRED`(API 31+) → 이 앱이 "설치자"가
   된 이후(두 번째 자체 업데이트부터)는 확인 대화상자 없이 조용히 갱신될 수 있음.
   첫 자체 업데이트는 "알 수 없는 앱 설치" 권한 1회 허용 + 시스템 확인 화면.
- 디버그 빌드는 `applicationId`에 `.debug` 접미사 → 릴리스 APK와 패키지가 달라 업데이트 불가(의도).
- 버전 번호는 **태그에서 파생**(`-PappVersionName/-PappVersionCode`), 코드에 하드코딩하지 않음.
  versionCode = major*1_000_000 + minor*1_000 + patch.

## 7. 서명/설치 경고에 대한 사실
- Play 미배포 앱은 Android가 "출처를 알 수 없는 앱" 1회 허용(설치 주체 앱별) 및 Play Protect
  검사 안내를 띄운다. 이는 **서명으로 없앨 수 없다**. 할 수 있는 최소화:
  - 릴리스 키로 일관 서명(v2+v3, v4 idsig 동봉; v1은 minSdk 28이라 불필요/비활성) → "업데이트"
    로 인식되어 재설치/데이터 손실 없음.
  - 첫 설치 후 앱 자체가 업데이트를 설치 → 브라우저/파일앱 경고 대신 앱 내 흐름.
  - targetSdk 최신, 불필요 권한 없음, `allowBackup=false`, 평문 트래픽은 설정으로 제한.
- Play Protect "알 수 없는 개발자" 경고는 Galaxy에서도 표시될 수 있음(무시 가능).

## 8. 보안 설계 요약
- API 키: Android Keystore AES-256-GCM(`SecureStore`)으로 암호화해 DataStore에 저장.
- 네트워크 보안 구성: 시스템 CA + **사용자 설치 CA** 신뢰(자체 서명 HTTPS NAS 지원), 평문 허용
  (LAN 전제) + 앱 설정으로 차단 가능. 외부 노출 서버는 HTTPS 권고(UI 경고).
- 컴포넌트 노출 최소: 런처 Activity만 exported, `taskAffinity=""`(태스크 하이재킹 완화). FileProvider는 `cache/updates/`만.
- R8 minify + 리소스 축소, `dependenciesInfo` 비활성(Play 전용 암호화 블롭 제거).
- CI: CodeQL, Trivy(의존성/시크릿/설정), gitleaks, Android Lint(SARIF), mobsfscan,
  Dependency Review. Release 워크플로는 Trivy CRITICAL/HIGH(수정판 존재)에서 중단.

## 9. 버전 핀 이유
클라우드 세션에서 로컬 빌드가 안 되므로 "확실히 존재하고 서로 호환되는" 조합을 고정했다.
AGP 8.10.1(Gradle ≥ 8.11.1 요구) + Gradle 8.14.3 + Kotlin 2.1.21(Compose 컴파일러 플러그인 동일 버전).
telephoto `zoomable-image-coil`은 Coil **2.x**용 아티팩트(Coil 3용은 `zoomable-image-coil3`).
올릴 때는 반드시 CI 통과를 확인하고 `docs/TROUBLESHOOTING.md`에 결과를 남긴다.

## 10. 보류/미구현 (아이디어)
- 오프라인 다운로드(아카이브 통째 저장) — 요구사항이 스트리밍이라 제외. 캐시가 대체.
- 양면 보기(태블릿 가로), 여백 자르기, 이미지 저장/공유, 즐겨찾기(카테고리 편집 API).
- 서버 여러 대 프로필.

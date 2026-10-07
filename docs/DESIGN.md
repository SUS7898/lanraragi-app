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

## 3. 아키텍처 (세션 1 후반 구조 개편 반영)
- 단일 모듈 `:app`, Kotlin 2.1 + Jetpack Compose(Material3), **수동 DI(`AppGraph`)**.
  Hilt/Koin·멀티모듈은 이 규모에서 비용만 늘려 **채택하지 않음**(결정). ViewModel은 `AppGraph` 전체가 아니라
  필요한 의존성만 생성자로 받는다(`LibraryViewModel(api, settings)`, `ReaderViewModel(Deps, …)`) → JVM 테스트 가능.
- 상태: `StateFlow` + `ViewModel`. 설정은 DataStore(Preferences). **읽기 기록/진행률은 Room**(`data/db/AppDatabase.kt`,
  `reading_progress` 테이블, PK `(serverId, arcid)`). JSON 블롭 저장은 폐기(릴리스 전이라 마이그레이션 없음).
- **데이터 식별자**: 모든 로컬 레코드는 `serverId`를 가진다(현재는 상수 `DEFAULT_SERVER_ID`). 서버 프로필 여러 개를
  지원할 때 스키마 변경 없이 `ReadingProgressRepository(dao, serverId)`만 동적으로 바꾸면 된다.
  `readingModeOverride` 컬럼 = 작품별 읽기 방향(바텀바 빠른 전환은 "이 작품만", 설정 시트는 기본값).
- 네트워크: OkHttp 단일 클라이언트. `LrrAuthInterceptor`가 **설정된 서버 호스트에만**
  `Authorization: Bearer base64(apiKey)` 부여(GitHub 등 다른 호스트로 키 유출 방지),
  "평문 HTTP 허용" 설정을 런타임에 강제.
- 이미지: **Coil 3**(`coil3-compose` + `coil-network-okhttp`, API용 OkHttp 클라이언트 공유) + telephoto
  `zoomable-image-coil3`(대형 이미지 서브샘플링/핀치 줌). Coil 3는 기본적으로 HTTP 캐시 헤더를 무시하므로
  별도 설정 없이 LRR 페이지가 디스크 캐시(기본 512MB)에 남는다. Coil 2는 유지보수 모드라 초기에 이전함(결정).
- 미리 받기: 다음 N장(기본 3)을 `DiskOnlyDecoder`(디코딩 안 함)로 enqueue → 비트맵 메모리 낭비 없이 디스크 캐시만 채움.
- 내비게이션: Navigation Compose **타입 안전 라우트**(`@Serializable` `SetupRoute`/`HomeRoute`/`ArchiveRoute(id)`/
  `ReaderRoute(id, page)`, `ui/AppRoot.kt`). 문자열 라우트 금지.
- 리더 구성: `ReaderScreen`(호스트·크롬·시스템 UI·키) / `PagedReader`(LTR·RTL·세로, telephoto) /
  `WebtoonReader`(연속 스크롤) / `ReaderGestures`(탭 존) / `ReaderSettingsSheet`.
- 하드웨어 볼륨키: `MainActivity.onKeyDown/Up` → `ReaderKeyEvents`(SharedFlow) → 리더.
- **즐겨찾기(좋아요)** = LANraragi 북마크 기능(`/api/categories/bookmark_link`에 연결된 정적 카테고리). `data/FavoritesRepository`가
  링크된 카테고리 id와 멤버 arcid 집합을 들고 서재·상세가 공유한다. 서버에 저장되므로 폰/태블릿/웹 UI가 같은 하트를 본다.
  링크가 없으면 첫 토글 때 "즐겨찾기" 정적 카테고리를 만들어 연결한다(🔑). 북마크 API가 없는 구서버(404)는 기능을 숨긴다.
- **평점** = 아카이브 태그 `rating:1..5`(앱 규약). 서버에 태그로 저장되어 기기 간 공유·웹 검색(`rating:5`)·서버 정렬(`sortby=rating`)이
  된다. 쓰기는 `PUT /api/archives/{id}/metadata`에 title/tags/summary **세 값을 항상 함께**(방금 읽은 메타데이터 기준, form body) 보내
  서버가 생략 필드를 비우는 일을 막는다. "N점 이상" 필터는 서버 검색 문법에 OR가 없어 `start=-1`(전체)로 받아 클라이언트에서 거른다.
  로컬 전용 평점은 폰/태블릿이 갈라지므로 채택하지 않았다(결정).
- **카테고리 순서**: 서버에는 순서 개념이 없어(pinned만) 로컬 Room `category_order(serverId, categoryId, position)`에 저장.
  표시 정렬 `CategorySort` = 이름순(기본, 📌 먼저, 자연수 정렬 `NaturalOrder`) / 서버 순서 / 수동. 드래그 편집은 `sh.calvin.reorderable`.
- **탭 존은 화면이 아니라 표시된 이미지 기준**(`zoneOf(contentBounds)`): telephoto `transformedContentBounds`를 뷰포트에 클램프해
  좌우 30%/가운데 40%로 나누고, 이미지 밖 여백은 가까운 가장자리로 친다. 웹툰 모드의 탭/키는 항목 점프가 아니라 뷰포트 90% 스크롤.
- **뒤로 가기 계층**: 리더/상세 → pop, 기록/설정 탭 → 서재 탭, 서재에 검색·필터가 있으면 → 초기화, 그 다음에야 앱 종료(`BackHandler`).
- **크래시 로그**: `CrashLog`가 `files/crash/last-crash.txt`에 마지막 미처리 예외를 남기고 설정 → 정보에서 보기/공유/삭제. 외부 전송 없음.
- 의도적으로 하지 않은 것: 멀티모듈, DI 프레임워크, `strings.xml` 분리(개인용·한국어 단일; 공개 배포 시 재검토).

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
| 북마크 링크 | `GET /api/categories/bookmark_link` → `{category_id}` (""=없음), `PUT /api/categories/bookmark_link/{id}` 🔑 | 구서버는 404 |
| 카테고리 편집 | `PUT /api/categories?name&pinned` 🔑 → `{category_id}`, `PUT/DELETE /api/categories/{catId}/{arcid}` 🔑 | 정적 카테고리의 `archives`에 멤버 arcid |
| 메타데이터 수정 | `PUT /api/archives/{id}/metadata` (title, tags, summary) 🔑 | **덮어쓰기** — 세 값을 항상 함께 보낼 것(form body) |
| 전체 검색 | `GET /api/search?start=-1` | 0.8.2+: 페이지 없이 전체 결과. `sortby`는 임의 네임스페이스 허용(`rating`, `artist`…) |
| 인증 | 헤더 `Authorization: Bearer base64(api_key)` | No-Fun 모드면 모든 API에 필요 |

`arcid`가 `TANK_`로 시작하면 탄코본(묶음). 검색 결과에 섞여 나옴(`groupby_tanks=true`).

## 5. 뷰어 기능 (구현됨)
읽기 방향 LTR/RTL/세로 넘김/웹툰(연속 스크롤) · 핀치/더블탭 줌(telephoto) · 이미지 맞춤
(화면/가로/세로) · 배경색 · 탭 존(가장자리 이전/다음, 가운데 메뉴) · 볼륨키 · 화면 켜짐 유지 ·
회전 잠금 · 페이지 슬라이더(RTL 반전) · 목차(TOC) 점프 · 페이지별 재시도 · 미리 받기 ·
로컬+서버 진행률 저장/이어 읽기 · NEW 자동 해제 · 몰입 모드(시스템 바 숨김).

## 5b. 서재 · 평점 · 즐겨찾기 (세션 2, v0.1.1)
정렬 추가일/제목/최근 읽음/평점/작가/시리즈/그룹 + 임의 네임스페이스 입력 · 즐겨찾기 칩(북마크 카테고리) · 평점 N점 이상 칩 ·
카드에 ♥/★N 배지 · 상세 화면 하트 토글 + 별 5개 · 카테고리 정렬 모드와 드래그 순서 편집 화면 · 키보드 페이지 키 ·
펀치홀 컷아웃 영역까지 그리기 · 뒤로 가기 계층 · 서버 응답 오류로 건너뛴 항목 수 표시.

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
  - 릴리스 키로 일관 서명(v3 필수, v2 블록도 넣지만 minSdk 28에서는 사용되지 않음, v4 idsig 동봉; v1은 불필요/비활성)
    → "업데이트"
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
AGP 8.10.1(Gradle ≥ 8.11.1 요구) + Gradle 8.14.3 + Kotlin 2.1.21(Compose 컴파일러 플러그인 동일 버전)
+ KSP **2.1.21-2.0.1**(Kotlin 버전과 접두사가 정확히 일치해야 함) + Room 2.7.1(KSP2 지원).
Coil **3.2.0**: telephoto 0.16.0의 `zoomable-image-coil3` POM이 coil-compose 3.2.0 · Kotlin 2.1.21 · Compose 1.8.0에
의존함을 Maven Central에서 확인하고 맞춤. 올릴 때는 반드시 CI 통과를 확인하고 `docs/TROUBLESHOOTING.md`에 결과를 남긴다.

## 10. 보류/미구현 (아이디어)
- 오프라인 다운로드(아카이브 통째 저장) — 요구사항이 스트리밍이라 제외. 캐시가 대체.
- 양면 보기(태블릿 가로), 여백 자르기, 이미지 저장/공유, 즐겨찾기(카테고리 편집 API).
- 서버 여러 대 프로필.

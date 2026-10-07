# 개선 백로그 (BACKLOG)

> 세션 1(2026-10-07) 종료 시점에 "추가로 필요하거나 있으면 좋은 것"을 검토해 정리한 목록.
> **구현은 아직 하지 않았다.** 로컬(Android Studio)에서 착수할 때 이 문서의 항목을 골라 진행하고,
> 끝나면 체크 표시 + `docs/PROGRESS.md` 갱신, 설계가 바뀌면 `docs/DESIGN.md`, 문제를 겪으면
> `docs/TROUBLESHOOTING.md`에 기록한다.
>
> 우선순위: **P0** 첫 릴리스 전후 반드시 · **P1** 체감이 큰 편의 개선 · **P2** 보안/견고성 · **P3** 장기/선택
> 난이도: 하(반나절 이내) · 중(하루 내외) · 상(여러 날, 설계 필요)

---

## P0 — 첫 릴리스 전후 반드시 할 것

### P0-1. 실기기 수동 테스트 (릴리스 빌드로!)
- 왜: 이 세션은 컴파일·단위 테스트·린트·보안 스캔까지만 검증했다. **R8 minify가 켜진 릴리스 APK는
  디버그와 다른 코드**다(kotlinx.serialization/리플렉션 keep 규칙 누락 시 런타임 크래시 가능).
- 어떻게: `docs/TEST_CHECKLIST.md` 순서대로. 반드시 `v0.1.0` 태그로 만든 **서명 릴리스 APK**로 테스트.
  크래시가 나면 Release 자산의 `mapping-vX.Y.Z.txt`로 스택을 복원(`retrace`).
- 난이도: 하(테스트) / 크래시 수정은 상황에 따라.
- 주의: 디버그 빌드는 패키지가 `.debug`라 자체 업데이트 검증이 의도적으로 실패한다. 업데이트 흐름은 릴리스로만 테스트 가능.

### P0-2. 키스토어 생성 · GitHub Secrets · 첫 태그
- `scripts/generate-keystore.sh` → Secrets 4개 → `git tag v0.1.0 && git push origin v0.1.0`.
- 키스토어 백업을 2곳 이상에(비밀번호 관리자 + 오프라인). 분실 시 기존 설치본 위 업데이트 불가.

### P0-3. Galaxy "자동 차단(Auto Blocker)" 안내 추가 (README)
- 왜: One UI 6 이상 Galaxy는 **자동 차단**이 켜져 있으면 Play/Galaxy 스토어 외 설치가 막힌다. 첫 설치뿐 아니라
  **앱 내 자체 업데이트(PackageInstaller)도 차단**될 수 있다.
- 어떻게: README 설치 절차에 "설정 → 보안 및 개인정보 보호 → 자동 차단 끄기(또는 설치 후 다시 켜기)" 추가.
  업데이트 실패 시 `UpdateManager.State.Error` 메시지에 자동 차단 힌트 문구를 넣는다(`onInstallResult`에서
  `STATUS_FAILURE_BLOCKED`면 "Galaxy 자동 차단 또는 기기 정책이 설치를 막았습니다" 안내).
- 난이도: 하.

### P0-4. 웹툰 모드 초대형 세로 이미지(스트립) 처리
- 왜: 웹툰식 아카이브는 한 장이 800×20000px 같은 세로 스트립인 경우가 많다. 현재 웹툰 모드는
  `SubcomposeAsyncImage(fillMaxWidth)`로 **원본 크기 비트맵**을 만들 수 있어(높이 무제한) GPU 텍스처 한계
  (기기별 4096~16384px)나 메모리 한계를 넘으면 빈 화면/"Bitmap too large"/OOM이 난다.
  페이지 넘김 모드는 telephoto가 서브샘플링(타일링)하므로 안전하다.
- 어떻게(2단계):
  1. **빠른 완화**: 웹툰 항목에 Coil `size(Size(screenWidthPx, Dimension.Undefined))` + 최대 높이 캡
     (`Precision.INEXACT`)으로 다운샘플. 화질은 떨어지지만 깨지지는 않는다.
  2. **제대로**: Mihon처럼 **세로로 타일 분할**. 디스크 캐시의 원본 파일을 `BitmapRegionDecoder`로 열어
     높이 ≤ 2048px 조각으로 나눠 LazyColumn 항목을 조각 단위로 구성(`WebtoonPage` → `WebtoonStrip`).
     이미지 치수는 `BitmapFactory.Options.inJustDecodeBounds`로 먼저 읽는다(원본은 Coil 디스크 캐시
     `imageLoader.diskCache?.openSnapshot(url)`에서 가져오면 재다운로드 없음).
  - 대안: 항목마다 telephoto `ZoomableAsyncImage(gesturesEnabled = false)` 사용(서브샘플링 유지). 단, 항목
    높이를 미리 알아야 하므로(치수 선조회) 결국 1차 치수 읽기 로직은 필요.
- 관련 파일: `ui/reader/ReaderScreen.kt` (`WebtoonReader`, `WebtoonPage`), `ReaderViewModel.prefetchAround`.
- 난이도: 1단계 하, 2단계 상.

---

## P1 — 체감이 큰 뷰어/서재 편의 기능

### P1-1. 태블릿 가로 양면 보기(spread)
- 왜: Galaxy Tab 가로에서 한 장만 보면 여백이 크다. 만화는 두 쪽 펼침이 자연스럽다.
- 어떻게: `PagedReader`에서 `spreads = buildSpreads(pages, aspectRatios)`로 (1장|2장) 묶음 리스트를 만들고
  `HorizontalPager(pageCount = spreads.size)`. RTL이면 Row 안에서 [오른쪽=앞 페이지]. 옵션: 표지 단독 시작
  (오프셋 1), 가로로 긴 이미지(aspect > 1)는 단독. 가로 모드·`WindowSizeClass` Expanded일 때만 활성.
  현재 페이지/진행률은 spread의 첫 페이지 인덱스로 환산해 `onPageChanged`.
- 관련: `ReaderScreen.kt`, `AppSettings`(spread 모드/표지 단독 설정), `ReaderSettingsSheet`.
- 난이도: 중. 이미지 치수 선조회(P0-4와 공유) 필요.

### P1-2. 읽기 방향 자동 감지 (아카이브별 기억은 구현됨)
- 구현됨: `reading_progress.readingModeOverride` 컬럼, 바텀바 빠른 전환 = 이 작품만, 설정 시트에 "이 작품만/기본값" 선택.
- 남은 것: 첫 페이지 치수를 읽어 세로/가로 비율 > 2.5면 웹툰 모드 제안(스낵바 "웹툰 모드로 볼까요?").
- 난이도: 하.

### P1-3. 밝기 · 색 필터(야간/세피아/흑백) · 블루라이트
- 어떻게: 리더 설정 시트에 밝기 슬라이더(`window.attributes.screenBrightness` 0.01~1, -1=시스템),
  `ColorFilter.colorMatrix`로 흑백/세피아/따뜻한 색조, 또는 반투명 오버레이 Box(검정/주황)로 어둡게.
  `AppSettings`에 저장.
- 난이도: 하.

### P1-4. 마지막 페이지에서 "다음 작품" 이어가기
- 왜: 시리즈를 연달아 볼 때 서재로 돌아가는 왕복이 번거롭다.
- 어떻게: 리더 진입 시 "컨텍스트 목록"(현재 서재 검색 결과 ID 리스트 또는 탄코본 수록 순서)을 넘긴다
  (`graph`에 `readerQueue: List<String>` 보관). 마지막 페이지 뒤에 "다음: 제목" 카드 페이지를 하나 더 두고 탭하면
  `reader/{nextId}`로 교체(`popUpTo` 현재 리더). 탄코본이면 `/api/tankoubons/{id}/full`의 순서를 사용.
- 난이도: 중.

### P1-5. 페이지 썸네일 그리드로 점프
- 어떻게: LANraragi `GET /api/archives/{id}/thumbnail?page=N`(없으면 `POST /api/archives/{id}/files/thumbnails`로
  생성 요청 후 minion 폴링). 바텀바 "격자" 버튼 → `ModalBottomSheet`에 `LazyVerticalGrid`.
- 난이도: 중. 서버 버전에 따라 page 썸네일 미지원일 수 있으니 실패 시 숨김.

### P1-6. 북마크 / 카테고리에 추가
- 왜: 서버의 카테고리(정적)로 "나중에 읽기"를 관리할 수 있다. 최신 LRR에는 **북마크 전용 카테고리 링크**가 있다.
- 어떻게: `GET /api/categories/bookmark_link` → 북마크 카테고리 ID. 상세 화면에 북마크 토글 버튼:
  `PUT /api/categories/{catId}/{arcid}` / `DELETE /api/categories/{catId}/{arcid}`. "카테고리에 추가…" 메뉴에
  정적 카테고리 목록(`Category.isDynamic == false`)과 새 카테고리 만들기(`PUT /api/categories`, form `name`).
  모두 🔑 API 키 필요 → 키 없으면 버튼 비활성 + 안내.
- 관련: `LrrApi.kt`(메서드 추가), `ArchiveDetailScreen.kt`.
- 난이도: 중.

### P1-7. 태그 자동완성 · 최근 검색어
- 어떻게: `GET /api/database/stats` → `[{namespace, text, weight}]`를 앱 시작 시 1회 받아 메모리/DataStore 캐시.
  검색창 입력 중 `namespace:` 접두어나 2글자 이상이면 상위 N개 제안 칩. 최근 검색어 10개 DataStore 저장.
- 난이도: 중. 통계가 큰 서버(수만 태그)면 백그라운드 로드 + 간단 인덱스.

### P1-8. 서재 UX 소소한 것들
- 입력 중 디바운스(400ms) 자동 검색 옵션. 리스트 보기(제목·태그 미리보기) ↔ 그리드 토글.
  카드에 추가일 표시. 홈 상단 "계속 읽기" 가로 캐러셀(기록 상위 5개). `SearchPage.skipped > 0`이면
  "N개 항목은 서버 응답 형식 문제로 건너뜀" 칩 표시(원래 문제의 가시화).
- 난이도: 하.

### P1-9. 키보드/마우스(DeX, 블루투스 키보드) 지원
- 어떻게: `MainActivity.onKeyDown`에 `KEYCODE_DPAD_LEFT/RIGHT`, `PAGE_UP/DOWN`, `SPACE`를 `ReaderKeyEvents`로 라우팅.
  마우스 휠은 Compose 기본 스크롤로 웹툰 모드에서 이미 동작.
- 난이도: 하.

### P1-10. 서버 여러 대(프로필)
- 왜: 집(LAN http) / 외부(Tailscale https) 주소가 다른 경우.
- 준비됨: Room의 모든 레코드가 `serverId`를 가진다(`DEFAULT_SERVER_ID`). 프로필 테이블(`server_profile`) 추가 +
  `ReadingProgressRepository(dao, serverId)`의 serverId를 활성 프로필로 바꾸면 기록이 서버별로 분리된다.
- 어떻게: `AppSettings.activeServerId`, 인터셉터·API는 활성 프로필만 본다. 같은 서버의 LAN/원격 주소 2개는
  **하나의 프로필에 URL 2개**로 두고 연결되는 쪽을 자동 선택(기록 공유).
- 난이도: 중.

### P1-11. 자동 재시도 · 네트워크 변화 대응
- 페이지 로드 실패 시 지수 백오프 자동 재시도 2회 후 버튼 노출. Wi-Fi↔LTE 전환 시 OkHttp가 재연결하므로 추가 작업 적음.
- 난이도: 하.

---

## P2 — 보안 · 견고성

### P2-1. 사용자 CA 전역 신뢰 대신 "서버 인증서/CA 가져오기"(핀 고정)
- 왜: 현재 `<certificates src="user"/>`는 기기의 모든 사용자 CA를 신뢰한다(수용한 위험, mobsfscan·lint 경고).
  앱이 **NAS 인증서(또는 그 CA)만** 신뢰하면 경고 없이 더 안전하다.
- 어떻게: 설정에 "서버 인증서 가져오기": 첫 연결에서 서버 인증서 체인을 받아(`OkHttp` handshake의
  `peerCertificates`) SHA-256 지문을 보여주고 사용자가 확인하면 저장(TOFU). 이후 `OkHttpClient`에
  `sslSocketFactory(customTrustManager)`: 시스템 CA + 저장된 인증서만 신뢰. 지문이 바뀌면 경고.
  네트워크 보안 설정에서 `src="user"` 제거 가능 → 수용 위험 1개 해소.
- 관련: `AppGraph.httpClient`, 새 `data/TrustStore.kt`, `SettingsScreen`.
- 난이도: 중~상. 주의: Coil도 같은 `OkHttpClient`를 쓰므로 한 곳만 바꾸면 됨.

### P2-2. 앱 잠금(생체 인증) · 스크린샷/최근 앱 미리보기 가리기
- 어떻게: `androidx.biometric:biometric` + 설정 토글 "앱 열 때 잠금"(`BiometricPrompt`, 장치 자격 증명 허용).
  `FLAG_SECURE` 토글(설정)로 스크린샷·최근 앱 썸네일 차단(mobsfscan INFO 항목 해소).
- 난이도: 하~중.

### P2-3. 업데이트 채널 · Wi-Fi 전용 다운로드 · 릴리스 출처 증명
- 베타 채널: `GET /repos/{o}/{r}/releases`(prerelease 포함) 선택 옵션. `Version` 비교는 pre-release를 낮게 보므로
  안전. Wi-Fi 전용 다운로드 옵션(`ConnectivityManager`).
- 출처 증명: 워크플로에 `actions/attest-build-provenance`로 APK 증명서 발행(수동 검증용). 앱은 이미
  SHA-256 + 서명 인증서 일치를 검사하므로 필수는 아님.
- 난이도: 하~중.

### P2-4. 진단 로그 · 크래시 로그(외부 전송 없음)
- 왜: 실기기에서 문제가 나면 logcat 없이도 원인을 볼 수 있어야 세션 간 디버깅이 빠르다.
- 어떻게: `Thread.setDefaultUncaughtExceptionHandler`로 스택을 `files/crash/`에 저장, 다음 실행 때 설정에
  "최근 크래시 보기/공유". API 오류·파싱 건너뜀을 링 버퍼(최근 200건)로 메모리에 보관하고 "진단 로그 공유"
  (`ACTION_SEND` 텍스트). **외부 서버로 보내지 않는다.**
- 난이도: 하~중.

### P2-5. 테스트 보강
- `UpdateManager`의 `GhRelease.toReleaseInfo()`를 internal/top-level로 빼서 단위 테스트(자산 선택, 체크섬 자산,
  draft 제외). `ReaderViewModel.resolveStartPage` 로직 분리 테스트. Compose UI 테스트(실기기/에뮬)는 선택.
- 난이도: 하.

### P2-6. CI 유지보수
- `github/codeql-action` v3 → **v4** (2026-12 v3 지원 종료 경고). Node 20 기반 액션 경고 → `actions/checkout`,
  `setup-java`, `upload-artifact`, `setup-python`, `gradle/actions` 최신 메이저로 올리기(태그 존재 확인 후).
- Dependabot PR이 오면 CI 통과 확인 후 병합. 버전 핀(AGP 8.10.1/Gradle 8.14.3/Kotlin 2.1.21)은 **별도 브랜치**에서
  한 번에 올리고 CI로 검증(AGP 9.x는 Gradle 9 요구 → `TROUBLESHOOTING §D` 갱신).
- `lint.xml` 베이스라인 도입으로 의도된 경고(GradleDependency 등) 소음 제거.
- 난이도: 하.

### P2-7. 릴리스 매핑 파일 공개 범위
- 현재 `mapping-vX.Y.Z.txt`를 Release 자산으로 공개한다(개인용이라 무방). 비공개로 두고 싶으면 Actions
  아티팩트로만 보관하도록 `release.yml`의 `files:`에서 제외.

---

## P3 — 장기 · 선택

- **오프라인 보관(핀 고정)**: 스트리밍 요구로 보류. 필요해지면 Coil 캐시와 별도 디렉터리에 전체 페이지를 저장하고
  리더가 로컬 파일을 우선 사용. `/api/archives/{id}/download`(zip)보다 페이지 단위 저장이 리더 재사용에 유리.
- **태블릿 2-pane 레이아웃**(서재 | 상세) with `WindowSizeClass`.
- **딥링크**: `lrrviewer://archive/{id}` 커스텀 스킴(서버 호스트는 가변이라 http 링크 인터셉트는 어려움).
- **여백 자르기(crop borders)**: 가장자리 색 샘플링 → `ContentScale` 대신 `drawWithContent` 클리핑. 계산 비용 주의.
- **다국어(`strings.xml`)**: 현재 UI 문자열은 코틀린에 한국어 하드코딩(개인용). 공개 배포 시 `values-en` 분리.
- **아이콘 디자인**: 현재 벡터 플레이스홀더(`drawable/ic_launcher_foreground.xml`).
- **탄코본 진행률**: `PUT /api/tankoubons/{id}/progress/{page}` 지원(묶음 단위 이어 읽기).
- **서버 "최근 읽음" 탭**: `sortby=lastread`(서버 진행률 추적 켜진 경우)로 다른 기기에서 읽은 것도 표시.

## 세션 2 추가 검토 (2026-10-07, 로컬에서 코드 전수 읽기) — 기존 항목에 없는 것만

**전면 개편 판단: 불필요.** 단일 모듈·수동 DI·MVVM·Room·Coil 3·타입 안전 라우트는 이 규모(4.5k줄)에 맞고, 나중에
비싼 것(저장소 스키마·이미지 스택·라우트)은 세션 1에서 이미 교체했다. 기능이 쌓이기 **전에** 모양을 정해 둘 것은 아래 S-1·S-2 둘뿐.

### S-1. 페이지 치수(width/height) 캐시 — P0-4·P1-1·P1-2가 공유하는 기반 (구조, 중)
- 왜: 웹툰 스트립 타일링(P0-4), 양면 보기(P1-1), 웹툰 자동 감지(P1-2)가 전부 "레이아웃 전에 이미지 크기를 알아야" 한다.
  셋을 따로 구현하면 치수 조회 코드가 세 벌 생긴다.
- 어떻게: `data/PageInfoRepository`(Room `page_info(serverId, arcid, index, width, height)`) + Coil 디스크 캐시 스냅샷에서
  `inJustDecodeBounds`로 읽는 단일 함수. 리더는 `List<String>`(URL) 대신 `List<Page>(url, index, size?)`를 들고 다닌다.
  Room version 2 + AutoMigration (`app/schemas/1.json`이 커밋돼 있어야 함).

### S-2. 서버 프로필은 "설정 항목이 더 늘기 전에" (P1-10 우선순위 상향 메모)
- `AppSettings`가 `serverUrl/apiKey` 단일 서버 전제라 설정 키가 늘수록 분리 비용이 커진다. LAN/Tailscale 두 주소를 쓸 계획이
  조금이라도 있으면 P1 중 먼저. 계획이 없으면 그대로 둔다.

### S-3. 웹툰 모드 탭 존이 "항목 단위 점프" (하)
- `ReaderScreen.onZoneTap` → `jumpEvents` → `WebtoonReader`의 `scrollToItem(p)`. 긴 스트립에서는 한 탭에 화면 몇 개분이 튄다.
- 수정: 웹툰 모드에서는 `listState.animateScrollBy(±viewportHeight * 0.9f)`. 볼륨키도 동일.

### S-4. 웹툰 줌이 `graphicsLayer` 스케일이라 레이아웃과 분리 (P0-4와 함께, 중)
- 확대 상태에서 탭 좌표·스크롤 범위가 실제 콘텐츠와 어긋난다. 타일링 재설계 때 `Modifier.zoomable`(telephoto) 또는
  항목 폭을 실제로 키우는 방식으로 바꾼다.

### S-5. 자동 업데이트 확인 실패(오프라인)도 "오늘 확인함"으로 기록 (하, 버그)
- `AppRoot.AutoUpdateCheck`가 `check()` 결과와 무관하게 `lastUpdateCheck = now`. 성공 시에만 갱신하도록.

### S-6. 서재 무한 스크롤 오프셋 (하, 잠재 버그)
- `LibraryViewModel.loadMore`가 `start = items.size`인데 `distinctBy { arcid }`로 중복이 빠지면 서버 오프셋과 어긋나 항목이 누락될 수 있다.
  서버 페이지 오프셋을 별도 필드로 추적.

### S-7. 미리 받기가 현재 페이지와 같은 OkHttp 디스패처를 공유 (중)
- 느린 NAS/외부망에서 prefetch 3장이 현재 페이지 요청을 늦출 수 있다. 현재 페이지 요청을 먼저 보내고(prefetch는 성공 콜백 뒤),
  또는 `Dispatcher.maxRequestsPerHost`를 2~3으로 제한.

### S-8. 썸네일과 페이지가 디스크 캐시를 공유 (하~중)
- 512MB 캐시를 페이지가 채우면 썸네일이 밀려 서재 스크롤이 매번 재다운로드. 썸네일 전용 소형 `ImageLoader`(64MB) 분리.

### S-9. 펀치홀/컷아웃 몰입 모드 (하)
- 리더에서 `window.attributes.layoutInDisplayCutoutMode = SHORT_EDGES`가 없으면 Galaxy 가로 모드에서 컷아웃 쪽에 검은 띠.

### S-10. 정렬 옵션 확장 (하)
- LANraragi `sortby`는 임의 네임스페이스를 받는다(`artist`, `series`, `date_added`…). `SearchQuery.SORT_OPTIONS`에 artist/series 추가.

### S-11. 콜드 스타트: 설정 읽기 + Keystore 복호화를 `runBlocking`으로 메인 스레드에서 (하~중)
- `AppGraph.settingsState` 초기값. 체감되면 스플래시 동안 비동기 로드로 바꾼다. 캐시 크기 설정도 재시작 전까지 미적용(같은 지점).

### S-12. 서재 카드 길게 누르기 → 빠른 동작 메뉴 (하~중)
- NEW 토글, 카테고리에 추가(P1-6과 연동), 기록 삭제. 상세 화면 왕복을 줄인다.

## 구조 개편으로 처리된 것 (세션 1 후반)
- [x] 기록 저장소 Room 전환 + `(serverId, arcid)` 식별자 — P1-10/P1-2/북마크 등의 기반
- [x] Coil 3 이전(`coil3-compose`, `coil-network-okhttp`, telephoto coil3)
- [x] 타입 안전 내비게이션, ViewModel 의존성 축소(테스트 가능), 리더 파일 분리

## 하지 않기로 한 것 (명시적 보류 — 다시 논의 전까지 유지)
- Google Play 배포, 광고/텔레메트리/외부 크래시 리포팅(개인정보·단순성).
- 아카이브 전체 다운로드 기본값(요구사항이 스트리밍).
- Mihon 확장 포크(원인은 확인했지만 이 앱으로 대체하는 것이 목표).
- Hilt/Koin 도입, 멀티모듈 분리(현 규모에서 이득 없음). ViewModel 생성자 주입 + `AppGraph`로 충분.

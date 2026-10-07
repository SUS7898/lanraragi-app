# LRR Viewer — NAS LANraragi 전용 Android 뷰어

NAS에서 컨테이너로 실행 중인 [LANraragi](https://github.com/Difegue/LANraragi)를 Galaxy 폰/태블릿에서
편하게 보기 위한 전용 뷰어입니다. Google Play에 올리지 않고 **GitHub Releases + 앱 내 업데이트**로 배포합니다.

- Mihon(LANraragi 확장)에서 일부 아카이브가 `NumberFormatException: For input string: ""` 로 열리지 않던 문제를
  근본적으로 회피합니다 (서버가 주는 숫자/불리언을 모두 관대하게 파싱).
- **스트리밍 열람**: 아카이브를 통째로 내려받지 않고 페이지 단위로 받아오며, 다음 몇 장을 미리 받아 둡니다.
- 서명된 APK + SHA-256 체크섬 + 서명 인증서 검증을 거치는 자체 업데이트.
- CI에서 CodeQL / Trivy / gitleaks / Android Lint / mobsfscan 보안 점검.

> 개발 메모·설계·트러블슈팅은 [`CLAUDE.md`](CLAUDE.md), [`docs/DESIGN.md`](docs/DESIGN.md),
> [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md), [`docs/PROGRESS.md`](docs/PROGRESS.md),
> 앞으로 할 일은 [`docs/BACKLOG.md`](docs/BACKLOG.md), 실기기 테스트는 [`docs/TEST_CHECKLIST.md`](docs/TEST_CHECKLIST.md) 참고.

## 기능

| 영역 | 내용 |
|---|---|
| 서재 | 썸네일 그리드, 태그 검색(LANraragi 문법 그대로), 카테고리, 정렬(추가일/제목/최근 읽음), 신규만·미태그·완독 숨김, 무한 스크롤, 당겨서 새로고침, 무작위 열기, 탄코본(묶음) 지원 |
| 상세 | 메타데이터, 진행률, 이어 읽기/처음부터, 태그 탭 → 검색, 목차(TOC) 점프, NEW 표시 토글, 서버 재추출, 웹 리더 열기 |
| 뷰어 | 읽기 방향 LTR / RTL(만화) / 세로 넘김 / 웹툰(연속 스크롤), 핀치·더블탭 줌(대형 이미지 서브샘플링), 화면/가로/세로 맞춤, 배경색, 탭 존(가장자리 이전/다음·가운데 메뉴), 볼륨 키, 화면 켜짐 유지, 회전 잠금, 페이지 슬라이더, 목차, 페이지별 재시도, 미리 받기(0~10장), 몰입 모드 |
| 진행률 | 기기 로컬 기록 + 서버 측 진행률(서버에서 켜져 있을 때) 동기화, 읽기 시작 시 NEW 자동 해제 |
| 보안 | API 키 Android Keystore(AES-GCM) 암호화 저장, 키는 설정한 서버 호스트에만 전송, 평문 HTTP 차단 옵션, 사용자 설치 CA 신뢰(자체 서명 HTTPS NAS) |
| 업데이트 | GitHub Releases 자동 확인(하루 1회) / 수동 확인, 다운로드 → SHA-256 → 서명 인증서 일치 검증 → 설치 |

## 설치 (최초 1회)

1. [Releases](../../releases/latest)에서 `lrr-viewer-vX.Y.Z.apk`를 폰/태블릿에 내려받아 엽니다.
2. "출처를 알 수 없는 앱 설치" 허용 안내가 나오면 **내려받은 앱(브라우저/파일 앱)** 에 대해 1회 허용합니다.
   Play Protect가 "알 수 없는 개발자" 경고를 띄울 수 있습니다. Play 미배포 앱의 정상 동작이며 서명으로 없앨 수 없습니다.
   Galaxy(One UI 6 이상)에서 **자동 차단(Auto Blocker)** 이 켜져 있으면 설치와 앱 내 업데이트가 막힐 수 있습니다.
   설정 → 보안 및 개인정보 보호 → 자동 차단을 잠시 끄거나 예외를 허용하세요.
3. 앱을 열고 NAS의 LANraragi 주소(예: `http://192.168.0.10:3000`)와 필요 시 API 키(서버 설정 → Security)를 입력, **연결 테스트** 후 저장.

이후 업데이트는 **앱 → 설정 → 업데이트**에서 진행합니다. 첫 자체 업데이트 때 "이 앱이 알 수 없는 앱을 설치하도록 허용"을
한 번 켜주면, 이후에는 다시 설치 파일을 찾을 필요 없이 앱 안에서 바로 갱신됩니다(같은 키로 서명되어 데이터 유지).

## 릴리스 (개발자용)

### 1) 서명 키 만들기 — 딱 한 번, 절대 분실 금지
```bash
scripts/generate-keystore.sh            # release.jks 생성 + GitHub Secrets 용 base64 출력
```
키스토어가 바뀌면 기존 설치본 위에 업데이트할 수 없습니다(삭제 후 재설치 필요). 안전한 곳에 백업하세요.

### 2) GitHub 저장소 Secrets 등록 (Settings → Secrets and variables → Actions)
| Secret | 값 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` 출력 |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | 키 별칭 (스크립트 기본값 `lrrviewer`) |
| `KEY_PASSWORD` | 키 비밀번호 |

### 3) 태그를 푸시하면 끝
```bash
git tag v0.1.0
git push origin v0.1.0
```
`Release` 워크플로가 테스트 → Trivy 취약점 게이트 → 서명 빌드 → `apksigner verify` → `SHA256SUMS.txt` 생성 →
GitHub Release 게시까지 수행합니다. 앱의 versionName/versionCode는 태그에서 자동 계산됩니다
(`v1.2.3` → `1.2.3`, code `1002003`). 코드에 버전을 적을 필요가 없습니다.

### 로컬 빌드 (Android Studio / PC)
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
# 릴리스 서명 로컬 테스트: keystore.properties (git 제외) 에 storeFile/storePassword/keyAlias/keyPassword
./gradlew :app:assembleRelease -PappVersionName=0.1.0
```

## 보안 점검 (CI)

`Security Scan` 워크플로(푸시/PR/매주):
- **CodeQL** (java-kotlin, security-extended)
- **Trivy** — 의존성 취약점(`gradle.lockfile`을 CI에서 생성해 스캔), 시크릿, 설정 오류. 수정판이 있는 CRITICAL/HIGH면 실패
- **gitleaks** — Git 히스토리의 비밀 키 유출
- **Android Lint** — SARIF 업로드
- **mobsfscan** — Android 보안 룰 정적 분석
- **Dependency Review** — PR에서 새 의존성 취약점

결과는 저장소 **Security → Code scanning** 탭(공개 저장소/Advanced Security) 또는 각 워크플로의 Artifacts에서 확인합니다.
`Release` 워크플로도 Trivy 게이트를 통과해야 APK를 게시합니다.

## 네트워크/보안 참고
- NAS가 LAN 전용(`http://`)이면 같은 Wi-Fi 또는 VPN(예: Tailscale)에서 사용하세요. 인터넷에 노출된 서버는 HTTPS를 권장하며,
  설정에서 "평문 HTTP 허용"을 끄면 http 연결이 모두 차단됩니다.
- 자체 서명 인증서를 쓰는 HTTPS NAS는 기기에 CA 인증서를 설치하면 동작합니다(사용자 CA 신뢰).
- No-Fun 모드 서버는 API 키가 필수입니다.

## 라이선스
MIT — [LICENSE](LICENSE)

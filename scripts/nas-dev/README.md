# NAS 개발 컨테이너 (Synology DS423+)

PC를 켜 두지 않고 NAS에서 Claude Code로 작업 → GitHub push 하기 위한 설정.
RAM 18GB(기본 2GB + 16GB) 기준. Container Manager(Docker) 필요.

## 1. 준비 (최초 1회)
1. File Station에서 공유 폴더 `docker`(Container Manager가 만든 것) 안에 `claude/dev/home`, `claude/dev/work` 폴더를 만든다.
   - **Synology Drive 동기화 대상에 넣지 말 것** — 자리표시 파일로 빌드가 깨진다(TROUBLESHOOTING §F-6).
   - **경로에 한글을 넣지 말 것** — AGP가 비ASCII 경로에서 빌드를 거부한다(§F-1).
     기존 백업/동기화 폴더(예: `/volume1/Tier/.../백업/개인/Project`)를 작업 폴더로 쓰지 말고 `work`에 새로 clone한다.
2. SSH 켜기: DSM 제어판 → 터미널 및 SNMP → 터미널 → "SSH 서비스 활성화"(포트 22) → 적용.
   SSH 로그인은 **administrators 그룹** 계정만 가능하다. 외부 접속은 포트 개방 대신 Tailscale 패키지를 쓴다.
3. NAS IP 확인: 제어판 → 네트워크 → 네트워크 인터페이스(예: `192.168.0.10`).
4. 같은 내부망 PC의 PowerShell에서 접속 (Windows 10/11 기본 내장 ssh):
   ```powershell
   ssh <DSM계정>@192.168.0.10        # 처음엔 지문 확인에 yes, 비밀번호는 DSM 비밀번호
   ```
   폰에서는 Termius 같은 SSH 앱으로 같은 주소·계정을 쓴다.
5. NAS에서 UID 확인 후 설정 파일 받기:
   ```bash
   id -u                                   # 이 값을 compose.yaml 의 UID 에 넣는다
   mkdir -p /volume1/docker/claude/dev/nas-dev && cd /volume1/docker/claude/dev/nas-dev
   B=https://raw.githubusercontent.com/SUS7898/lanraragi-app/main/scripts/nas-dev
   curl -fsSLO $B/Dockerfile && curl -fsSLO $B/compose.yaml
   vi compose.yaml                         # UID 수정 (i 로 편집, Esc → :wq 저장)
   ```
   (브랜치가 아직 main에 합쳐지지 않았다면 `main` 자리에 작업 브랜치 이름을 넣는다.)
6. 빌드·기동:
   ```bash
   cd /volume1/docker/claude/dev/nas-dev && sudo docker compose up -d --build
   ```

## 2. 컨테이너 안 설정 (최초 1회)
```bash
sudo docker exec -it claude-dev bash
curl -fsSL https://claude.ai/install.sh | bash          # 홈 볼륨에 설치 → 유지·자동 업데이트
git config --global user.name  "sus7898"
git config --global user.email "seep7898@gmail.com"
ssh-keygen -t ed25519 -C nas-dev && cat ~/.ssh/id_ed25519.pub   # GitHub → Settings → SSH keys 에 등록
cd /work && git clone git@github.com:sus7898/lanraragi-app.git
cd lanraragi-app && ./gradlew :app:testDebugUnitTest :app:assembleDebug   # 첫 빌드는 오래 걸림
```

## 3. 평소 사용
```bash
sudo docker exec -it claude-dev bash
tmux new -As main              # SSH가 끊겨도 세션 유지 (다시 붙을 때도 같은 명령)
cd /work/lanraragi-app && claude
```
- 처음 `claude` 실행 시 로그인 URL이 나온다 → 폰/PC 브라우저로 열고 코드를 붙여넣는다.
- `claude remote-control`로 띄우면 Claude 앱의 Claude Code 목록에 나타나 폰에서 지시할 수 있다
  (tmux 안에서 띄워 두면 SSH를 닫아도 계속 동작).
- 같은 LAN이라 LANraragi 실서버 API로 직접 확인할 수 있다(클라우드 세션은 불가).

## 지킬 것
- 서명 키(`*.jks`, `keystore.properties`)는 NAS에도 두지 않는다. 릴리스 서명은 GitHub Actions secrets로만.
- 최종 검증은 여전히 CI. 릴리스 전 R8 APK 확인(CLAUDE.md 규칙)은 실기기 설치로 한다 —
  J4125 + 컨테이너에서 에뮬레이터(KVM·GPU 없음)는 검증되지 않았다.
- 비공식 16GB 모듈이므로 처음에 Synology Assistant의 메모리 테스트를 한 번 돌려 둔다.

# NAS 개발 컨테이너 (Synology DS423+)

PC를 켜 두지 않고 NAS에서 Claude Code로 작업 → GitHub push. 휴대폰·다른 PC에서는 Remote Control로 접속.
RAM 18GB 기준. 컨테이너 하나를 `work/` 아래 모든 저장소(lanraragi-app, librario_java, PocketRisu_app)가 같이 쓴다.

```
/volume1/docker/claude/dev/
  nas-dev/  Dockerfile, compose.yaml
  home/     컨테이너의 /home/dev  (Claude 로그인, git 자격 증명, Gradle·Maven 캐시)
  work/     컨테이너의 /work      (저장소 체크아웃)
```

## 0. 시작 전 점검 (기존 NAS 영향)
```bash
df -h /volume1                      # 여유 20GB 이상 (이미지 ~4GB + 빌드 캐시 수 GB)
free -h                             # 18GB 인식 확인
sudo docker ps --format '{{.Names}}\t{{.Status}}'   # 기존 컨테이너 목록 (claude-dev 이름 겹침 없는지)
ip -4 addr | grep 'inet '           # LAN 대역 (예: 192.168.0.x)
```
DSM 화면에서:
- Synology Drive 관리 콘솔 → 팀 폴더: `docker`가 동기화 대상이 **아니어야** 한다.
- 스냅샷 복제/Hyper Backup이 `docker` 폴더를 잡고 있으면 `claude/dev/home`(캐시)·`work/*/build`·`target`을 제외한다
  (빌드 캐시가 스냅샷·백업 용량을 계속 키운다).
- 제어판 → 보안 → 계정: 자동 차단 켜짐, DSM 관리자 계정 2단계 인증 켜짐.

## 1. 파일 받기·빌드 (NAS SSH)
```bash
sudo mkdir -p /volume1/docker/claude/dev/home /volume1/docker/claude/dev/work /volume1/docker/claude/dev/nas-dev
sudo chown -R "$(id -un)":users /volume1/docker/claude/dev
chmod 700 /volume1/docker/claude/dev/home
cd /volume1/docker/claude/dev/nas-dev
B=https://raw.githubusercontent.com/SUS7898/lanraragi-app/main/scripts/nas-dev
curl -fsSLO $B/Dockerfile && curl -fsSLO $B/compose.yaml
sed -i "s/UID: \"1026\"/UID: \"$(id -u)\"/" compose.yaml && grep UID compose.yaml
sudo docker compose up -d --build      # 없으면 sudo docker-compose up -d --build. 20분 안팎, 그동안 NAS가 느릴 수 있음
sudo docker ps --filter name=claude-dev
```
(브랜치가 main에 합쳐지기 전이면 `B=` 의 `main`을 작업 브랜치 이름으로.)

## 2. 컨테이너 안: 도구 확인 · Claude 설치 · 로그인
```bash
sudo docker exec -it claude-dev bash
java -version 2>&1 | head -1; mvn -v | head -1; node -v; python3 -V; $CHROME --version
curl -fsSL https://claude.ai/install.sh | bash
claude              # 로그인 URL → 휴대폰 브라우저로 열어 승인 → 코드 붙여넣기 → /exit
```

## 3. git · GitHub 자격 증명 (컨테이너 안)
GitHub → Settings → Developer settings → **Fine-grained token**: 저장소는 필요한 것만
(lanraragi-app, librario_java, PocketRisu_app), 권한 Contents·Pull requests = Read and write, 만료 90일.
```bash
git config --global user.name  "SUS7898"
git config --global user.email "133582238+SUS7898@users.noreply.github.com"   # 공개 저장소에 실제 메일을 남기지 않음
git config --global credential.helper store
read -rsp "GitHub token: " T; echo
printf 'protocol=https\nhost=github.com\nusername=SUS7898\npassword=%s\n\n' "$T" | git credential approve; unset T
ls -l ~/.git-credentials              # -rw------- 인지 확인
```

## 4. 저장소 받기 (컨테이너 안)
```bash
cd /work
git clone https://github.com/SUS7898/lanraragi-app.git
git clone https://github.com/SUS7898/librario_java.git
git clone https://github.com/SUS7898/PocketRisu_app.git
exit                                   # 컨테이너에서 나와 NAS 본체로
```

## 5. (해당 시) 동기화 폴더에 남은 작업 옮기기 — NAS 본체에서
예: Librario 2.3.0 (11/8 09:07 이후 바뀐 파일만, 원본은 읽기만 한다)
```bash
SRC="/volume1/Tier/Tier_2/백업/개인/Project/librario-java"
DST=/volume1/docker/claude/dev/work/librario_java
cd "$SRC" && find . -newermt '2026-11-08 09:07' -type f -not -path '*/target/*' -not -path '*/build/*' > /tmp/wip230.txt
rsync -a --files-from=/tmp/wip230.txt "$SRC"/ "$DST"/
sudo docker exec -it -w /work/librario_java claude-dev bash -c 'git switch -c wip/2.3.0-from-pc && git status --short'
```

## 6. 평소 사용: Remote Control
```bash
sudo docker exec -it claude-dev bash
tmux new -As main                      # 다시 붙을 때도 같은 명령. 빠져나오기: Ctrl+b 후 d
cd /work/librario_java && claude remote-control
```
SSH 창을 닫아도 계속 돈다. 휴대폰 Claude 앱 / 다른 PC claude.ai/code 의 Code 목록에 나타난다.
NAS 재부팅·Container Manager 업데이트 뒤에는 컨테이너는 자동으로 뜨지만 remote-control 은 다시 띄워야 한다
(SSH 대신 DSM → Container Manager → 컨테이너 → claude-dev → 터미널에서도 된다).

## 보안 메모
- 메모리 6g(스왑 금지)·CPU 3코어 상한, watchtower 제외 라벨.
- 컨테이너는 일반 사용자 `dev`, `no-new-privileges`, 포트 공개 없음, sudo 없음. 마운트는 `home`·`work` 둘뿐.
  docker.sock·LANraragi 데이터·서명 키(`pocketrisu-app-keystore` 등)는 절대 마운트하지 않는다.
- 남는 위험: 컨테이너는 집 LAN에 닿는다(LANraragi 실서버 테스트용). DSM·공유기 관리 화면은 비밀번호·2단계 인증으로 지킨다.
- Remote Control 세션은 Claude 계정으로 접속하므로 **Claude 계정(로그인 수단)의 2단계 인증**이 곧 NAS 컨테이너의 문 잠금이다.
- `--dangerously-skip-permissions`는 쓰지 않는다. 웹 페이지·이슈 본문의 지시는 프롬프트 주입일 수 있다.
- 토큰(`~/.claude`, `~/.git-credentials`)은 `home/`에 있다 → `docker` 공유 폴더를 SMB/Drive로 다른 계정에 열지 않는다.
- SSH: 공유기 포트 개방 금지(밖에서는 Tailscale), 안 쓸 때는 꺼 둬도 된다(위 Container Manager 터미널로 대체).
- Chrome은 `--no-sandbox`로 돈다(Docker 기본 seccomp 때문). 컨테이너가 격리를 맡으므로, 신뢰하지 않는 사이트 탐색 용도로 쓰지 않는다.
- 비공식 16GB 모듈 → Synology Assistant 메모리 테스트를 한 번 돌려 둔다.
- 에뮬레이터는 안 된다(KVM·GPU 없음). 릴리스 전 APK 확인은 실기기로.

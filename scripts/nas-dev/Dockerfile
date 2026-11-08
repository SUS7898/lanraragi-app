# NAS(Synology DS423+, x86_64)용 개발 컨테이너: JDK 17 + Android SDK + Python 3 + Node.js 22 + git/tmux.
# Librario 게이트(tools/verify/verify.py: java·javac·javap·node)와 PocketRisu 프런트 빌드(node)까지 돈다.
# Claude Code 본체는 홈 볼륨(/home/dev)에 설치한다 → 이미지 재빌드 없이 자동 업데이트·로그인 유지.
FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl unzip xz-utils tmux openssh-client ca-certificates less rsync python3 \
 && rm -rf /var/lib/apt/lists/*

# Node.js (공식 배포본, 버전 고정)
ARG NODE_VERSION=22.12.0
RUN curl -fsSL https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz \
  | tar -xJ -C /usr/local --strip-components=1 \
 && node --version && corepack enable

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:/home/dev/.local/bin

RUN mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp \
 && curl -fsSLo tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm tools.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

# NAS 사용자와 같은 UID로 맞춰야 볼륨 파일 권한이 꼬이지 않는다 (NAS에서 `id`로 확인, 보통 1026).
ARG UID=1026
RUN useradd -m -u ${UID} -s /bin/bash dev && chown -R dev $ANDROID_HOME

USER dev
WORKDIR /work

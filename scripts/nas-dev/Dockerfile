# NAS(Synology DS423+, x86_64)용 공용 개발 컨테이너 — 프로젝트별이 아니라 /work 아래 모든 저장소가 같이 쓴다.
#   lanraragi-app / PocketRisu_app : JDK 17 + Android SDK(Gradle이 필요한 플랫폼을 더 받음) + Node.js 22
#   librario_java                  : JDK 17 + Maven(jar 빌드) + Python 3·Node(verify.py 게이트) + Chrome(navrig·화면 확인)
# Claude Code 본체는 홈 볼륨(/home/dev)에 설치한다 → 이미지 재빌드 없이 자동 업데이트·로그인 유지.
FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl unzip xz-utils tmux openssh-client ca-certificates less rsync python3 fonts-noto-cjk \
 && rm -rf /var/lib/apt/lists/*

# Node.js (공식 배포본, 버전 고정)
ARG NODE_VERSION=22.12.0
RUN curl -fsSL https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz \
  | tar -xJ -C /usr/local --strip-components=1 \
 && node --version && corepack enable

# Maven (공식 배포본, 버전 고정)
ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  | tar -xz -C /opt && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

# Chrome (헤드리스 확인용: Librario navrig 는 /usr/bin/google-chrome 을 찾는다. Ubuntu chromium 은 snap 이라 컨테이너에서 안 됨)
RUN curl -fsSLo /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
 && apt-get update && apt-get install -y --no-install-recommends /tmp/chrome.deb \
 && rm -rf /tmp/chrome.deb /var/lib/apt/lists/*

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

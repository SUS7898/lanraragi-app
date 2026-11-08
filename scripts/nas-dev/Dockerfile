# NAS(Synology DS423+, x86_64)용 개발 컨테이너: JDK 17 + Android SDK + git/tmux.
# Claude Code 본체는 홈 볼륨(/home/dev)에 설치한다 → 이미지 재빌드 없이 자동 업데이트·로그인 유지.
FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl unzip tmux openssh-client ca-certificates less \
 && rm -rf /var/lib/apt/lists/*

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

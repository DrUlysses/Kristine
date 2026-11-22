# Kristine without a window: the server, the library and the player.
# Built from the app folder that `./gradlew :desktopApp:packageDistributionForCurrentOS` makes (CI does both).
FROM debian:trixie-slim

# libvlc plays; fpcalc fingerprints; yt-dlp and ffmpeg download missing songs
RUN apt-get update \
    && apt-get install -y --no-install-recommends libvlc5 vlc-plugin-base libchromaprint-tools yt-dlp ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY desktopApp/build/compose/binaries/main/app/Kristine /opt/kristine

# The database and certificate live here; the music folder defaults to /data/music
ENV KRISTINE_HOME=/data
ENTRYPOINT ["/opt/kristine/bin/Kristine"]

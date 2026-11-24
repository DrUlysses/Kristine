# Kristine

A music player and library manager for Android, desktop and the [web](https://drulysses.github.io/Kristine/).
It sorts and tags your songs (by name or by sound), finds duplicates, shows synced lyrics, and plays on its own or
as a remote control for another Kristine or an MPD server.

## Setup

1. Get the app: Android APK, Linux AppImage or Windows build from the latest
   [Actions](https://github.com/DrUlysses/Kristine/actions) run.
2. In **Settings**, set the **Music folder**, and optionally:
    - **AcoustID key**: a free key from [acoustid.org](https://acoustid.org), to identify songs by their sound.
    - **yt-dlp program** (desktop): custom path to yt-dlp executable.
3. To use another device's library: **Connections**, then pick a nearby server or enter its address.

### Server (Docker)

Set `MUSIC_FOLDER` and start [docker-compose.yml](docker-compose.yml):

```sh
MUSIC_FOLDER=/path/to/music docker compose up -d
```

It listens on port 45679 (TCP) and announces itself on 45678 (UDP).

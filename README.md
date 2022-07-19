# e621Syncer
Synchronizes e621 locally. 

Uses the publically available e621 database dumps to download all available posts from the website.
Reimplements functions such as tag search and pools for offline usage.
All downloaded files will be compressed further to reduce archive file size.
Uses BPG to compress images and HEVC for videos

## Needed Java libraries

- Common-io
- Common-io Image
- Common-lang
- Commons-beanutils
- Commons-codec
- Commons-collections4
- Commons-io
- Commons-lang3
- Commons-logging
- Commons-text
- HikariCP
- httpclient
- httpcore
- imageio-core
- imageio-jpeg
- imageio-metadata
- imageio-tiff
- jnativehook
- mysql-connector-java
- opencsv
- slf4j-api
- vlcj
- vlcj-natives
- jna
- jna-platform

## Additional external tools

- BPG Image De- / Compressor -> https://bellard.org/bpg/
- FFMPEG -> https://ffmpeg.org/
- MozJPG -> https://github.com/mozilla/mozjpeg

## VLC

VLC is needed for video playback.
The project expects axvlc.dll, libvlc.dll, libvlccore.dll and npvlc.dll in a subfolder lib/vlc . 
VLC's plugins go in lib/vlc/plugins.
You can get VLC here: https://www.videolan.org/vlc/index.html

A complete copy of LibVLC x64 is provided in the release package.

The VLC native library path is hardcoded in JWDiscoveryStrategy.java , it will NOT use a locally installed VLC

## How to run this project

Download e621Syncer.sql and import the script into your favourite database tool. I use MariaDB during development.

Clone this project and supply all dependencies, run it in your favourite IDE.
Or simply download a release and run it that way. -> https://github.com/JayanWarden/e621Syncer/releases
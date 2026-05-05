# BDIX CloudStream Extensions

[![Build](https://github.com/mpshimul/bdix-cloudstream-extensions/actions/workflows/build.yml/badge.svg)](https://github.com/mpshimul/bdix-cloudstream-extensions/actions/workflows/build.yml)

CloudStream extension for **DhakaMovie** – a BDIX movie and TV series streaming site.

## 📱 Features

- ✅ **Movies** – Browse 1000+ latest, trending, top‑10 movies, plus categories:
  - South Indian, Netflix, Prime, Hindi, Hollywood, Indian Bangla
- ✅ **TV Series** – 1000+ series with full seasons and episodes
- ✅ **Direct playback** – No external player required
- ✅ **Fake detail URLs** – Opens info page before playing (no accidental playback) 

## 📦 Installation

- **One-Click Install (Recommended)**: [![Install Repository](https://img.shields.io/badge/Install-Repository-1f425f.svg)](cloudstreamrepo://raw.githubusercontent.com/mpshimul/bdix-cloudstream-extensions/master/repo.json)

[![Install from webpage](https://img.shields.io/badge/Install-Directly_into_CloudStream-1f425f.svg)](https://mpshimul.github.io/bdix-cloudstream-extensions/install.html)

1. **Add repository to CloudStream**  
   Open CloudStream → **Settings** → **Extensions** → **Add Repository**  
   Enter the URL: 
```
https://raw.githubusercontent.com/mpshimul/bdix-cloudstream-extensions/master/repo.json
```
   Give it a name (e.g., "BDIX Repo").

2. **Install extension**  
Tap on the newly added repository → **Install** next to **DhakaMovie BDIX**.

3. **Start streaming**  
Go back to the home screen, choose a category (All Movies, TV Series, etc.), and enjoy.

## 🧪 Requirements

- CloudStream app version: **latest** from [official GitHub releases](https://github.com/recloudstream/cloudstream/releases)
- Android device (phone/ Android TV)

## 🙏 Disclaimer

This extension does **not** host any copyrighted content. It only fetches publicly available links from `dhakamovie.com`. Users are responsible for complying with local laws.

## 🛠️ Development

Clone the repository and open in Android Studio. Build with:
```bash
./gradlew :DhakaMovieProvider:make
```
Plugin entry point: DhakaMoviePlugin.kt
Provider logic: DhakaMovieProvider.kt

## 📝 License
MIT – Free to use and modify.
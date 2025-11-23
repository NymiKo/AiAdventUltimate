# AI Advent Ultimate

Кросс-платформенное приложение AI чат-бота с поддержкой голосового ввода (Desktop версия).

## ✨ Возможности

- 💬 Текстовый чат с AI
- 🎤 **Голосовой ввод (Desktop)** - распознавание речи через Yandex SpeechKit
- 🔊 **Озвучивание ответов голосом Джарвиса (Desktop)** - автоматическое озвучивание ответов AI
- 🌐 Кросс-платформенность: Android, iOS, Desktop (JVM)
- 🗣️ Отличное качество распознавания и синтеза русской речи

## 🎙️ Голосовой ввод и вывод

Для Desktop версии доступны:
- **Голосовой ввод** - распознавание речи через Yandex SpeechKit STT
- **Озвучивание ответов** - синтез речи голосом Джарвиса через Yandex SpeechKit TTS

### Быстрая настройка:

1. Получите API ключи в [Yandex Cloud](https://console.cloud.yandex.ru/)
2. Создайте файл `.env` в корне проекта:
   ```bash
   YANDEX_API_KEY=your_api_key
   YANDEX_FOLDER_ID=your_folder_id
   ```
3. Запустите приложение:
   ```bash
   ./gradlew composeApp:run
   ```

### Документация:
- [VOICE_INPUT.md](./VOICE_INPUT.md) - Настройка голосового ввода
- [VOICE_OUTPUT.md](./VOICE_OUTPUT.md) - Настройка озвучивания ответов
- [VOICE_QUICKSTART.md](./VOICE_QUICKSTART.md) - Быстрый старт с голосовыми функциями

---

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM), Server.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that's common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple's CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you're sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

* [/shared](./shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
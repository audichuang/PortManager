# Port Manager IntelliJ Plugin

A plugin for IntelliJ IDEA to easily find and kill processes running on specific ports.

## Features

- Find processes listening on a given port.
- Kill selected processes.
- Supports macOS and Windows.
- Tool Window interface.

## Building

This project uses Gradle. Make sure you have a compatible JDK (17 or higher) installed.

1.  **Important:** Update the `intellijPlatform.local()` path in `build.gradle.kts` to point to your local IntelliJ IDEA installation.
2.  Build the plugin using the Gradle wrapper:
    ```bash
    ./gradlew buildPlugin
    ```
3.  The built plugin `.zip` file will be located in `build/distributions/`.

## Installation

You can install the plugin manually in IntelliJ IDEA:

1.  Go to `Settings/Preferences` > `Plugins`.
2.  Click the gear icon and select `Install Plugin from Disk...`.
3.  Select the `.zip` file from the `build/distributions/` directory.

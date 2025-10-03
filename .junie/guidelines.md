# Kristine - Developer Guidelines

## Project Overview

Kristine is a cross-platform music player application built with Kotlin Multiplatform and Jetpack Compose. It supports
Android, desktop (JVM), and web (WebAssembly) platforms.

## Tech Stack

- **Kotlin Multiplatform**: For sharing code across platforms
- **Jetpack Compose**: For UI on all platforms
- **SQLDelight**: For database access
- **Ktor**: For client-server networking
- **Koin**: For dependency injection
- **Kotlinx Serialization**: For JSON serialization
- **Media3**: For Android media playback
- **VLCJ**: For desktop media playback
- **Spotify API**: For Spotify integration

## Project Structure

```
composeApp/
├── src/
│   ├── androidMain/       # Android-specific code
│   ├── commonMain/        # Shared code for all platforms
│   ├── jvmMain/           # Desktop-specific code
│   └── wasmJsMain/        # Web-specific code
├── build.gradle.kts       # Build configuration
└── webpack.config.d/      # WebAssembly configuration
```

### Key Packages

- `dr.ulysses`: Main package
- `dr.ulysses.ui`: UI components and views
- `dr.ulysses.player`: Media player functionality
- `dr.ulysses.network`: Networking and client-server communication
- `dr.ulysses.entities`: Data models and repositories
- `dr.ulysses.database`: Database access
- `dr.ulysses.inject`: Dependency injection

## Building and Running

### Android

```bash
./gradlew :composeApp:assembleDebug
```

### Desktop

```bash
./gradlew :composeApp:run
```

### Web

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

## Best Practices

1. **Code Organization**:
    - Place shared code in `commonMain`
    - Use `expect/actual` pattern for platform-specific implementations
    - Follow the package structure for new features

2. **UI Development**:
    - Use Compose for all UI components
    - Follow Material Design guidelines
    - Create reusable components in `ui/components`
    - Create reusable parts for the components in `ui/elements`
    - Place screens in `ui/views`

3. **Networking**:
    - Use the NetworkManager for client-server communication
    - Follow the established patterns for handling requests and responses

4. **Database**:
    - Use SQLDelight for database access
    - Define queries in .sq files
    - Access the database through repository classes

5. **Dependency Injection**:
    - Use Koin for dependency injection
    - Register dependencies in the appropriate module

## Common Tasks

### Adding a New Feature

1. Determine if the feature needs platform-specific implementations
2. Add shared code to `commonMain`
3. Implement platform-specific code in respective source sets if needed
4. Update the UI to include the new feature
5. Update repositories and database if necessary

### Debugging

- Use the Logger object for logging
- Check platform-specific logs:
    - Android: Logcat
    - Desktop: Console
    - Web: Browser console

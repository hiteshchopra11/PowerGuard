# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Running
- **Build debug APK**: `./gradlew :app:assembleDebug`
- **Quick install & run**: `./run_android.sh` (builds, installs, and launches app)
- **Install only**: `./run_android.sh --install-only` (skips build, installs existing APK)
- **Grant permissions**: `./grant_permissions.sh` (sets up required system permissions)

### Testing
- **Run unit tests**: `./gradlew test`
- **Run instrumented tests**: `./gradlew connectedAndroidTest`
- **Run specific test class**: `./gradlew testDebugUnitTest --tests "ClassName"`

### Project Setup
- **Sync dependencies**: `./gradlew build --refresh-dependencies`
- **Clean build**: `./gradlew clean`
- **Gradle wrapper update**: `./gradlew wrapper --gradle-version=latest`

## Architecture Overview

PowerGuard is an AI-powered Android battery optimization app using on-device Gemma LLM. The project follows clean architecture with these key layers:

### Core Modules
- **app**: Main Android application module
- **GemmaInferenceSDK**: Custom on-device LLM inference SDK for Google Gemma models

### Application Architecture
```
UI Layer (Jetpack Compose)
├── screens/ - Main app screens (Dashboard, Explore, History)
├── components/ - Reusable UI components
└── viewmodels/ - MVVM ViewModels with Hilt DI

Domain Layer
├── usecase/ - Business logic use cases
└── model/ - Domain models

Data Layer
├── local/ - Room database with DAOs and entities
├── gemma/ - Gemma LLM integration (primary analysis source)
└── collector/ - Usage data collection services
```

### Key Components

#### Dependency Injection (Hilt)
- **AppModule**: Core application dependencies
- **DatabaseModule**: Room database configuration
- **LLMModule**: Gemma SDK integration
- **ActionableModule**: Battery optimization actions
- **CollectorModule**: Usage data collection
- **RepositoryModule**: Simplified repository patterns

#### Actionable System
Located in `actionable/` - executes AI-generated optimization strategies:
- **ActionableService**: Background service for executing optimizations
- **ActionableExecutor**: Coordinates optimization actions
- **battery/**: Battery-specific optimizations (kill apps, wake locks, standby buckets)
- **data/**: Data usage restrictions
- **monitoring/**: Alerts for battery and data usage

#### Data Collection
- **UsageDataCollector**: Monitors app usage patterns and system metrics
- **DeviceInfoProvider**: Gathers device capabilities and current state
- **PowerGuardDatabase**: Room database storing insights and actionables

#### Gemma LLM Integration
The custom **GemmaInferenceSDK** provides:
- On-device inference with Google Gemma 2B model
- Battery-efficient inference modes
- JSON response parsing for structured AI outputs
- Lifecycle-aware resource management

## Development Guidelines

### API Level Targeting
- **Minimum SDK**: API 35 (Android 15)
- **Target SDK**: API 35 (Android 15)
- **Compile SDK**: API 35

### Testing Strategy
- **Unit tests**: Located in `src/test/` using JUnit, MockK, and Coroutines Test
- **Instrumented tests**: Located in `src/androidTest/` using Espresso and Compose UI testing
- **Test coverage**: Focus on use cases, repositories, and ViewModels

### Key Libraries
- **UI**: Jetpack Compose with Material 3
- **DI**: Dagger Hilt
- **Database**: Room with coroutines
- **Charts**: MPAndroidChart for analytics visualization
- **AI**: Custom Gemma SDK (on-device inference only)
- **Utilities**: Consolidated TimeUtils for formatting

### System Permissions
The app requires core system permissions for optimization features:
- **PACKAGE_USAGE_STATS**: Monitor app usage patterns
- **BATTERY_STATS**: Monitor battery usage
- **WRITE_SECURE_SETTINGS**: Modify system power settings  
- **QUERY_ALL_PACKAGES**: Access app information
- **FORCE_STOP_PACKAGES**: Stop problematic apps
- **CHANGE_APP_IDLE_STATE**: Modify app standby states
- **MANAGE_NETWORK_POLICY**: Control data usage restrictions

Use `grant_permissions.sh` for automated permission setup during development.

### Performance Considerations
- **Gemma LLM**: ~2-3 second inference time, ~1.5GB RAM usage during active inference
- **Battery optimization**: SDK includes low-battery detection for efficient inference
- **Background processing**: Uses coroutines with appropriate dispatchers
- **Database**: Room with KTX extensions for suspend functions

### Debugging & Development
- **Logs**: Use structured logging with class-specific tags
- **Debug builds**: Include UI tooling and test manifests
- **ProGuard**: Disabled in debug builds for easier debugging
- **Utilities**: Centralized TimeUtils for date/time formatting across the app
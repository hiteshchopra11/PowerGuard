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

PowerGuard is an AI-powered Android battery optimization app featuring **dual AI service architecture** with seamless switching between Firebase AI and custom Backend API. The project follows clean architecture with these key layers:

### Core Modules
- **app**: Main Android application module with dual AI service support
- **AiInferenceSDK**: Firebase AI integration SDK for cloud-based inference

### Application Architecture
```
UI Layer (Jetpack Compose) - com.hackathon.powerguard.ui.*
├── screens/ - Main app screens (Dashboard, Explore, History)
├── components/ - Reusable UI components
├── viewmodels/ - MVVM ViewModels with Hilt DI
└── navigation/ - Navigation components

Domain Layer - com.hackathon.powerguard.domain.*
├── usecase/ - Business logic use cases
└── model/ - Domain models (data transfer objects)

Data Layer - com.hackathon.powerguard.data.*
├── local/ - Room database with DAOs and entities
├── ai/ - Dual AI service architecture (Firebase AI & Backend API)
├── network/ - Backend API integration with Retrofit
├── preferences/ - User preferences for service switching
├── model/ - Data models and DTOs
└── di/ - Data layer dependency injection modules

Infrastructure Layer - com.hackathon.powerguard.*
├── collector/ - Usage data collection services
├── actionable/ - Battery optimization execution system
├── services/ - Background services
├── receivers/ - Broadcast receivers
├── widget/ - App widget implementation
└── utils/ - Utility classes and helpers
```

### Key Components

#### Dependency Injection (Hilt)
- **AppModule**: Core application dependencies and database integration
- **DatabaseModule**: Room database configuration (data layer)
- **AiModule**: Dual AI service providers with named bindings (@Named("firebase"), @Named("backend"))
- **NetworkModule**: Retrofit and OkHttp configuration for Backend API
- **ActionableModule**: Battery optimization action handlers and executors
- **CollectorModule**: Usage data collection service providers
- **AppRepository**: Simplified repository pattern implementations

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

#### Dual AI Service Architecture
The app features a comprehensive dual AI service system with:

**Common Interface** (`PowerGuardAnalysisService`):
- Unified contract for both Firebase AI and Backend API implementations
- Service availability detection and service type identification
- Clean abstraction for seamless service switching

**Firebase AI Service** (`PowerGuardFirebaseService`):
- Cloud-based inference with Google's Firebase AI platform
- Wraps existing `AiRepository` functionality
- Handles Firebase-specific authentication and request processing

**Backend API Service** (`PowerGuardBackendService`):
- REST API integration using Retrofit and OkHttp
- Custom endpoint: `https://powerguardbackend.onrender.com/api/analyze`
- Proper error handling, timeouts, and request/response logging
- Network connectivity validation

**Service Factory** (`AnalysisServiceFactory`):
- Runtime service switching without app restart
- Factory pattern with dependency injection support
- User preference-based service selection

**Preferences Management** (`AnalysisPreferences`):
- SharedPreferences-based storage for service selection
- Persistent user choice between Firebase AI and Backend API
- Default Firebase AI with Backend API as optional alternative

## Development Guidelines

### Package Structure
The project follows a consistent package naming convention:
- **Main Application**: `com.hackathon.powerguard.*`
- **AiInferenceSDK**: `com.powerguard.llm.*`

**Key Package Organization:**
- `actionable/` - Battery optimization handlers and execution system
- `collector/` - System and usage data collection services
- `data/` - Data layer with repositories, models, and database integration
- `di/` - Dependency injection modules organized by feature
- `domain/` - Use cases and business logic
- `services/` - Background services and system integration
- `ui/` - Jetpack Compose UI components, screens, and ViewModels
- `utils/` - Shared utility classes and helper functions
- `widget/` - App widget implementation

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
- **DI**: Dagger Hilt with named bindings for service switching
- **Database**: Room with coroutines
- **Charts**: MPAndroidChart for analytics visualization
- **AI**: Firebase AI integration via Firebase SDK
- **Networking**: Retrofit 2.9.0 + OkHttp 4.12.0 for Backend API
- **JSON**: Gson for serialization/deserialization
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
- **Firebase AI**: Network-dependent response times (~2-4s), minimal local memory usage
- **Backend API**: Network-dependent response times (~1-3s), efficient JSON processing
- **Service Switching**: Runtime switching with no app restart required
- **Battery optimization**: Network requests optimized for battery efficiency
- **Background processing**: Uses coroutines with appropriate dispatchers
- **Database**: Room with KTX extensions for suspend functions

### Debugging & Development
- **Logs**: Use structured logging with class-specific tags
- **Debug builds**: Include UI tooling and test manifests
- **ProGuard**: Disabled in debug builds for easier debugging
- **Utilities**: Centralized TimeUtils for date/time formatting across the app

### Recent Changes & Improvements
- **Dual AI Architecture**: Implemented switchable Firebase AI and Backend API services
- **Service Factory Pattern**: Added runtime service switching with user preferences
- **Backend API Integration**: Full REST API support with Retrofit + OkHttp
- **Network Layer**: Added API models, mappers, and proper error handling
- **Package Structure**: Corrected package naming from `powergaurd` to `powerguard` throughout the entire codebase
- **Enhanced DI**: Updated Hilt modules with named bindings for service differentiation
- **Architecture**: Improved separation of concerns with clearer layer boundaries
- **Build System**: Optimized Gradle configuration for faster builds and better caching
- **Development Tools**: Added comprehensive shell scripts for quick development workflow

### Code Quality Standards
- Follow clean architecture principles with clear separation between UI, Domain, and Data layers
- Use dependency injection (Hilt) for testable and maintainable code
- Implement proper error handling and resource management
- Write unit tests for business logic (use cases, repositories, ViewModels)
- Use coroutines for asynchronous operations with appropriate dispatchers
- Follow Android development best practices and Material Design guidelines
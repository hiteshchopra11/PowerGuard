# PowerGuard - AI-Powered Battery & Data Optimization

<div align="center">
  
  [![Android](https://img.shields.io/badge/Android-15+-green.svg)](https://android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org)
  [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
  [![AI](https://img.shields.io/badge/AI-Firebase%20%2B%20Backend-purple.svg)]()
  
</div>

## 🎯 Overview

PowerGuard is an intelligent Android application that leverages **dual AI service architecture** to analyze your device's power consumption and data usage patterns. The app supports seamless switching between **Firebase AI** for cloud-based inference and a **custom backend API** for specialized analysis, providing personalized, actionable recommendations to optimize battery life and reduce data consumption.

## 🔗 Related Repositories

### 📡 **Backend API Project**
> **🌐 Backend Repository**: **[PowerGuardBackend](https://github.com/hiteshchopra11/PowerGuardBackend)**
> 
> The PowerGuard Android app integrates with a dedicated backend API service that provides specialized device analysis and optimization recommendations. The backend project contains:
> - REST API server implementation
> - Custom AI analysis algorithms for battery and data optimization
> - API documentation and deployment configurations
> - Database schemas and migration scripts

## ✨ Key Features

### 🧠 **Dual AI Service Architecture**
- **Firebase AI Integration**: Cloud-based inference with Google's Firebase AI platform
- **Backend API Support**: Custom REST API integration for specialized analysis
- **Runtime Switching**: Seamlessly switch between services without app restart
- **Intelligent Fallback**: Automatic service availability detection

### 🔋 **Battery Optimization**
- **App Standby Management**: Intelligently manages background app behavior using system standby buckets
- **Wake Lock Control**: Prevents apps from keeping your device awake unnecessarily
- **Battery Alerts**: Customizable low-battery notifications with smart thresholds
- **Force Stop**: Intelligent app termination for battery-draining applications
- **Real-time Monitoring**: Continuous battery usage pattern analysis

### 📱 **Data Management**
- **Background Data Restriction**: Blocks unnecessary background data usage per app
- **Network Usage Monitoring**: Real-time tracking with historical analysis
- **Smart Alerts**: Proactive notifications before hitting data limits
- **Network Policy Management**: Fine-grained control over app network access
- **Data Usage Analytics**: Visual insights into app-specific consumption

### 🎨 **Modern UI/UX**
- **Material Design 3**: Clean, intuitive interface with dynamic theming
- **Jetpack Compose**: Modern declarative UI framework
- **Dashboard Analytics**: Interactive charts and visual insights
- **Responsive Design**: Optimized for various screen sizes
- **Dark/Light Theme**: System-adaptive theming support

## 🏗️ Architecture

PowerGuard follows **Clean Architecture** principles with clear separation of concerns and dependency injection:

```
┌─────────────────────────────────────────────────────────────┐
│                          UI Layer                           │
│    Jetpack Compose • Material Design 3 • ViewModels        │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                           │
│       Use Cases • Business Logic • Repository Contracts     │
├─────────────────────────────────────────────────────────────┤
│                        Data Layer                           │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐│
│  │   Firebase AI   │ │   Backend API   │ │   Local Room    ││
│  │   (Primary)     │ │  (Switchable)   │ │   Database      ││
│  └─────────────────┘ └─────────────────┘ └─────────────────┘│
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐│
│  │    Usage        │ │   Actionable    │ │   System        ││
│  │  Collectors     │ │   Executors     │ │   Services      ││
│  └─────────────────┘ └─────────────────┘ └─────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 🧩 **Core Components**

#### **Dual AI Service Architecture**
- **PowerGuardAnalysisService**: Common interface for both AI implementations
- **PowerGuardFirebaseService**: Firebase AI integration wrapper
- **PowerGuardBackendService**: REST API implementation using Retrofit
- **AnalysisServiceFactory**: Factory pattern for runtime service switching
- **AnalysisPreferences**: Persistent user preference management

#### **Data Collection & Processing**
- **UsageDataCollector**: Monitors app usage patterns and system metrics
- **DeviceInfoProvider**: Gathers device capabilities and current state
- **PowerGuardDatabase**: Room database for caching insights and actionables
- **ModelMapper**: Converts between internal models and API formats

#### **Actionable System**
- **ActionableExecutor**: Coordinates optimization strategy execution
- **Battery Handlers**: App standby buckets, wake locks, force stop
- **Data Handlers**: Background data restrictions, network policies
- **Monitoring Handlers**: Battery and data usage alerts

#### **Dependency Injection (Hilt)**
- **AiModule**: AI service providers with named bindings
- **NetworkModule**: Retrofit and OkHttp configuration
- **DatabaseModule**: Room database setup
- **AppModule**: Core application dependencies

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Minimum SDK**: Android 15 (API level 35)
- **Target SDK**: Android 15 (API level 35)
- **JDK**: 11 or higher

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/PowerGuard.git
   cd PowerGuard
   ```

2. **Configure Firebase** (for Firebase AI service)
   - Follow the [Firebase AI setup guide](https://firebase.google.com/docs/ai-logic) to create your project
   - Download `google-services.json` from your Firebase project
   - Place `google-services.json` in the `app/` directory (this file is gitignored for security)
   - Firebase AI requires a valid Firebase project with AI services enabled

3. **Grant system permissions**
   ```bash
   ./grant_permissions.sh
   ```

4. **Build and run**
   ```bash
   ./run_android.sh
   # Or for quick install: ./run_android.sh --install-only
   ```

### System Requirements

PowerGuard requires the following permissions for full functionality:

#### **Core Permissions**
- **PACKAGE_USAGE_STATS**: Monitor app usage patterns
- **BATTERY_STATS**: Access battery consumption data
- **QUERY_ALL_PACKAGES**: Query installed app information

#### **System-Level Permissions** (requires ADB or root)
- **WRITE_SECURE_SETTINGS**: Modify system power settings
- **FORCE_STOP_PACKAGES**: Stop problematic apps
- **CHANGE_APP_IDLE_STATE**: Modify app standby states
- **MANAGE_NETWORK_POLICY**: Control data usage restrictions

> **⚠️ Important**: Actionable execution (applying optimizations) requires **root access** or system-level permissions. While the app can analyze and provide recommendations on any device, actually applying battery and data optimizations requires elevated privileges. Use `./grant_permissions.sh` for automated setup during development.

## 🔧 AI Service Configuration

### Firebase AI (Default)
- **Provider**: Google Firebase AI platform
- **Network**: Required for cloud inference
- **Setup**: Configure `google-services.json` in your Firebase project
- **Features**: Advanced natural language processing, broad knowledge base

### Backend API (Optional)
- **Endpoint**: `https://powerguardbackend.onrender.com/api/analyze`
- **Protocol**: REST API with JSON request/response
- **Network**: Required for API calls
- **Features**: Specialized device analysis, custom optimization strategies

### Runtime Switching
Users can toggle between services via the app's settings menu:
1. Open PowerGuard app
2. Tap the 3-dots menu (top-right)
3. Toggle "Use backend (cloud)" switch
4. Service switches immediately without restart

## 📊 API Integration

### Backend API Contract

**Request Format:**
```json
{
  "deviceId": "android-device-001",
  "timestamp": 1725724800,
  "battery": {
    "level": 45.0,
    "temperature": 35.0,
    "voltage": 3.7,
    "isCharging": false,
    "chargingType": "none",
    "health": 95,
    "capacity": 3000.0,
    "currentNow": 500.0
  },
  "apps": [
    {
      "packageName": "com.example.app",
      "appName": "Example App",
      "batteryUsage": 15.5,
      "dataUsage": 1024000,
      "screenTime": 3600000
    }
  ],
  "networkUsage": {
    "totalDataUsed": 512000000,
    "wifiDataUsed": 256000000,
    "mobileDataUsed": 256000000
  }
}
```

**Response Format:**
```json
{
  "id": "analysis-1725724800",
  "success": true,
  "timestamp": 1725724800,
  "message": "Analysis completed successfully",
  "responseType": "BATTERY_OPTIMIZATION",
  "actionable": [
    {
      "id": "action-001",
      "type": "KILL_APP",
      "packageName": "com.example.app",
      "priority": "HIGH",
      "impact": "15% battery improvement expected"
    }
  ],
  "insights": [
    {
      "type": "BATTERY_DRAIN",
      "title": "High Battery Usage Detected",
      "description": "Example App is consuming 15% of battery",
      "severity": "HIGH"
    }
  ]
}
```

## 🛠️ Development

### Build Commands
```bash
# Debug build
./gradlew :app:assembleDebug

# Quick install and run
./run_android.sh

# Install existing APK only
./run_android.sh --install-only

# Grant required permissions
./grant_permissions.sh
```

### Testing
```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Specific test class
./gradlew testDebugUnitTest --tests "ClassName"
```

### Key Dependencies
- **UI**: Jetpack Compose with Material 3
- **DI**: Dagger Hilt for dependency injection
- **Database**: Room with coroutines support
- **Networking**: Retrofit + OkHttp for backend API
- **Firebase**: Firebase AI for cloud inference
- **Testing**: JUnit, MockK, Coroutines Test

## 📱 Usage

### Dashboard
- View real-time battery and data usage statistics
- Check AI-generated optimization recommendations
- Monitor app-specific consumption patterns
- Apply suggested optimizations with one tap (requires root access)

### Explore Screen
- Interactive chat interface with AI services
- Ask questions about device performance
- Get personalized optimization advice
- Test different optimization queries

### History Screen
- View past AI analysis results
- Track optimization effectiveness over time
- Compare before/after performance metrics
- Export historical data for analysis

### Settings & Configuration
- Switch between Firebase AI and Backend API
- Configure alert thresholds
- Manage system permissions
- Customize notification preferences

## 🏃‍♂️ Performance

### Firebase AI Service
- **Response Time**: ~2-4 seconds (network dependent)
- **Memory Usage**: Minimal client-side footprint
- **Network**: Requires internet connection
- **Privacy**: Data processed in Google's secure cloud

### Backend API Service  
- **Response Time**: ~1-3 seconds (API dependent)
- **Memory Usage**: Minimal client-side footprint
- **Network**: Requires internet connection
- **Privacy**: Data sent to custom backend endpoint

### Local Processing
- **Database**: SQLite with Room ORM
- **Collections**: Efficient background data gathering
- **Actions**: System-level optimization execution
- **Memory**: ~50MB average app memory usage

## 🔒 Privacy & Security

- **Local First**: Core functionality works offline
- **Minimal Data**: Only necessary device metrics collected
- **User Control**: Full transparency on data sent to AI services
- **Service Choice**: Users control which AI service processes their data
- **No Tracking**: No user behavior tracking or analytics

## 🤝 Contributing

We welcome contributions! Please read our contributing guidelines before submitting PRs.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Google Firebase** for AI platform integration
- **Android Open Source Project** for system APIs
- **Jetpack Compose** for modern Android UI
- **Material Design** for UI/UX guidelines
- **Retrofit & OkHttp** for robust networking

---

<div align="center">
  <strong>PowerGuard - Intelligent Power Management for Android</strong><br>
  Built with modern Android architecture and dual AI services
</div>
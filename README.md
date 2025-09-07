# PowerGuard - AI-Powered Battery & Data Optimization

<div align="center">
  <img src="app/src/main/res/drawable/ic_power_guard.xml" alt="PowerGuard Logo" width="120" height="120">
  
  [![Android](https://img.shields.io/badge/Android-15+-green.svg)](https://android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org)
  [![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![AI](https://img.shields.io/badge/AI-On--device%20LLM-purple.svg)]()
</div>

## 🎯 Overview

PowerGuard is an intelligent Android application that leverages **on-device AI** to analyze your device's power consumption and data usage patterns. Using an **on-device LLM**, PowerGuard provides personalized, actionable recommendations to optimize battery life and reduce data consumption without compromising your privacy.

## 🎥 Demo Video

> **Coming Soon!** 📹 
> 
> A comprehensive video demonstration showing PowerGuard in action will be available here. The demo will showcase:
> - Real-time battery optimization
> - AI-powered recommendations
> - Data usage management
> - User interface walkthrough

---

## ✨ Key Features

### 🧠 **AI-Powered Analysis**
- **On-Device LLM**: Uses Google's Gemma model for complete privacy
- **Smart Recommendations**: Contextual suggestions based on usage patterns
- **Natural Language Interface**: Chat with your device about power optimization

### 🔋 **Battery Optimization**
- **App Standby Management**: Intelligently manages background app behavior
- **Wake Lock Control**: Prevents apps from keeping your device awake unnecessarily
- **Battery Alerts**: Customizable low-battery notifications
- **Force Stop**: Smart app termination for battery-draining applications

### 📱 **Data Management**
- **Background Data Restriction**: Blocks unnecessary background data usage
- **Data Usage Monitoring**: Real-time tracking and historical analysis
- **Smart Alerts**: Proactive notifications before hitting data limits
- **Network Policy Management**: Fine-grained control over app network access

### 🎨 **Modern UI/UX**
- **Material Design 3**: Clean, intuitive interface
- **Dark/Light Theme**: Adaptive theming support
- **Dashboard Analytics**: Visual insights into your device performance
- **Interactive Chat**: Conversational AI for easy optimization

## 🏗️ Architecture

PowerGuard follows a modern Android architecture with clean separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                          UI Layer                           │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐│
│  │   Dashboard     │ │    Explore      │ │    History      ││
│  │    Screen       │ │    Screen       │ │    Screen       ││
│  └─────────────────┘ └─────────────────┘ └─────────────────┘│
│                             │                               │
├─────────────────────────────┼─────────────────────────────────┤
│                     ViewModel Layer                         │
│  ┌─────────────────┐       │       ┌─────────────────┐      │
│  │   Dashboard     │       │       │    Explore      │      │
│  │   ViewModel     │───────┼───────│   ViewModel     │      │
│  └─────────────────┘       │       └─────────────────┘      │
│                             │                               │
├─────────────────────────────┼─────────────────────────────────┤
│                      Domain Layer                           │
│  ┌─────────────────┐       │       ┌─────────────────┐      │
│  │    Use Cases    │───────┼───────│  Repositories   │      │
│  └─────────────────┘       │       └─────────────────┘      │
│                             │                               │
├─────────────────────────────┼─────────────────────────────────┤
│                        Data Layer                           │
│  ┌─────────────────┐ ┌─────┴─────┐ ┌─────────────────┐      │
│  │   Local DB      │ │   AI      │ │   Collectors    │      │
│  │   (Room)        │ │   SDK     │ │   (Usage/Bat)   │      │
│  └─────────────────┘ └───────────┘ └─────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 🧩 **Core Components**

- **AI Inference SDK**: Custom on-device LLM integration
- **Actionable Engine**: Executes AI-generated optimization strategies
- **Usage Collectors**: Monitors device metrics and app behavior
- **Permission Manager**: Handles system-level permissions gracefully
- **Notification System**: Smart alerts and recommendations

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **Minimum SDK**: Android 15 (API level 35)
- **Target SDK**: Android 15 (API level 35)
- **JDK**: 17 or higher

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/PowerGuard.git
   cd PowerGuard
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned PowerGuard directory

3. **Configure Gemma API** (Optional)
   - Add your Gemma API credentials to `app/src/main/assets/gemma_api.properties`
   - The app works offline by default with on-device inference

4. **Grant necessary permissions**
   ```bash
   ./grant_permissions.sh
   ```

5. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   # Or use Android Studio's run button
   ```

### System Requirements

PowerGuard requires several system-level permissions for full functionality:

- **Usage Stats Access**: Monitor app usage patterns
- **Network Policy Management**: Control background data
- **Battery Optimization**: Manage app standby states
- **Notification Access**: Display smart alerts

> **Note**: Some features require system-level access and work best on rooted devices or when installed as a system app.

## 💡 Usage

### 1. **Initial Setup**
- Launch PowerGuard and grant required permissions
- The app will automatically start collecting device metrics
- Gemma LLM initializes in the background for AI analysis

### 2. **Dashboard Overview**
- View real-time battery and data usage statistics
- Check AI-generated optimization recommendations
- Monitor app-specific consumption patterns

### 3. **Interactive Chat**
- Navigate to the "Explore" tab
- Ask questions about your device's performance
- Get personalized optimization advice from the AI

### 4. **Apply Optimizations**
- Review AI recommendations on the dashboard
- Tap on optimization cards to apply suggested changes
- Monitor improvement in battery life and data usage

### 5. **Historical Analysis**
- Visit the "History" tab to view past insights
- Track optimization effectiveness over time
- Compare before/after performance metrics

## 🛠️ Technical Details

### **On-Device AI**
PowerGuard uses a compact on-device LLM optimized for mobile devices:
- **Inference Time**: ~2-3 seconds on modern devices
- **Memory Usage**: ~1.5GB RAM during active inference
- **Privacy**: All processing happens locally, no data leaves your device

### **Optimization Strategies**
- **Standby Buckets**: Uses `UsageStatsManager` to control app background behavior
- **Network Policies**: Leverages `NetworkPolicyManager` for data restrictions
- **Wake Lock Management**: Controls `PowerManager` wake locks
- **Force Stop**: Utilizes `ActivityManager` for resource management

### **Data Collection**
- **Battery Stats**: System-level battery consumption data
- **Network Usage**: Per-app data consumption metrics
- **App Usage**: Usage patterns and screen time statistics
- **System Events**: Boot, charging, and connectivity events

## 🎯 Roadmap

- [ ] **Machine Learning Models**: Additional on-device ML models for enhanced predictions
- [ ] **Widget Support**: Home screen widget for quick optimization access
- [ ] **Tasker Integration**: Automation support for power users
- [ ] **Export Analytics**: Data export for advanced users
- [ ] **Multi-Device Sync**: Cross-device optimization insights (privacy-preserving)

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Google AI** for on-device LLM research
- **Android Open Source Project** for system APIs
- **Material Design** for UI/UX guidelines
- **Jetpack Compose** for modern Android UI development

## 📞 Support

- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/your-username/PowerGuard/issues)
- 💡 **Feature Requests**: [GitHub Discussions](https://github.com/your-username/PowerGuard/discussions)
- 📧 **Contact**: your-email@example.com

---

<div align="center">
  <strong>PowerGuard - Intelligent Power Management for Android</strong><br>
  Made with ❤️ using AI and modern Android development practices
</div> 
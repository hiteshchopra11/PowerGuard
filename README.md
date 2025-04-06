# PowerGuard: Battery & Network Optimization System

## Project Overview

PowerGuard is an innovative system designed to optimize battery and network usage on
mobile devices. It collects real device usage data to provide insights and optimize device performance.

### Key Features

1. **Real-time Device Monitoring**: Collects actual device data using the UsageDataCollector
2. **Actionable Insights**: Generates and stores insights from device data analysis
3. **Battery Information**: Detailed battery statistics and health information
4. **Network Usage Monitoring**: Tracks data usage across applications
5. **Historical Insights**: View past insights with timestamps

## System App Requirements

**Important Note:** PowerGuard is designed to run as a system app to access the necessary
permissions for deep optimization. This requires:

1. A rooted device, or
2. An Android emulator with system privileges, or
3. Integration into a custom ROM

For installation instructions, please see [SYSTEM_APP_INSTALLATION.md](SYSTEM_APP_INSTALLATION.md).

### Installation Location

PowerGuard is installed as a privileged system app in `/system/priv-app` to maximize permission
access. This location grants the app more automatic permissions compared to the standard
`/system/app` location.

After installation, you must run the provided permissions script to handle permissions that require
special granting:

```bash
# Make the script executable
chmod +x grant_permissions.sh

# Run the script to grant permissions
./grant_permissions.sh
```

## Features

### Core Functionality

- Real device data collection
- Battery stats visualization
- Network usage monitoring
- Insights history tracking

### User Interface

The app provides three main screens:

1. **Dashboard**: Shows battery status, network usage information, and a query prompt
2. **Battery**: Displays detailed battery information using real device data
3. **History**: Shows insights generated from device data analysis with timestamps

## Architecture

PowerGuard follows a modern Android architecture:

- **MVVM Pattern**: Clear separation of UI, business logic, and data
- **Dependency Injection**: Uses Hilt for dependency management
- **Coroutines**: Handles asynchronous operations efficiently
- **WorkManager**: Manages background tasks for data collection
- **Room Database**: Stores insights for historical tracking
- **Jetpack Compose**: Powers the modern UI components

## Project Structure

- **ui**: UI components and screens
- **worker**: Background processing components
- **data**: Data models and repositories
- **domain**: Use cases and business logic
- **collector**: Device data collectors
- **di**: Dependency injection modules

## Getting Started

1. Clone the repository
2. Open the project in Android Studio
3. Build and run on your device or emulator

## License

MIT License 
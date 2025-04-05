# PowerGuard API Contract

This document outlines the API contract between the PowerGuard Android application and the backend server.

## Device Data Collection API

### Endpoint
```
POST /api/v1/analyze-device-data
```

### Request Format

```json
{
  "deviceId": "string",
  "timestamp": "long",
  "battery": {
    "level": "int",
    "temperature": "float",
    "voltage": "int",
    "isCharging": "boolean",
    "chargingType": "string",
    "health": "int"
  },
  "memory": {
    "totalRam": "long",
    "availableRam": "long",
    "lowMemory": "boolean",
    "threshold": "long"
  },
  "cpu": {
    "usage": "float",
    "temperature": "float",
    "frequencies": ["long"]
  },
  "network": {
    "type": "string",
    "strength": "int",
    "isRoaming": "boolean",
    "dataUsage": {
      "foreground": "long",
      "background": "long"
    }
  },
  "apps": [
    {
      "packageName": "string",
      "processName": "string",
      "appName": "string",
      "isSystemApp": "boolean",
      "lastUsed": "long",
      "foregroundTime": "long",
      "backgroundTime": "long",
      "batteryUsage": "float",
      "dataUsage": {
        "foreground": "long",
        "background": "long"
      },
      "memoryUsage": "long",
      "cpuUsage": "float",
      "notifications": "int",
      "crashes": "int"
    }
  ],
  "settings": {
    "batteryOptimization": "boolean",
    "dataSaver": "boolean",
    "powerSaveMode": "boolean",
    "adaptiveBattery": "boolean",
    "autoSync": "boolean"
  },
  "deviceInfo": {
    "manufacturer": "string",
    "model": "string",
    "osVersion": "string",
    "sdkVersion": "int",
    "screenOnTime": "long"
  }
}
```

### Response Format

```json
{
  "success": "boolean",
  "timestamp": "long",
  "message": "string",
  "actionables": [
    {
      "id": "string",
      "type": "string",
      "packageName": "string",
      "priority": "int",
      "description": "string",
      "reason": "string",
      "parameters": {
        "key1": "value1",
        "key2": "value2"
      }
    }
  ],
  "insights": [
    {
      "type": "string",
      "title": "string",
      "description": "string",
      "severity": "string"
    }
  ],
  "batteryScore": "int",
  "dataScore": "int",
  "performanceScore": "int",
  "estimatedSavings": {
    "batteryMinutes": "int",
    "dataMB": "int",
    "storageMB": "int"
  }
}
```

## Sample Request JSON

```json
{
  "deviceId": "eec89568-6a83-4832-a070-7dd661bc9c67",
  "timestamp": 1697292350000,
  "battery": {
    "level": 65,
    "temperature": 32.5,
    "voltage": 3950,
    "isCharging": false,
    "chargingType": "not_charging",
    "health": 2
  },
  "memory": {
    "totalRam": 8589934592,
    "availableRam": 2147483648,
    "lowMemory": false,
    "threshold": 524288000
  },
  "cpu": {
    "usage": 12.5,
    "temperature": 38.2,
    "frequencies": [1800000, 2000000, 1950000, 1800000]
  },
  "network": {
    "type": "WIFI",
    "strength": 3,
    "isRoaming": false,
    "dataUsage": {
      "foreground": 52428800,
      "background": 15728640
    }
  },
  "apps": [
    {
      "packageName": "com.example.highbatterydrain",
      "processName": "com.example.highbatterydrain",
      "appName": "High Battery Drain App",
      "isSystemApp": false,
      "lastUsed": 1697292000000,
      "foregroundTime": 1800000,
      "backgroundTime": 3600000,
      "batteryUsage": 5.2,
      "dataUsage": {
        "foreground": 10485760,
        "background": 5242880
      },
      "memoryUsage": 209715200,
      "cpuUsage": 4.8,
      "notifications": 15,
      "crashes": 0
    },
    {
      "packageName": "com.example.highdatausage",
      "processName": "com.example.highdatausage",
      "appName": "High Data Usage App",
      "isSystemApp": false,
      "lastUsed": 1697291000000,
      "foregroundTime": 900000,
      "backgroundTime": 7200000,
      "batteryUsage": 2.1,
      "dataUsage": {
        "foreground": 15728640,
        "background": 31457280
      },
      "memoryUsage": 104857600,
      "cpuUsage": 1.2,
      "notifications": 8,
      "crashes": 1
    }
  ],
  "settings": {
    "batteryOptimization": true,
    "dataSaver": false,
    "powerSaveMode": false,
    "adaptiveBattery": true,
    "autoSync": true
  },
  "deviceInfo": {
    "manufacturer": "Google",
    "model": "Pixel 7",
    "osVersion": "Android 13",
    "sdkVersion": 33,
    "screenOnTime": 10800000
  }
}
```

## Sample Response JSON

```json
{
  "success": true,
  "timestamp": 1697292351000,
  "message": "Analysis completed successfully",
  "actionables": [
    {
      "id": "a1b2c3d4",
      "type": "OPTIMIZE_BATTERY",
      "packageName": "com.example.highbatterydrain",
      "priority": 1,
      "description": "This app is consuming excessive battery power in the background",
      "reason": "High background battery drain detected",
      "parameters": {
        "restrictBackground": true,
        "optimizeBatteryUsage": true
      }
    },
    {
      "id": "e5f6g7h8",
      "type": "RESTRICT_BACKGROUND",
      "packageName": "com.example.highdatausage",
      "priority": 2,
      "description": "App is using significant data in the background",
      "reason": "High background data usage detected",
      "parameters": {
        "restrictBackgroundData": true
      }
    },
    {
      "id": "i9j0k1l2",
      "type": "KILL_APP",
      "packageName": "com.example.highbatterydrain",
      "priority": 3,
      "description": "Force stop app to immediately save battery",
      "reason": "Critical battery usage detected",
      "parameters": {}
    }
  ],
  "insights": [
    {
      "type": "BATTERY",
      "title": "Battery Drain Sources Identified",
      "description": "We found 2 apps that are significantly draining your battery",
      "severity": "HIGH"
    },
    {
      "type": "DATA",
      "title": "Background Data Usage Alert",
      "description": "1 app is using excessive data in the background",
      "severity": "MEDIUM"
    }
  ],
  "batteryScore": 65,
  "dataScore": 78,
  "performanceScore": 82,
  "estimatedSavings": {
    "batteryMinutes": 45,
    "dataMB": 50,
    "storageMB": 20
  }
}
```

## Actionable Types

The `type` field in actionables can have the following values:

1. `OPTIMIZE_BATTERY`: Apply battery optimization settings for an app
2. `RESTRICT_BACKGROUND`: Restrict background processes/data for an app
3. `KILL_APP`: Force stop an app
4. `OPTIMIZE_STORAGE`: Clear cache or optimize storage usage
5. `ENABLE_BATTERY_SAVER`: Turn on system battery saver mode
6. `ENABLE_DATA_SAVER`: Turn on system data saver mode
7. `ADJUST_SYNC_SETTINGS`: Modify auto-sync settings
8. `CATEGORIZE_APP`: Categorize an app for specialized handling

## HTTP Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Invalid request format
- `401 Unauthorized`: Authentication failed
- `403 Forbidden`: Insufficient permissions
- `500 Internal Server Error`: Server-side error 
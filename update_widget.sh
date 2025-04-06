#!/bin/bash
# Script to update the PowerGuard widget for testing

echo "Updating PowerGuard widget..."

# Send a broadcast to update all widgets with a test message
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n com.hackathon.powergaurd/.widget.PowerGuardWidget

echo "Widget update broadcast sent."

# Optional: Send a specific action to test battery saving feature
# adb shell am broadcast -a com.hackathon.powergaurd.ACTION_SAVE_BATTERY -n com.hackathon.powergaurd/.widget.PowerGuardWidget

echo "Done!" 
# Nearby Shelter

Nearby Shelter is an Android application designed to help users find the closest safety shelters in real-time during emergency alerts. It monitors incoming notifications for emergency warnings and provides immediate navigation guidance.

## 🚀 Features

- **Real-time Alert Detection**: Monitors system notifications for emergency and shelter-related alerts.
- **Dynamic Mode Selection**:
    - **Silent Mode**: Mutes all alerts.
    - **Critical Only**: Notifies only for immediate emergency shelter alerts.
    - **All Alerts**: Full protection, receiving both warnings and alerts.
- **Smart Navigation**: Automatically identifies the closest shelter based on your current location.
- **Lock Screen Support**: Can wake up the screen and show a map even when the device is locked.
- **Privacy Focused**: Location data is used only to find the nearest shelter locally on the device.

## 🛠 Required Permissions

To provide life-saving alerts effectively, the app requires several permissions:
- **Location (Fine & Background)**: To accurately find the closest shelter even when the app is not in use.
- **Notification Access**: To detect emergency alerts from other official sources.
- **Display Over Other Apps**: To show emergency information over the lock screen.
- **Disable Battery Optimization**: To ensure the alert listener stays active in the background.

## 🏗 Tech Stack

- **UI**: Jetpack Compose (Modern declarative UI)
- **Language**: Kotlin & Java
- **Location Services**: Google Play Services Location
- **Architecture**: NotificationListenerService for alert detection

## 👨‍💻 Developed By

Made with ❤️ by **Amit Mokady**

---
*Disclaimer: This app is intended for informational purposes and should be used in conjunction with official local emergency services.*

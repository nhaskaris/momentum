# Crash Reporting Implementation

Implemented a mechanism to capture app crashes, save them to local storage, and allow users to send them to developers via the Settings screen.

## Changes

### [NEW] [CrashHandler.kt](file:///Users/nhaskaris/StudioProjects/momentum/app/src/main/java/com/eliteonetube/momentum/logic/CrashHandler.kt)
A utility class that implements `Thread.UncaughtExceptionHandler`.
- Captures stack traces and device metadata (Model, OS version, Timestamp).
- Saves the report to `crash_report.txt` in the app's internal storage.
- Provides static methods to retrieve and clear the report.

### [NEW] [MomentumApplication.kt](file:///Users/nhaskaris/StudioProjects/momentum/app/src/main/java/com/eliteonetube/momentum/MomentumApplication.kt)
Custom `Application` class to initialize the `CrashHandler` as early as possible.

### [MODIFY] [AndroidManifest.xml](file:///Users/nhaskaris/StudioProjects/momentum/app/src/main/AndroidManifest.xml)
Registered `MomentumApplication` in the manifest.

### [MODIFY] [ProfileScreen.kt](file:///Users/nhaskaris/StudioProjects/momentum/app/src/main/java/com/eliteonetube/momentum/ui/theme/ProfileScreen.kt)
Added UI to the "App Settings" section:
- A "Send Crash Report" button appears only if a crash was previously recorded.
- Tapping the button opens a system share sheet to send the report (e.g., via Email).
- The report is cleared after being shared.

## Verification
- Verified that the project builds successfully.
- The `CrashHandler` uses the default exception handler to ensure the app still follows standard crash behavior after logging.
- The UI is dynamic and only shows the report button when necessary.

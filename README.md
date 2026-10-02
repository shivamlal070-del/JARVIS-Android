# JARVIS — Native Android Assistant & Controller

Designed specifically for **Samsung Galaxy Tab S5e (SM-T720 / SM-T725)** running **Android 11 (One UI 3.1)**.

Built with **Kotlin** and **Jetpack Compose**.

---

## 📱 Hardware & OS Target Specifications
- **Target Device**: Samsung Galaxy Tab S5e
- **Screen**: 10.5" Super AMOLED, 2560 × 1600 (16:10 aspect ratio)
- **OS**: Android 11 (API Level 30) with Samsung One UI 3.1
- **Architecture**: Modular MVVM with Services & Subsystems

---

## 🛠️ Step-by-Step Setup on the Physical Android Device

### Step 1: Build & Install the APK
Using Android Studio or Gradle CLI:
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Step 2: Enable the Accessibility Service (Samsung One UI 3.1)
To allow JARVIS to inspect UI elements, click buttons in external apps, and read screen content:
1. Open tablet **Settings** → **Accessibility**.
2. Tap **Installed Services** (or **Downloaded Services**).
3. Find **JARVIS Assistant Controller**.
4. Toggle the switch to **ON**.
5. Tap **Allow** when the system prompt asks to allow JARVIS full control of interactions.

*ADB Shortcut (Optional for Developers):*
```bash
adb shell settings put secure enabled_accessibility_services com.jarvis.assistant/com.jarvis.assistant.core.device.JarvisAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

---

### Step 3: Grant Runtime Audio & Camera Permissions
Launch the JARVIS application on your tablet. When prompted:
1. **Microphone**: Tap **While using the app** (or allow in foreground).
2. **Camera**: Allow access to photograph Class 11 DPP questions and notebook calculations.

*ADB Shortcut:*
```bash
adb shell pm grant com.jarvis.assistant android.permission.RECORD_AUDIO
adb shell pm grant com.jarvis.assistant android.permission.CAMERA
```

---

### Step 4: Exclude from Samsung One UI Battery Optimization
Samsung One UI aggressively kills background services if not excluded:
1. Go to **Settings** → **Apps** → **JARVIS**.
2. Tap **Battery**.
3. Select **Unrestricted** (or turn off **Optimize battery usage** for JARVIS).
4. In **Settings** → **Device Care** → **Battery** → **Background usage limits**, ensure JARVIS is added to **Never sleeping apps**.

---

### Step 5: Configure your Google Gemini API Key
1. In JARVIS, navigate to **Settings** on the left rail.
2. Under **Google Gemini API Key**, paste your key.
3. Tap **Save Key Securely**. It is stored in `EncryptedSharedPreferences` on your Tab S5e.

---

## 🎙️ Spoken Voice Commands
- `"Hello Jarvis"` → Speaks *"Yes, I'm listening."*
- `"Open YouTube"` / `"Open WhatsApp"` / `"Open ChatGPT"` / `"Open Gemini"`
- `"Can you start a 25-minute study timer?"`
- `"Remind me to solve physics DPP at 7 PM"`
- `"How much battery do I have?"`
- `"Read what's on the screen"`
- `"Go back"`
- `"Is this website safe?"`
- `"Start study mode"` → Class 11 Physics/Chemistry/Math step-by-step DPP tutor.

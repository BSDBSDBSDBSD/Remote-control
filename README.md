# 🖥️ Bluetooth Remote Control

שליטה מרחוק מלאה במכשיר אנדרואיד דרך Bluetooth - מסך חי, לחיצות, אפליקציות, shell.

---

## ⚡ Build מהיר

```bash
git clone https://github.com/BSDBSDBSDBSD/Remote-control.git
cd Remote-control
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ דרישות Build

| כלי | גרסה |
|-----|-------|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17 |
| compileSdk | 33 (Android 13) |
| minSdk | 28 (Android 9) |

---

## 🚀 שימוש

### מצב שרת (המכשיר הנשלט):

**עם Root (מומלץ - פשוט יותר):**
1. הפעל מתג Root
2. לחץ "הפעל שרת" - לא צריך אישורים נוספים

**בלי Root:**
1. כנס להגדרות → נגישות (Accessibility)
2. הפעל את **"BT Remote Control"** מרשימת השירותים
3. לחץ "הפעל שרת"
4. אשר בקשת **צילום מסך** (MediaProjection) שתופיע

### מצב לקוח (המכשיר השולט):
1. ודא **Bluetooth מזווג** בין שני המכשירים
2. לחץ "התחבר ושלוט"
3. בחר מכשיר מהרשימה
4. מסך המכשיר הרחוק יופיע בזמן אמת

---

## 🗂️ מבנה הפרויקט

```
app/src/main/java/com/bsd/remotecontrol/
├── bluetooth/
│   └── RemoteClient.kt             # לקוח - מתחבר ושולח פקודות
├── screen/
│   └── ScreenShareService.kt       # שרת - שולח מסך + מבצע פקודות
├── input/
│   ├── InputManager.kt             # מנהל קלט (root + accessibility)
│   └── RemoteAccessibilityService.kt # שליטה בלי root
├── model/
│   └── RemoteCommand.kt            # פקודות ותגובות
└── ui/
    ├── MainActivity.kt             # מסך ראשי + DeviceScanActivity
    └── RemoteViewActivity.kt       # מסך שליטה מרחוק
```

---

## 🎮 פעולות נתמכות

| פעולה | Root | Accessibility |
|--------|------|---------------|
| לחיצה על מסך | ✅ | ✅ |
| גלילה / Swipe | ✅ | ✅ |
| כפתורי ניווט (Back/Home/Recents) | ✅ | ✅ |
| עוצמת קול | ✅ | ✅ |
| רשימת אפליקציות | ✅ | ✅ |
| פתיחת אפליקציה | ✅ | ✅ |
| סגירת אפליקציה (force-stop) | ✅ | ❌ |
| Shell command | ✅ | ❌ |
| צילום מסך בזמן אמת | ✅ | ✅ (MediaProjection) |

---

## 📡 פרוטוקול

Bluetooth RFCOMM, UUID: `fa87c0d0-afac-11de-8a39-0800200c9b77`

```
פקודות: [4 bytes len][JSON]
פריימים: [4 bytes size][JPEG bytes]
```

JPEG quality: 40% — מאוזן בין איכות למהירות על Bluetooth (~4fps)

---

## ⚙️ כוונון ביצועים

ב-`ScreenShareService.kt`:
```kotlin
const val JPEG_QUALITY = 40      // 20-60: נמוך יותר = מהיר יותר
const val FRAME_INTERVAL_MS = 250L  // 4fps. שנה ל-500 לחיסכון
```

---

## 🔐 הרשאות נדרשות

| הרשאה | מטרה |
|--------|-------|
| `BLUETOOTH_CONNECT/SCAN` | חיבור Bluetooth |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | צילום מסך |
| `BIND_ACCESSIBILITY_SERVICE` | שליטה בלי root |
| `INJECT_EVENTS` | הזרקת קלט (root) |

---

## ⚠️ הגבלות Bluetooth

Bluetooth Classic (RFCOMM) מוגבל לכ-**700KB/s** בפועל.
עם JPEG 40% ורזולוציית FHD, כל פריים ~50-100KB → ~7-14fps תיאורטי.
בפועל עם latency: ~4fps יציב.

לביצועים טובים יותר — ניתן להחליף ל-Wi-Fi Direct לאחר ה-handshake הראשוני.

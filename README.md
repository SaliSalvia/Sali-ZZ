```text
 ███   ███  █      ███  ████
█   █ █   █ █     █   █ █   █
█     █   █ █     █   █ █   █
 ███  █████ █     █████ ████
    █ █   █ █     █   █ █ █
█   █ █   █ █     █   █ █  █
 ███  █   █ █████ █   █ █   █

 ▓▓▓   ▓▓▓  ▓     ▓   ▓ ▓▓▓  ▓▓▓
▓   ▓ ▓   ▓ ▓     ▓   ▓  ▓  ▓   ▓
▓     ▓   ▓ ▓     ▓   ▓  ▓  ▓   ▓
 ▓▓▓  ▓▓▓▓▓ ▓     ▓   ▓  ▓  ▓▓▓▓▓
    ▓ ▓   ▓ ▓     ▓   ▓  ▓  ▓   ▓
▓   ▓ ▓   ▓ ▓      ▓ ▓   ▓  ▓   ▓
 ▓▓▓  ▓   ▓ ▓▓▓▓▓   ▓   ▓▓▓ ▓   ▓
```

# Sali-ZZai v2.2 — Native Android App for Z.ai

<div dir="rtl">

اپ نیتیو اندروید (Kotlin + Jetpack Compose) برای Z.ai — بدون نیاز به سرور!

## ✨ ویژگی‌ها

### 🔐 احراز هویت کامل (نسخه ۲)
- **ورود با ایمیل و گذرواژه** — هش PBKDF2WithHmacSHA256 روی دستگاه
- **ساخت حساب جدید** با نام/نام کاربری اختیاری
- **ورود با کد یک‌بار مصرف ایمیل** (۶ رقمی، انقضای ۵ دقیقه)
- **ورود با گوگل** (Credential Manager + GoogleID)
- **حالت مهمان** برای استفاده بدون حساب
- تمام داده‌ها روی دستگاه، هیچ سروری در کار نیست

### 🎨 رابط کاربری روان (مشابه DeepSeek)
- چت استریم زنده با نمایش token به token
- **کارت «روند تفکر» برای هر پاسخ**: بازشدنی، با زمان‌سنج زنده و ذخیرهٔ دائمی استدلال مدل
- **عمق تفکر قابل انتخاب**: سریع / متعادل / عمیق (`reasoning_effort` = low / high / max)
- اسکرول خودکار هوشمند + دکمهٔ «رفتن به آخرین پیام»
- انتخاب سریع مدل از نوار بالای چت (بدون رفتن به تنظیمات)
- ورودی صوتی (میکروفون) با تشخیص گفتار
- پخش صوتی پاسخ‌ها (TTS فارسی/انگلیسی) با توقف خودکار در پایان
- تم روشن / تیره / سیستمی / AMOLED با پالت و تایپوگرافی فارسی‌محور
- انیمیشن‌های روان برای ورود/خروج و پشتیبانی کامل از RTL

### 🔑 اتصال ایمن و هوشمند به Z.ai (نسخه ۲.۲)
- **اعتبارسنجی کلید API پیش از ذخیره** — کلید با یک درخواست واقعی به Z.ai تست می‌شود و کلید نامعتبر هرگز ذخیره نمی‌گردد
- **دریافت خودکار فهرست مدل‌ها** به‌محض تست کلید (و دکمهٔ به‌روزرسانی جداگانه)
- **مدل‌های رایگان پیش‌فرض**: GLM-5.3 → GLM-5.2 → GLM-5.3 Flash با انتخاب خودکار بهترین مدلِ موجود در حساب
- کارت وضعیت اتصال: زمان پاسخ، تعداد مدل‌ها، آخرین زمان تأیید و کلید ماسک‌شده
- انتخاب مدل با یک ضربه، همراه نشان «رایگان»، «بینایی» و «سریع»

### 📦 تحلیل ZIP پیشرفته
- استخراج ZIP روی دستگاه با `java.util.zip`
- پشتیبانی از **چند ZIP همزمان** + مقایسه خودکار
- نمایشگر درختی تعاملی ساختار پوشه‌ها
- استخراج متن کد/مقاله برای تحلیل علمی (شامل `.tex` و `.bib`)
- ردنویسی فایل‌های نویز (node_modules، .git، __MACOSX)

### 🤖 همه قابلیت‌های Z.ai
- **چت استریم** با GLM-5.3 / GLM-5.2 / GLM-5.3-Flash (رایگان و پیش‌فرض) و مدل‌های قدیمی‌تر GLM-4.x
- **اعتبارسنجی کلید API** و دریافت خودکار فهرست مدل‌های حساب شما
- **VLM (تحلیل تصویر)** با GLM-5.3-Flash / GLM-5V-Turbo — عکس بگیر، تحلیل کن
- **تولید تصویر** با اندازه‌های مختلف
- **جستجوی وب** زنده
- **TTS** — تبدیل متن پاسخ به صدا
- **ASR** — تبدیل صوت به متن
- **تفکر عمیق** (Reasoning mode)

### 🔒 امنیت
- رمزنگاری API Key با EncryptedSharedPreferences
- هش گذرواژه با PBKDF2 (۱۲۰۰۰ تکرار، ۲۵۶ بیت)
- مقایسه زمان ثابت برای جلوگیری از timing attack
- محدودسازی ۵ تلاش برای OTP
- بدون ذخیره توکن گوگل

## 🛠 تکنولوژی‌ها

- **Language**: Kotlin 2.0.20
- **UI**: Jetpack Compose + Material 3 (BOM 2024.09.03)
- **Architecture**: MVVM (ViewModel + StateFlow)
- **Networking**: OkHttp + Kotlin Serialization + SSE
- **Database**: Room (SQLite) v2
- **Preferences**: DataStore (Encrypted)
- **Image Loading**: Coil
- **Markdown**: Markwon
- **Auth**: AndroidX Credential Manager + Google Identity
- **Voice**: Android SpeechRecognizer + TextToSpeech (built-in)
- **Build**: Gradle 8.9 (Kotlin DSL)

## 🏗️ ساخت APK

### روش ۱: GitHub Actions (توصیه‌شده)
این پروژه Workflow ساخت APK را همراه خود دارد. ریپوی خالی را روی سیستم clone کنید، محتویات ZIP را در ریشهٔ آن استخراج کنید و پوشهٔ مخفی `.github` را هم نگه دارید. سپس در پوشهٔ پروژه اجرا کنید:

```bash
git add -A
git commit -m "Add Sali-ZZai Android app"
git branch -M main
git push -u origin main
```

پس از Push، در GitHub به **Actions → Build Native Android APK** بروید. اجرای خودکار APK امضاشده را می‌سازد؛ فایل `Sali-ZZai.apk` را از بخش **Artifacts → sali-zzai-apk** دانلود کنید. برای اجرای دستی هم **Run workflow** را بزنید.

> تا وقتی چهار Secret امضا تنظیم نشده، APK اکشن با کلید debug امضا می‌شود (نصب‌شدنی است، ولی کلید debug هر Runner متفاوت است و نصب روی نسخهٔ قبلی رد می‌شود). برای امضای ثابت و قابل آپدیت، طبق [راهنمای امضا](docs/SIGNING-GUIDE.md) یک keystore بسازید و چهار Secret را تنظیم کنید.

### روش ۲: Android Studio
۱. این پروژه را در Android Studio باز کنید
۲. روی **Run** بزنید (Shift+F10)
۳. APK در `app/build/outputs/apk/debug/` ساخته می‌شود

### روش ۳: خط فرمان
```bash
./gradlew assembleRelease
# APK در: app/build/outputs/apk/release/app-release.apk
```

## 📱 نصب APK

پس از دانلود APK از GitHub Actions:
- در مرورگر اندروید، روی APK بزنید
- اجازه "نصب از منابع ناشناس" بدهید
- برنامه را باز کنید

## 🔑 تنظیم اولیه

۱. برنامه را باز کنید
۲. **ثبت‌نام** یا **ورود** با ایمیل/گذرواژه، یا **ورود با کد ایمیل**، یا **گوگل**، یا **مهمان**
۳. به **تنظیمات** بروید و **Z.ai API Key** را وارد کنید (از https://chat.z.ai بخش API Keys)

### تنظیم ورود با گوگل (اختیاری)
۱. در [Google Cloud Console](https://console.cloud.google.com/) یک پروژه بسازید
۲. **Credentials → Create Credentials → OAuth client ID → Web application**
۳. Client ID را کپی کنید
۴. در تنظیمات برنامه در فیلد **Google OAuth Web Client ID** وارد کنید

بدون این کلید، دکمه گوگل فقط راهنما نشان می‌دهد ولی ورود با ایمیل/OTP کاملاً کار می‌کند.

## 📁 ساختار پروژه

```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/salizzai/app/
    │   ├── SaliZZaiApp.kt          # Application class
    │   ├── MainActivity.kt         # Single activity
    │   ├── data/
    │   │   ├── api/ZaiApiClient.kt # Direct Z.ai calls (chat, VLM, TTS, ASR, image, websearch)
    │   │   ├── db/                 # Room database + DAOs (User, EmailCode, Conversation, Message, Attachment)
    │   │   ├── model/              # Auth, Chat, ZIP, FileTree models
    │   │   ├── prefs/              # UserPreferences (DataStore)
    │   │   └── repository/         # AuthRepository
    │   ├── feature/
    │   │   ├── auth/               # Login + Signup + OTP + Google
    │   │   ├── chat/               # Chat screen + ViewModel (streaming, voice, TTS, multi-ZIP)
    │   │   ├── history/            # History list
    │   │   ├── settings/           # Settings (auth, API key, voice, AI)
    │   │   ├── imagegen/           # Image generation
    │   │   ├── discover/           # Web search
    │   │   └── ziptree/            # ZIP file tree viewer
    │   ├── ui/
    │   │   ├── theme/              # Color, Theme, Typography
    │   │   └── components/         # Markdown, ThinkingIndicator
    │   └── util/
    │       ├── PasswordHasher.kt   # PBKDF2 hashing
    │       ├── ZipExtractor.kt     # ZIP extraction + multi-ZIP comparison
    │       ├── VoiceInputController.kt  # ASR
    │       └── TtsController.kt    # TTS
    └── res/
        ├── drawable/               # Logo (Z with purple)
        ├── values/                 # Strings, colors, themes
        └── xml/                    # Backup + network config
├── .github/workflows/build-apk.yml    # GitHub Actions (auto-build)
├── build.gradle.kts                   # Project-level
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## 🆕 نسخه ۲.۲ — تطابق کامل با API زد‌ای‌آی، تفکر و بازطراحی رابط

### اصلاح خطاهای واقعی API

| مشکل | اصلاح |
|---|---|
| `thinking.type = "disabled"` برای GLM-5.3 ارسال می‌شد → پاسخ خطا | برای خانوادهٔ ۵.۳ همیشه `enabled` ارسال می‌شود و عمق با `reasoning_effort` کنترل می‌گردد |
| دمای مجاز تا ۲.۰ بود ولی Z.ai فقط `[0,1]` می‌پذیرد | لغزنده و مقدار ذخیره‌شده به بازهٔ ۰ تا ۱ محدود شد |
| جستجوی وب به `POST /functions/web_search` با بدنهٔ اشتباه می‌رفت | `POST /web_search` با `search_engine: search-prime`، `search_query` و `count`؛ پارس `search_result[]` |
| تولید تصویر بدون `model` (فیلد اجباری) ارسال می‌شد | `glm-image` / `cogview-4-250304` با `quality` و اندازه‌های معتبر |
| تبدیل گفتار به متن با مدل `whisper-1` | `glm-asr-2512` مطابق مستندات |
| GLM-5.3/5.2 به‌عنوان مدل «بینایی» علامت خورده بودند | ۵.۳ و ۵.۲ متنی‌اند؛ تصویر به‌صورت خودکار به `glm-5.3-flash` مسیر می‌گیرد |
| عکس‌های بزرگ‌تر از ۵ مگابایت رد می‌شدند | تغییر اندازهٔ خودکار تصویر پیش از ارسال |

### قابلیت‌های جدید

| قابلیت | توضیح |
|---|---|
| کارت روند تفکر | استدلال مدل در یک کارت بازشدنی با زمان‌سنج زنده نمایش و در دیتابیس ذخیره می‌شود |
| جستجوی وب در چت | دکمهٔ زمین در نوار ورودی؛ نتایج `search-prime` به‌عنوان زمینه به مدل داده و به‌صورت «منبع» زیر پاسخ ذخیره می‌شوند |
| انتخاب سریع مدل | شیت پایین‌کشیدنی از نوار چت با نشان‌های رایگان/تصویر و انتخاب عمق تفکر |
| پاسخ دوباره | تولید مجدد آخرین پاسخ بدون تکرار پیام کاربر |
| تاریخچهٔ کامل مدیریت‌شده | سنجاق‌کردن و حذف گفتگو از منوی هر ردیف |
| ذخیرهٔ تصویر | ذخیرهٔ تصویر تولیدشده در گالری (سازگار با Scoped Storage) |
| مهاجرت دیتابیس | ارتقای Room از v2 به v3 بدون پاک‌شدن گفتگوهای قبلی |

### عملکرد و سرعت

| بهبود | اثر |
|---|---|
| توقف رندر Markdown در هر توکن (throttle 85ms) | حذف لگ اسکرول در پاسخ‌های طولانی |
| نمایش انیمیشن برند فقط در اولین اجرا | حدود ۲ ثانیه شروع سریع‌تر در اجراهای بعدی |
| ساخت گفتگو فقط هنگام ارسال اولین پیام | بدون ردیف خالی در تاریخچه |
| `WhileSubscribed(5s)` برای فهرست تاریخچه | توقف کوئری‌های بی‌مصرف هنگام خروج از تب |
| کش `Markwon` بر اساس تم | جلوگیری از بازسازی رندرر برای هر پیام |
| بارگذاری تنبل `OkHttp`/`EventSource` | کاهش زمان راه‌اندازی اپ |

📘 راهنمای گام‌به‌گام آپدیت، پوش و انتشار: [docs/UPDATE-GUIDE.md](docs/UPDATE-GUIDE.md)

## 🆕 تغییرات نسخه ۲.۰ نسبت به ۱.۰

| بخش | ۱.۰ | ۲.۰ |
|---|---|---|
| احراز هویت | فقط API Key | ایمیل/گذرواژه، OTP، گوگل، مهمان |
| ZIP | تک ZIP | چند ZIP + مقایسه خودکار |
| VLM | ❌ | ✅ (GLM-4.5V) |
| ورودی صوتی | ❌ | ✅ SpeechRecognizer |
| پخش صوتی | ❌ | ✅ TextToSpeech |
| پنل Reasoning | ❌ | ✅ |
| رابط کاربری | پایه | روان، RTL، انیمیشن |
| مدل‌ها | ۵ | ۱۰ (GLM-5.3، 5.2، 5.3-Flash، 5.3-FlashX، 5V-Turbo، 4.7-Flash، 4.6، 4.5V، 4.5، 4.5-Flash) |
| ترجمه intent | ZIP | ZIP + تصویر + متن |
| حجم متن استخراج‌شده | ۲۰۰KB | ۲۵۶KB + پشتیبانی LaTeX |

### 🔥 بهبودهای v2.0 نهایی (Performance & UX)

| بهبود | توضیح |
|---|---|
| **Batching در Streaming** | buffer ۶۴ کاراکتر یا ۸۰ms flush → ~۸ برابر کاهش به‌روزرسانی UI/DB |
| **اسکرول خودکار هوشمند** | وقتی کاربر بالا اسکرول کرده، اسکرول خودکار به پایین قطع می‌شود (مشابه DeepSeek/ChatGPT) |
| **I/O روی Dispatchers.IO** | آپلود فایل و decode تصویر روی IO thread → جلوگیری از UI freeze |
| **produceState برای bitmap** | decode تصویر async + نمایش loading |
| **Debounce در Settings** | write در DataStore ۴۰۰ms بعد از توقف تایپ (سیستم پرامپت) |
| **Slider onValueChangeFinished** | فقط وقتی کاربر دست برداشت ذخیره می‌شود |
| **ConnectionPool OkHttp** | reuse اتصال‌ها → کاهش latency در درخواست‌های متوالی |
| **Cache HTTP 10MB** | کش خودکار پاسخ‌های تکراری |
| **R8 full mode + crunchPngs** | APK کوچک‌تر و startup سریع‌تر |
| **largeHeap + hardwareAccelerated** | حافظه بیشتر برای چت‌های بزرگ + رندر سخت‌افزاری |
| **contentType برای LazyColumn** | reuse بهتر کامپوزیشن‌ها در لیست طولانی پیام‌ها |
| **animateScrollToItem** | اسکرول روان بدون IllegalArgumentException |

## 📚 مستندات

| سند | محتوا |
| --- | --- |
| [اسکورکارت آمادگی محصول](docs/PRODUCT_READINESS_SCORECARD.md) | ارزیابی وضعیت فعلی + نقشه راه ۳۰/۶۰/۹۰ روزه |
| [ماتریس تست دستگاه](docs/DEVICE_TESTING_MATRIX.md) | برنامه تست روی دستگاه‌های واقعی |
| [سیاست حریم خصوصی](PRIVACY.md) · [امنیت](SECURITY.md) | مستندات قانونی و امنیتی برای انتشار در استور |
| [مشارکت](CONTRIBUTING.md) | قوانین تنظیم، تست و PR |

## 📜 لایسنس

MIT

</div>

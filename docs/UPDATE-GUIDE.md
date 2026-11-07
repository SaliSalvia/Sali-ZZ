# راهنمای آپدیت، پوش و انتشار نسخهٔ ۲.۱

<div dir="rtl">

این راهنما گام‌به‌گام نشان می‌دهد چگونه تغییرات نسخهٔ **۲.۱** را روی سیستم خود بسازید، تست کنید، به ریپوی گیت‌هاب پوش کنید و فایل APK را در بخش Releases آپلود کنید.

ریپو: `https://github.com/SaliSalvia/Sali-ZZ`

---

## ۰) خلاصهٔ تغییرات نسخهٔ ۲.۱

| # | تغییر | فایل‌های اصلی |
|---|-------|----------------|
| ۱ | **اعتبارسنجی کلید API پیش از ذخیره** — کلید با درخواست واقعی به Z.ai تست می‌شود و فقط در صورت تأیید ذخیره می‌گردد | `data/api/ZaiApiClient.kt` |
| ۲ | **دریافت خودکار فهرست مدل‌ها** هنگام تست کلید (`GET /models`) با فالبک تست تک‌توکنی | `data/api/ZaiApiClient.kt` |
| ۳ | **مدل‌های رایگان پیش‌فرض**: `glm-5.3` → `glm-5.2` → `glm-5.3-flash` با انتخاب خودکار بهترین مدلِ موجود در حساب | `data/model/Models.kt`، `data/prefs/UserPreferences.kt` |
| ۴ | **اصلاح Base URL** به `https://api.z.ai/api/paas/v4` + مهاجرت خودکار از مقدار قدیمی `/api/v1` | `data/api/ZaiApiClient.kt`، `app/build.gradle.kts` |
| ۵ | **بازطراحی رابط تنظیمات**: کارت وضعیت اتصال، نمایش ماسک کلید، انتخاب مدل با نشان «رایگان»، نوار پیشرفت و پیام‌های خطای دقیق | `feature/settings/SettingsScreen.kt` |
| ۶ | **چیپ مدل فعال در نوار بالای چت** (با یک ضربه به تنظیمات می‌رود) و متن‌های راهنمای به‌روز | `feature/chat/ChatScreen.kt` |
| ۷ | نسخه از `2.0.2` به `2.1.0` (versionCode 5) | `app/build.gradle.kts` |

---

## ۱) پیش‌نیازها

- **JDK 17** (Temurin یا OpenJDK)
- **Android SDK** با `compileSdk = 35` و `build-tools 35.x`
- **Git** و دسترسی به ریپو
- متغیر محیطی `ANDROID_HOME` (مثلاً `$HOME/Android/Sdk`)

بررسی سریع:

```bash
java -version          # باید 17 باشد
echo $ANDROID_HOME     # باید مسیر SDK را نشان دهد
git --version
```

---

## ۲) دریافت آخرین نسخهٔ کد

```bash
git clone https://github.com/SaliSalvia/Sali-ZZ.git
cd Sali-ZZ
git checkout master
git pull origin master
```

> اگر از قبل کلون دارید و تغییرات محلی دارید، اول آن‌ها را کامیت یا استش کنید تا `pull` تمیز انجام شود.

---

## ۳) ساخت شاخهٔ کاری

```bash
git checkout -b feature/api-key-validation-v2.1
```

---

## ۴) اعمال تغییرات (اگر خودتان کد را ویرایش می‌کنید)

فایل‌های زیر را ویرایش/جایگزین کنید:

1. `app/src/main/java/com/salizzai/app/data/model/Models.kt` — کاتالوگ مدل‌ها و `AvailableModels`
2. `app/src/main/java/com/salizzai/app/data/api/ZaiApiClient.kt` — `validateKey`، `fetchModelIds`، `normalizeBaseUrl`
3. `app/src/main/java/com/salizzai/app/data/prefs/UserPreferences.kt` — ذخیرهٔ فهرست مدل‌ها و زمان تأیید
4. `app/src/main/java/com/salizzai/app/feature/settings/SettingsScreen.kt` — رابط جدید تنظیمات
5. `app/src/main/java/com/salizzai/app/feature/chat/ChatScreen.kt` — چیپ مدل و متن‌های راهنما
6. `app/build.gradle.kts` — نسخه و `ZAI_BASE_URL`
7. `README.md` و همین فایل راهنما

### نکتهٔ امنیتی
کلید API **فقط** در `EncryptedSharedPreferences` روی دستگاه ذخیره می‌شود. هرگز کلید را در کد، فایل `.env` کامیت‌شده یا لاگ‌ها قرار ندهید.

---

## ۵) تست و ساخت APK

```bash
# تست‌های واحد
./gradlew --no-daemon test

# ساخت نسخهٔ دیباگ (قابل نصب فوری)
./gradlew --no-daemon assembleDebug

# ساخت نسخهٔ ریلیز (مینیفای/R8 هم بررسی می‌شود)
./gradlew --no-daemon assembleRelease
```

مسیر خروجی:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

نصب روی گوشی متصل:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### چک‌لیست تست دستی

- [ ] در تنظیمات، یک کلید **اشتباه** وارد کن → دکمهٔ «بررسی و ذخیره» باید خطای ۴۰۱ بدهد و کلید ذخیره **نشود**.
- [ ] کلید صحیح را وارد کن → پیام «کلید تأیید و ذخیره شد ✓»، تعداد مدل‌ها و زمان پاسخ نمایش داده شود.
- [ ] «دریافت خودکار فهرست مدل‌ها» را بزن → فهرست به‌روز شود.
- [ ] یکی از مدل‌های GLM-5 را انتخاب کن و در چت یک پیام بفرست → پاسخ استریم شود.
- [ ] بعد از بستن و باز کردن اپ، کلید و مدل انتخابی باقی مانده باشند.
- [ ] «تست اتصال» روی کلید ذخیره‌شده کار کند و «حذف» کلید را پاک کند.

---

## ۶) کامیت

```bash
git add app/src/main README.md docs/UPDATE-GUIDE.md app/build.gradle.kts
git commit -m "feat: validate Z.ai API key before saving and auto-discover models

- verify key with GET /models, fall back to a 1-token chat probe
- default to free glm-5.3 / glm-5.2 / glm-5.3-flash with best-match selection
- fix base URL to api.z.ai/api/paas/v4 and migrate the old /api/v1 value
- redesign settings with connection status, masked key and model picker"
```

---

## ۷) پوش به ریپو

```bash
git push -u origin feature/api-key-validation-v2.1
```

سپس در گیت‌هاب یک **Pull Request** به `master` باز کنید و پس از سبز شدن Actions، Merge کنید.

اگر مستقیم روی `master` کار می‌کنید:

```bash
git checkout master
git merge --ff-only feature/api-key-validation-v2.1
git push origin master
```

---

## ۸) ساخت و آپلود خودکار (GitHub Actions)

فایل `.github/workflows/build-apk.yml` با هر push روی `master` به‌صورت خودکار:

1. تست‌ها را اجرا می‌کند (`./gradlew test`)
2. APK دیباگ و ریلیز را می‌سازد
3. آن‌ها را به‌عنوان **Artifact** در صفحهٔ Actions آپلود می‌کند

مسیر دانلود: **GitHub → Actions → آخرین Workflow → بخش Artifacts → `SaliBussiness-APK-<sha>`**

### انتشار رسمی در Releases (با تگ)

```bash
git tag -a v2.1.0 -m "Sali-ZZai v2.1.0 — API key validation & free GLM-5 models"
git push origin v2.1.0
```

با push شدن تگ، workflow به‌صورت خودکار یک Release با نام `SaliBussiness v2.1.0` می‌سازد و فایل‌های زیر را ضمیمه می‌کند:

```
SaliBussiness-debug.apk
SaliBussiness-release.apk
SHA256SUMS.txt
```

### آپلود دستی APK به Releases

1. **GitHub → Releases → Draft a new release**
2. Tag: `v2.1.0` (یا تگ جدید) و عنوان دلخواه
3. فایل‌های `app-debug.apk` و `app-release.apk` را با drag & drop اضافه کنید
4. توضیحات تغییرات را از بخش «خلاصهٔ تغییرات» همین فایل کپی کنید
5. **Publish release**

برای امضای نسخهٔ ریلیز با کلید واقعی، این Secretها را در **Settings → Secrets and variables → Actions** تنظیم کنید:

| Secret | مقدار |
|--------|-------|
| `SALI_KEYSTORE_BASE64` | خروجی `base64 -w0 sali.keystore` |
| `SALI_KEYSTORE_PASSWORD` | رمز keystore |
| `SALI_KEY_ALIAS` | نام alias |
| `SALI_KEY_PASSWORD` | رمز کلید |

بدون این‌ها، نسخهٔ ریلیز با کلید دیباگ امضا می‌شود (نصب‌شدنی است، اما برای انتشار عمومی مناسب نیست).

---

## ۹) اگر می‌خواهید فقط همین آپدیت را روی فورک خودتان بیاورید

```bash
git remote add upstream https://github.com/SaliSalvia/Sali-ZZ.git 2>/dev/null || true
git fetch upstream
git checkout -b update-2.1 upstream/master
git push origin update-2.1
```

سپس PR را روی شاخهٔ خودتان باز کنید.

---

## ۱۰) عیب‌یابی

| علامت | علت احتمالی | راه حل |
|-------|--------------|--------|
| `HTTP 401/403` هنگام بررسی کلید | کلید اشتباه، حذف‌شده یا از نوع Coding Plan | کلید **Standard API Key** تازه از [chat.z.ai](https://chat.z.ai) → API Keys بسازید |
| `HTTP 404` | Base URL اشتباه | مقدار را به `https://api.z.ai/api/paas/v4` برگردانید (دکمهٔ «پیش‌فرض») |
| «فهرست مدل‌ها از API خوانده نشد» | حساب شما endpoint `/models` را ارائه نمی‌دهد | طبیعی است؛ اپ از فهرست داخلی استفاده می‌کند و مدل‌ها را تک‌توکنی تست می‌کند |
| `model not found` در چت | مدل انتخابی در حساب شما نیست | از بخش «مدل هوش مصنوعی» مدل دیگری انتخاب یا فهرست را به‌روزرسانی کنید |
| خطای Gradle: `SDK location not found` | `ANDROID_HOME` تنظیم نیست | `local.properties` بسازید: `sdk.dir=/path/to/Android/Sdk` |
| Actions قرمز شد | تست یا کامپایل خطا دارد | لاگ مرحلهٔ `Test and build APK` و Artifact `build-diagnostics` را ببینید |

---

## ۱۱) بازگشت به عقب (در صورت نیاز)

```bash
git revert <commit-sha>
git push origin master
```

برای برگرداندن فقط نسخه: `versionCode` و `versionName` را در `app/build.gradle.kts` به مقدار قبلی برگردانید.

</div>

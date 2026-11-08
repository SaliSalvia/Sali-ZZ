# راهنمای ساخت keystore و امضای نسخهٔ ریلیز

<div dir="rtl">

این راهنما نشان می‌دهد چگونه یک keystore واقعی بسازید، چهار Secret لازم را در GitHub تنظیم کنید و مطمئن شوید APK منتشرشده در Releases با همان کلید امضا می‌شود.

> **نکتهٔ مهم:** Workflow فعلی Release را هرگز با کلید عمومی Debug امضا نمی‌کند؛ بدون keystore اختصاصی، فقط APK آزمایشی Debug به‌عنوان Artifact ساخته می‌شود. اگر نسخه‌ای که قبلاً نصب کرده‌اید با کلید دیگری امضا شده باشد، Android ممکن است اجازهٔ نصب آپدیت روی آن را ندهد و حذف نسخهٔ قدیمی لازم شود. از نخستین نسخه‌ای که با keystore اختصاصی خودتان منتشر می‌کنید، همان کلید را برای تمام به‌روزرسانی‌های بعدی نگه دارید.

---

## ۱) پیش‌نیاز

`keytool` داخل JDK است. اگر JDK 17 دارید، آماده‌اید:

```bash
keytool -help
```

Android Studio هم JDK خودش را دارد (مسیر `jbr/bin` داخل پوشهٔ نصب).

---

## ۲) ساخت keystore

پوشه‌ای بیرون از ریپو انتخاب کنید (مثلاً `~/keys`). این فایل و رمزهایش سرمایهٔ شماست؛ اگر گم شود، دیگر نمی‌توانید نسخهٔ جدید همان اپ را منتشر کنید.

### لینوکس / مک

```bash
mkdir -p ~/keys && cd ~/keys
keytool -genkeypair -v \
  -keystore sali-zzai-release.keystore \
  -alias salizzai \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12
```

### ویندوز (PowerShell یا CMD)

```bat
mkdir %USERPROFILE%\keys
cd %USERPROFILE%\keys
keytool -genkeypair -v -keystore sali-zzai-release.keystore -alias salizzai -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
```

در پاسخ به پرسش‌ها:

| پرسش | پیشنهاد |
|------|----------|
| Enter keystore password | یک رمز قوی (حداقل ۱۲ کاراکتر) — همان می‌شود `SALI_KEYSTORE_PASSWORD` |
| Re-enter password | تکرار همان |
| What is your first and last name? | نام شما یا نام برند (مثلاً `Sali Salvia`) |
| Organizational unit / Organization | اختیاری، مثلاً `Sali-ZZai` |
| City / State / Country code | مثلاً `Tehran` / `Tehran` / `IR` |
| Is CN=... correct? | `yes` |
| Enter key password for `<salizzai>` | **Enter** را بزنید تا همان رمز keystore استفاده شود (ساده‌تر و کم‌خطاتر) |

اگر رمز کلید را جدا گذاشتید، آن را هم یادداشت کنید.

بررسی صحت فایل:

```bash
keytool -list -v -keystore sali-zzai-release.keystore -alias salizzai
```

باید `Alias name: salizzai`، `Key Algorithm: RSA` و `SHA256:` را ببینید.

---

## ۳) تبدیل keystore به Base64 (تک‌خطی)

CI فایل را با `base64 --decode` باز می‌کند، پس خروجی باید **یک خط بدون شکست خط** باشد. اگر بین خطوط `\n` بیفتد، ممکن است Secret درست ذخیره نشود.

### لینوکس

```bash
base64 -w0 sali-zzai-release.keystore > keystore.b64 && wc -c keystore.b64
```

### مک (BSD base64 خط‌شکنی دارد، از openssl استفاده کنید)

```bash
openssl base64 -A -in sali-zzai-release.keystore -out keystore.b64 && wc -c keystore.b64
```

### ویندوز (PowerShell)

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\keys\sali-zzai-release.keystore")) | Set-Content -NoNewline keystore.b64
```

محتوای `keystore.b64` را کامل کپی کنید. اگر می‌خواهید مطمئن شوید سالم است، همان را برعکس کنید:

```bash
base64 -d keystore.b64 > check.keystore && cmp sali-zzai-release.keystore check.keystore && echo OK
```

---

## ۴) تنظیم چهار Secret در GitHub

**Settings → Secrets and variables → Actions → New repository secret**

| نام Secret (دقیقاً همین) | مقدار |
|---|---|
| `SALI_KEYSTORE_BASE64` | محتوای کامل `keystore.b64` (یک خط) |
| `SALI_KEYSTORE_PASSWORD` | رمز keystore |
| `SALI_KEY_ALIAS` | `salizzai` (alias شما) |
| `SALI_KEY_PASSWORD` | رمز کلید (اگر همان رمز keystore است، همان را بگذارید) |

> ⚠️ در کد workflow متغیرهای محیطی `SIGNING_KEYSTORE_PASSWORD`، `SIGNING_KEY_ALIAS` و `SIGNING_KEY_PASSWORD` وجود دارند، ولی **نام Secret**ها همان چهار مورد جدول بالا است. اگر Secret ناقص باشد، نسخهٔ انتشار ساخته/منتشر نمی‌شود و فقط APK دیباگ به‌عنوان Artifact CI ارائه می‌شود.

هیچ‌کدام از این‌ها را در فایل‌های ریپو، کد، Issue یا چت نگذارید. `.gitignore` هم برای اطمینان، `*.keystore` و `*.jks` را نادیده می‌گیرد.

---

## ۵) ساخت نسخهٔ امضاشده

با تنظیم بودن هر چهار Secret، هر push به `master` (یا هر تگ `v*`) نسخهٔ ریلیز را با کلید شما امضا می‌کند.

1. `versionCode` را در `app/build.gradle.kts` یک واحد بالا ببرید و `versionName` را تغییر دهید (مثلاً `2.2.1`).
2. کامیت و push:

```bash
git add app/build.gradle.kts
git commit -m "Bump version to 2.2.1"
git push origin master
```

3. در **Actions**، مرحلهٔ `Prepare release signing` باید `available=true` اعلام کند و کلید را با `keytool` تأیید کند. سپس مرحلهٔ `Build signed release APK` اجرا می‌شود. اگر Secret ناقص باشد، مرحلهٔ Release عمداً skip می‌شود؛ APK دیباگ همچنان به‌عنوان Artifact CI ساخته خواهد شد.

4. Release به‌طور خودکار با تگ `v<versionName>` ساخته می‌شود (`ساخت خودکار تگ از versionName`).

---

## ۶) بررسی امضای APK

APK را از Releases دانلود کنید و با `apksigner` (داخل `build-tools` در Android SDK) بررسی کنید:

```bash
apksigner verify --print-certs Sali-ZZai-release.apk
```

- **درست:** `Signer #1 certificate DN: CN=Sali Salvia, ...` (اطلاعاتی که در keytool وارد کردید)
- **اشتباه:** `CN=Android Debug` یعنی هنوز با کلید دیباگ امضا می‌شود.

می‌توانید `SHA256SUMS.txt` منتشرشده را هم چک کنید:

```bash
sha256sum -c SHA256SUMS.txt
```

---

## ۷) ساخت امضاشده به‌صورت محلی (اختیاری)

برای اینکه روی سیستم خودتان هم APK ریلیزِ امضاشده بسازید، همان چهار مقدار را به‌عنوان متغیر محیطی بدهید (نام‌ها اینجا `SIGNING_*` هستند، چون `app/build.gradle.kts` همین‌ها را می‌خواند):

### لینوکس / مک

```bash
export SIGNING_KEYSTORE_FILE="$HOME/keys/sali-zzai-release.keystore"
export SIGNING_KEYSTORE_PASSWORD="رمز-keystore"
export SIGNING_KEY_ALIAS="salizzai"
export SIGNING_KEY_PASSWORD="رمز-کلید"
./gradlew :app:assembleRelease
```

### ویندوز (PowerShell)

```powershell
$env:SIGNING_KEYSTORE_FILE="$env:USERPROFILE\keys\sali-zzai-release.keystore"
$env:SIGNING_KEYSTORE_PASSWORD="رمز-keystore"
$env:SIGNING_KEY_ALIAS="salizzai"
$env:SIGNING_KEY_PASSWORD="رمز-کلید"
.\gradlew.bat :app:assembleRelease
```

گزارش Gradle باید `✅ Release signing config loaded from ...` را نشان دهد. اگر نداد، مسیر فایل یا نام متغیرها را چک کنید.

---

## ۸) پشتیبان‌گیری (مهم‌ترین بخش)

- فایل `.keystore` و رمزهایش را در دو جای مطمئن نگه دارید (مثلاً مدیر رمز + یک فلش رمزگذاری‌شده).
- این فایل را **هرگز** داخل ریپو، پیام، Issue یا چت نگذارید.
- اگر keystore را گم کنید: نمی‌توانید نسخهٔ جدید همان اپ را با همان امضا منتشر کنید و کاربران باید یک‌بار اپ را حذف و دوباره نصب کنند.
- اگر نسخه را در Google Play می‌گذارید، از **Play App Signing** استفاده کنید؛ آن‌وقت این keystore نقش «upload key» را دارد و در صورت گم‌شدن، قابل بازنشانی است.

---

## ۹) عیب‌یابی

| خطا / نشانه | علت | راه‌حل |
|---|---|---|
| Release APK skipped | یک یا چند Secret خالی یا keystore نامعتبر است | نام‌ها را با جدول بخش ۴ تطبیق دهید و با `keytool -list` آن را تأیید کنید |
| `Keystore was tampered with, or password was incorrect` | `SALI_KEYSTORE_PASSWORD` غلط است یا Base64 ناقص کپی شده | رمز را چک و `keystore.b64` را کامل و تک‌خطی دوباره بسازید |
| `Failed to read key <alias> from store: No key with alias` | `SALI_KEY_ALIAS` غلط است | با `keytool -list -keystore ...` alias واقعی را ببینید |
| `Given final block not properly padded` | Base64 خراب/بریده یا `base64` اشتباهِ سیستم (مک) | روش `openssl base64 -A` را استفاده کنید |
| `App not installed` هنگام آپدیت روی گوشی | امضای APK جدید با نسخهٔ نصب‌شده فرق دارد | اپ قبلی را حذف کنید؛ از این نسخه به بعد امضا ثابت می‌ماند |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` در adb | همان مشکل بالا | `adb uninstall com.salizzai.app` سپس نصب مجدد |
| Release با وجود push ساخته نشد | تگ آن نسخه از قبل وجود دارد (گام ساخت Release فقط یک‌بار برای هر نسخه اجرا می‌شود) | `versionName` را بالا ببرید |

---

## ۱۰) چک‌لیست کوتاه

- [ ] keystore ساخته شد و `keytool -list` آن را تأیید می‌کند
- [ ] `keystore.b64` تک‌خطی ساخته شد
- [ ] چهار Secret با نام‌های دقیق تنظیم شد
- [ ] `versionCode`/`versionName` بالا رفت و push شد
- [ ] لاگ CI خط `Release will be signed with the repository keystore.` را نشان می‌دهد
- [ ] `apksigner verify --print-certs` نام شما را نشان می‌دهد، نه `Android Debug`
- [ ] نسخهٔ پشتیبان keystore و رمزها جای امن ذخیره شد

</div>

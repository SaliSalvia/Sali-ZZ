# راهنمای Push نسخهٔ سخت‌سازی‌شدهٔ Sali-ZZ

این راهنما برای فایل `Sali-ZZ-production-hardened.zip` و یک ریپوی خالی GitHub نوشته شده است. آرشیو شامل پوشهٔ ریشهٔ `Sali-ZZ/` و فایل Workflow داخل `.github/workflows/` است.

## ۱) آماده‌سازی Termux

```bash
pkg update -y
pkg install git gh unzip -y
termux-setup-storage
```

ورود به GitHub (یک‌بار کافی است):

```bash
gh auth login
# GitHub.com → HTTPS → Login with a web browser
```

## ۲) استخراج سورس

فایل ZIP را در پوشهٔ Downloads قرار دهید، سپس:

```bash
cd ~/storage/downloads
unzip -o Sali-ZZ-production-hardened.zip
cd Sali-ZZ
chmod +x gradlew
```

در این مرحله باید این مسیرها وجود داشته باشند:

```bash
test -f app/build.gradle.kts && echo 'app module: OK'
test -f gradle/wrapper/gradle-wrapper.jar && echo 'Gradle wrapper: OK'
test -f .github/workflows/build-apk.yml && echo 'GitHub Actions workflow: OK'
test -x gradlew && echo 'gradlew executable: OK'
```

## ۳) Push به ریپوی خالی

ابتدا یک ریپوی خالی با نام دلخواه در GitHub بسازید؛ README، License یا Gitignore را هنگام ساخت اضافه نکنید. سپس `USERNAME` را با نام کاربری خودتان و نام ریپو را با نام واقعی جایگزین کنید:

```bash
git init
git branch -M main
git config user.name "Your Name"
git config user.email "you@example.com"
git add -A
git commit -m "Sali-ZZai 2.2.1: security and reliability hardening"
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

یا اگر می‌خواهید GitHub CLI ریپو را هم بسازد:

```bash
gh repo create Sali-ZZ --public --source=. --remote=origin --push
```

فقط یکی از دو روش Push بالا را اجرا کنید.

## ۴) دریافت APK

در GitHub به **Actions → Build Sali-ZZai APK** بروید و آخرین اجرا را باز کنید. پس از موفقیت Workflow، فایل `Sali-ZZai-debug.apk` در Artifact با نام `Sali-ZZai-APK-<commit>` قرار می‌گیرد. این APK برای نصب و تست است و با کلید debug امضا می‌شود.

## ۵) امضای Release واقعی (اختیاری)

برای ساخت APK انتشار با امضای پایدار، چهار Secret زیر را از مسیر **Settings → Secrets and variables → Actions** اضافه کنید:

- `SALI_KEYSTORE_BASE64`
- `SALI_KEYSTORE_PASSWORD`
- `SALI_KEY_ALIAS`
- `SALI_KEY_PASSWORD`

فایل keystore و رمزهایش را داخل ریپو یا ZIP قرار ندهید. دستورهای ساخت keystore و تبدیل آن به Base64 در [راهنمای امضا](docs/SIGNING-GUIDE.md) آمده است. بدون هر چهار Secret معتبر، Workflow فقط APK دیباگ را می‌سازد و انتشار Release را رد می‌کند.

## نکات مهم

- نسخهٔ پروژه `2.2.1` و `versionCode` آن `8` است.
- Build کامل Android و تست روی دستگاه باید پس از Push در GitHub Actions انجام و تأیید شود؛ ZIP به‌تنهایی به معنی APK ساخته‌شده نیست.
- برای نسخه‌های جدیدی که با keystore اختصاصی ساخته می‌شوند، همان فایل و رمزها را در امنیت کامل نگه دارید؛ از دست رفتن کلید، امکان امضای آپدیت سازگار را از بین می‌برد.
- اگر Workflow شکست خورد، از تب **Actions** وارد همان اجرا شوید و لاگ اولین مرحلهٔ قرمز را بررسی کنید.

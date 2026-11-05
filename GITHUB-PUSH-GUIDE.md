# 🚀 راهنمای آماده‌سازی و Push به گیت‌هاب

این فایل راهنمای گام به گام برای پوش کردن پروژه به گیت‌هاب و ساخت APK در GitHub Actions است.

## 📋 پیش‌نیازها

1. **حساب گیت‌هاب** — اگر ندارید در https://github.com/signup ثبت‌نام کنید
2. **Git نصب روی گوشی/کامپیوتر** — یا استفاده از GitHub CLI
3. **فایل ZIP استخراج شده در پوشه دانلود گوشی**

---

## 🔧 روش ۱: استفاده از GitHub CLI (ساده‌ترین روش)

### گام ۱: نصب GitHub CLI
- **اندروید**: اپلیکیشن Termux را از F-Droid نصب کنید
  ```bash
  pkg install gh git
  ```
- **ویندوز/Mac/Linux**: از https://cli.github.com دانلود کنید

### گام ۲: ورود به گیت‌هاب
```bash
gh auth login
# انتخاب: GitHub.com → HTTPS → Login with a web browser
# کد نمایش داده شده را در مرورگر وارد کنید
```

### گام ۳: ساخت ریپو جدید و پوش پروژه
```bash
# به پوشه دانلود بروید
cd /storage/emulated/0/Download
# یا هر جایی که ZIP را اکسترکت کرده‌اید

# اکسترکت ZIP (اگر هنوز نکرده‌اید)
unzip Sali-ZZai-v2.0-Final.zip -d Sali-ZZai
cd Sali-ZZai

# ساخت ریپو جدید در گیت‌هاب و پوش اولیه
gh repo create Sali-ZZ --public --source=. --remote=origin --push
```

### گام ۴: انتظار برای ساخت APK
- به آدرس `https://github.com/USERNAME/Sali-ZZ/actions` بروید
- workflow به‌طور خودکار اجرا می‌شود
- پس از ~۸-۱۲ دقیقه، APK آماده است
- به تب **Artifacts** بروید و `sali-zzai-apk` را دانلود کنید

---

## 🔧 روش ۲: استفاده از رابط وب گیت‌هاب

### گام ۱: ساخت ریپو جدید
1. به https://github.com/new بروید
2. **Repository name**: `Sali-ZZ`
3. **Public** انتخاب کنید
4. **不要** add README/gitignore/license اضافه نکنید
5. روی **Create repository** کلیک کنید

### گام ۲: آپلود فایل‌ها از طریق رابط وب
1. در صفحه ریپو، روی **uploading an existing file** کلیک کنید
2. **همه فایل‌های استخراج شده** از ZIP را drag & drop کنید
3. شامل پوشه `.github` و فایل `.gitignore` هم بشوید
4. یک commit message بنویسید: `Initial commit - Sali-ZZai v2.0`
5. روی **Commit changes** کلیک کنید

### گام ۳: انتظار برای ساخت APK
- به تب **Actions** بروید
- workflow به‌طور خودکار اجرا می‌شود

---

## 🔧 روش ۳: استفاده از Git کامندی (Termux)

### گام ۱: نصب Git در Termux
```bash
pkg install git
git config --global user.name "نام شما"
git config --global user.email "email@example.com"
```

### گام ۲: آماده‌سازی پروژه
```bash
cd /storage/emulated/0/Download/Sali-ZZai
git init
git add -A
git commit -m "Sali-ZZai v2.0 - Native Android Z.ai Client"

# ساخت branch main
git branch -M main
```

### گام ۳: ساخت Personal Access Token
1. به https://github.com/settings/tokens بروید
2. **Generate new token (classic)** → **Generate token**
3. دسترسی **repo** را انتخاب کنید
4. توکن را کپی کنید (فقط یک بار نمایش داده می‌شود!)

### گام ۴: پوش به گیت‌هاب
```bash
# اضافه کردن remote (به جای USERNAME نام کاربری خودتان)
git remote add origin https://github.com/USERNAME/Sali-ZZ.git

# پوش اولیه
git push -u origin main
# وقتی username خواست: نام کاربری گیت‌هاب
# وقتی password خواست: token (نه پسورد گیت‌هاب!)
```

---

## 🔄 روش ۴: به‌روزرسانی ریپو موجود (پاک و پوش دوباره)

اگر از قبل ریپو به نام `Sali-ZZ` دارید و می‌خواهید فایل‌های جدید را جایگزین کنید:

### گام ۱: کلون ریپو موجود
```bash
cd ~
gh repo clone USERNAME/Sali-ZZ
cd Sali-ZZ
```

### گام ۲: حذف همه فایل‌های قدیمی (به جز .git)
```bash
# حذف همه فایل‌ها به جز پوشه .git
find . -mindepth 1 -maxdepth 1 -not -name '.git' -exec rm -rf {} +
```

### گام ۳: کپی فایل‌های جدید از پوشه دانلود
```bash
# از پوشه دانلود گوشی به پوشه پروژه
cp -r /storage/emulated/0/Download/Sali-ZZai/* .
cp -r /storage/emulated/0/Download/Sali-ZZai/.github .
cp /storage/emulated/0/Download/Sali-ZZai/.gitignore .
```

### گام ۴: Commit و Push
```bash
git add -A
git commit -m "Upgrade to v2.0 - Performance & UX improvements"
git push origin main
```

---

## 📱 دانلود APK پس از build

1. به صفحه ریپو بروید: `https://github.com/USERNAME/Sali-ZZ`
2. روی تب **Actions** کلیک کنید
3. روی آخرین run موفق کلیک کنید
4. در پایین صفحه، بخش **Artifacts** را پیدا کنید
5. روی **sali-zzai-apk** کلیک کنید تا دانلود شود
6. فایل `sali-zzai-apk.zip` را اکسترکت کنید → `Sali-ZZai.apk` داخلش است
7. APK را روی گوشی نصب کنید

---

## ❓ اشکال‌یابی

### مشکل: workflow اجرا نمی‌شود
- مطمئن شوید پوشه `.github/workflows/` پوش شده است
- در گیت‌هاب: `Settings → Actions → General → Allow all actions`
- ریپو باید Public باشد (یا workflow مجاز برای Private باشد)

### مشکل: build شکست می‌خورد
- به تب Actions بروید، روی run شکست‌خورده کلیک کنید
- logs را بخوانید
- معمولاً مشکل از Java version یا Android SDK است
- اگر `gradle-wrapper.jar` پوش نشده باشد، workflow خودکار آن را تولید می‌کند

### مشکل: APK نصب نمی‌شود
- **Android Settings → Security → Unknown sources** را فعال کنید
- مطمئن شوید APK امضاشده است (با debug key در workflow)

### مشکل: app کرش می‌کند
- مطمئن شوید API Key صحیح Z.ai را در تنظیمات وارد کرده‌اید
- به https://chat.z.ai بروید، وارد شوید، Settings → API Keys
- نسخه Android 8.0+ (API 26+) لازم است

---

## ✅ چک‌لیست نهایی قبل از پوش

- [ ] فایل ZIP اکسترکت شده
- [ ] پوشه `.github` شامل `workflows/build-apk.yml` است
- [ ] فایل `gradle/wrapper/gradle-wrapper.jar` موجود است (43KB)
- [ ] فایل `gradlew` اجرایی است (chmod +x)
- [ ] فایل `app/build.gradle.kts` موجود است
- [ ] فایل `.gitignore` موجود است
- [ ] git user.name و user.email تنظیم شده‌اند

---

## 🎯 خلاصه دستورات سریع

```bash
# در پوشه پروژه:
cd /storage/emulated/0/Download/Sali-ZZai
git init
git add -A
git commit -m "Sali-ZZai v2.0"
git branch -M main
gh repo create Sali-ZZ --public --source=. --remote=origin --push
# یا اگر ریپو از قبل وجود دارد:
# git remote add origin https://github.com/USERNAME/Sali-ZZ.git
# git push -u origin main
```

سپس به `https://github.com/USERNAME/Sali-ZZ/actions` بروید و منتظر build باشید. APK را از Artifacts دانلود کنید.

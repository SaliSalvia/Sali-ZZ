# راهنمای توسعه، آپدیت و انتشار Sali-ZZai 2.2.1

<div dir="rtl">

این سند وضعیت فعلی پروژه را توصیف می‌کند. برای راه‌اندازی ریپوی خالی و Push از Termux، راهنمای اصلی [GITHUB-PUSH-GUIDE.md](../GITHUB-PUSH-GUIDE.md) را دنبال کنید.

## وضعیت نسخه

| مورد | مقدار |
|---|---|
| `versionName` | `2.2.1` |
| `versionCode` | `8` |
| `minSdk` | `26` |
| `compileSdk` / `targetSdk` | `35` |
| JDK | `17` |
| Gradle Wrapper | `8.9` |
| Android Gradle Plugin | `8.7.2` |
| Kotlin | `2.1.20` |
| KSP | `2.1.20-1.0.32` (KSP2) |
| Room | `2.7.2` |

نسخهٔ AGP به 8.7.2 ارتقا یافته تا `compileSdk 35` در محدودهٔ پشتیبانی رسمی باشد. AGP 8.5 حداکثر API 34 را پشتیبانی می‌کرد؛ API 35 حداقل به AGP 8.6 نیاز دارد.

**محدودیت انتشار در Google Play:** این نسخه `targetSdk 35` دارد و برای APK آزمایشی/توزیع مستقیم مناسب است؛ اما از ۳۱ اوت ۲۰۲۶، ارسال اپ جدید یا به‌روزرسانی به Google Play به `targetSdk 36` یا بالاتر نیاز دارد. پیش از انتشار در Play باید مهاجرت Android 16 با AGP/Kotlin سازگار و آزمون روی دستگاه انجام شود.

## تغییرات سخت‌سازی نسخهٔ 2.2.1

- **ZIP:** سقف ۲۵ MiB برای فایل فشرده، ۲۰۰۰ ورودی، ۱۰ MiB برای هر ورودی و ۵۰ MiB برای کل دادهٔ بازشده؛ رد مسیرهای مطلق، traversal، نام‌های تکراری/مبهم و برخورد فایل با مسیر فرزند.
- **ضمیمه‌ها:** خواندن جریانی محدود، حداکثر ۸ فایل در هر انتخاب، ۲۵ MiB برای هر فایل و ۵۰ MiB مجموع.
- **API:** فقط HTTPS، رد URLهای دارای userinfo/query/fragment و خاموش‌کردن redirect برای جلوگیری از انتقال ناخواستهٔ Bearer token.
- **اعتبارنامه‌ها:** عدم fallback به SharedPreferences متنی؛ اگر Android Keystore خراب/دسترس‌ناپذیر باشد، کلید فقط در حافظهٔ فرایند می‌ماند و پس از Restart باید دوباره وارد شود.
- **گذرواژه:** PBKDF2-HMAC-SHA256 با ۶۰۰٬۰۰۰ تکرار، salt تصادفی ۳۲ بایتی، خروجی ۲۵۶ بیتی و bounds هنگام verify.
- **احراز هویت:** رابط کاربری روشن می‌گوید OTP صرفاً کد محلی آزمایشی است و ایمیلی ارسال نمی‌شود. ادعاهای Google در نبود backend اعتبارسنجی سروری نمی‌شوند؛ این دو جریان نباید به‌عنوان احراز هویت production استفاده شوند.
- **Release:** دیگر هیچ fallback به کلید عمومی Debug وجود ندارد. بدون چهار Secret معتبر، تنها APK آزمایشی Debug ساخته می‌شود.
- **CI:** Actionها به نسخه‌های Node 24 ارتقا یافته‌اند؛ Workflow تست واحد، lint غیرمسدودکننده، APK Debug و در صورت وجود keystore معتبر APK Release را اجرا می‌کند.

## پیش‌نیاز Build محلی

- JDK 17
- Android SDK Platform 35 و Build Tools سازگار
- دسترسی اینترنت برای دانلود Gradle و وابستگی‌های Maven

```bash
java -version
./gradlew --version
./gradlew --no-daemon test
./gradlew --no-daemon lintDebug
./gradlew --no-daemon assembleDebug
```

خروجی Debug در `app/build/outputs/apk/debug/app-debug.apk` قرار می‌گیرد. Release را فقط با keystore اختصاصی بسازید؛ راهنمای دقیق آن در [SIGNING-GUIDE.md](SIGNING-GUIDE.md) است.

## GitHub Actions

پس از Push به `main` یا `master`، به **Actions → Build Sali-ZZai APK** بروید. Artifact عمومی CI شامل `Sali-ZZai-debug.apk` و `SHA256SUMS.txt` است. در صورت پیکربندی تمام Secretهای امضا، Release امضاشده نیز ساخته می‌شود و در شرایط مشخص به GitHub Releases می‌رود.

Secretهای لازم:

- `SALI_KEYSTORE_BASE64`
- `SALI_KEYSTORE_PASSWORD`
- `SALI_KEY_ALIAS`
- `SALI_KEY_PASSWORD`

فایل keystore را هرگز در ZIP پروژه یا Git commit قرار ندهید.

## قبل از انتشار نسخهٔ بعدی

۱. `versionCode` را حداقل یک واحد افزایش دهید؛ برای نصب به‌روزرسانی باید از نسخهٔ نصب‌شده بالاتر باشد.
۲. `versionName` را تغییر دهید.
۳. تست‌ها، `lintDebug` و `assembleDebug` را اجرا کنید.
۴. APK امضاشده را روی چند دستگاه واقعی نصب و تست کنید.
۵. سیاست حریم خصوصی، صفحهٔ معرفی فروشگاه، اسکرین‌شات‌ها و Data Safety را بازبینی کنید.

</div>

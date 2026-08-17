<div align="center">
  <img src="docs/images/manga-peak-logo.webp" alt="Anilord logo" width="140" />

  # Anilord | مانجا بيك

  قارئ ومشغل مفتوح المصدر للمانجا والروايات والأنمي على Android.

  ![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
  ![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
  ![License](https://img.shields.io/badge/License-GPL--3.0-blue)
</div>

## عن التطبيق

مانجا بيك تطبيق Android يجمع قراءة المانجا والروايات ومشاهدة الأنمي في واجهة واحدة. يدعم المصادر المتعددة، البحث، المفضلة، سجل القراءة والمشاهدة، التنزيل للاستخدام دون اتصال، ووضع القراءة القابل للتخصيص.

المشروع مبني على [Kotatsu](https://github.com/KotatsuApp/Kotatsu) ويضم نسخة المصدر المستخدمة من [kotatsu-parsers](https://github.com/KotatsuApp/kotatsu-parsers) داخل المستودع لتسهيل البناء والمراجعة.

## المميزات

- تصفح وبحث في مصادر المانجا والروايات والأنمي.
- قارئ مانجا وروايات قابل للتخصيص مع دعم Webtoon.
- مشغل أنمي داخلي يدعم السيرفرات المتعددة وHLS وMP4 وMKV.
- تنزيل الفصول والحلقات والقراءة أو المشاهدة دون اتصال.
- مفضلة، تصنيفات، سجل، علامات مرجعية وإشعارات التحديثات.
- واجهة Material You مناسبة للهواتف والأجهزة اللوحية.
- إعلانات أصلية منظمة وإعلان اختياري قبل مشاهدة الأنمي.
- مزامنة ونسخ احتياطي وتكاملات تتبع اختيارية.
- دعم Android 6.0 وما بعده.

## بنية المشروع

| المسار | المحتوى |
|---|---|
| `app/` | تطبيق Android والاختبارات والموارد |
| `kotatsu-parsers/` | كود مصادر المانجا والروايات والأنمي المستخدم في التطبيق |
| `gradle/` | Gradle Wrapper وكتالوج الإصدارات |
| `docs/images/` | لوجو Anilord المستخدم في هذا الملف |

## متطلبات البناء

- Android Studio حديث.
- JDK 17.
- Android SDK 36 وBuild Tools 35.0.0.
- اتصال بالإنترنت في أول بناء لتنزيل الاعتمادات.

## تشغيل نسخة التطوير

```bash
git clone https://github.com/mangapeak/Anilord.git
cd Anilord
./gradlew :app:assembleDebug
```

على Windows استخدم:

```powershell
.\gradlew.bat :app:assembleDebug
```

ينشئ Android Studio ملف `local.properties` وفيه مسار Android SDK تلقائيًا. يمكن أيضًا نسخ `local.properties.example` إلى `local.properties` ثم إضافة القيم المحلية المطلوبة.

## الإعدادات الخاصة والآمنة

لا يحتوي المستودع على مفاتيح توقيع، إعداد Firebase حقيقي، رموز بوتات، بيانات تقارير الأعطال، مفاتيح OAuth خاصة، أو معرفات AdMob الإنتاجية.

- الإعلانات تستخدم معرفات Google التجريبية افتراضيًا في كل نسخ المصدر العام.
- ضع معرفات AdMob الحقيقية في `local.properties` على جهاز البناء فقط.
- لتفعيل Firebase، نزّل `google-services.json` من مشروع Firebase الخاص بك وضعه داخل `app/`. الملف متجاهل بواسطة Git.
- تكاملات التتبع وبعض المصادر التي تحتاج مفاتيح خاصة تبقى معطلة حتى تضيف قيمك في `local.properties`.
- لا ترفع `local.properties` أو `google-services.json` أو ملف التوقيع إلى GitHub.

راجع [local.properties.example](local.properties.example) لمعرفة أسماء القيم المتاحة دون وجود أي أسرار حقيقية.

لخطوات رفع المجلد بأمان، راجع [UPLOAD_TO_GITHUB.md](UPLOAD_TO_GITHUB.md).

## إنشاء نسخة Release

بعد إضافة إعداداتك المحلية ومفتاح التوقيع الخاص بك:

```bash
./gradlew :app:bundleRelease
```

المشروع لا يضم مفتاح توقيع أو كلمة مروره. إعداد توقيع Google Play يجب أن يبقى خارج المستودع.

## المساهمة

المساهمات وتقارير الأخطاء مرحب بها. عند الإبلاغ عن مشكلة مصدر، اذكر اسم المصدر والرابط والخطوات التي أدت للمشكلة دون نشر أي بيانات تسجيل دخول.

## الترخيص والنَسب

هذا المشروع منشور وفق رخصة [GNU GPL v3](LICENSE). وهو عمل مشتق من Kotatsu وkotatsu-parsers؛ يجب الحفاظ على إشعارات النَسب والرخصة وإتاحة كود أي نسخة موزعة وفق شروط GPL-3.0. راجع [NOTICE.md](NOTICE.md).

## إخلاء المسؤولية

التطبيق لا يستضيف محتوى مانجا أو روايات أو أنمي، ولا يرتبط بالمواقع التي يعرضها. المحتوى يأتي من مصادر متاحة عبر الويب، وقد تتغير هذه المصادر أو تتوقف دون إشعار. يتحمل المستخدم والموزع مسؤولية الالتزام بالقوانين وشروط الخدمات في بلده.

---

### English summary

Anilord is an open-source Android manga, novel and anime client based on Kotatsu. It includes the parser source used by the app, supports offline downloads and multi-server anime playback, and builds with safe demo advertising IDs by default. Private Firebase, signing and service credentials are intentionally excluded.

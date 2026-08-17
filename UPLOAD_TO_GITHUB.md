# رفع المشروع إلى GitHub

ارفع **محتويات هذا المجلد فقط**، ولا ترفع مجلد العمل الأصلي الذي يحتوي على إعدادات البناء الخاصة.

## الطريقة الموصى بها

أنشئ مستودعًا فارغًا في GitHub بدون إضافة README أو License من الموقع، ثم افتح Terminal داخل مجلد `MangaPeak-OpenSource` ونفّذ:

```bash
git init
git add .
git update-index --chmod=+x gradlew
git update-index --chmod=+x kotatsu-parsers/gradlew
git status
git commit -m "Publish Manga Peak source code"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

قبل `git commit` تأكد من عدم ظهور أي من الملفات التالية في `git status`:

- `local.properties`
- `app/google-services.json`
- أي ملف APK أو AAB
- أي ملف JKS أو keystore أو ملف توقيع
- أي `.env` أو ملف service account

ملفات `README.md` و`LICENSE` و`NOTICE.md` و`SECURITY.md` والصور داخل `docs/images/` يجب أن تظهر ضمن الملفات المضافة.

إذا سبق أن رفعت سرًا إلى مستودع عام، لا يكفي حذفه في تحديث جديد؛ ألغِ المفتاح أو غيّر كلمة المرور ثم نظّف سجل Git القديم.

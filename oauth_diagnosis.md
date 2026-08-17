# تشخيص Google OAuth في Anilord

## النتيجة

التطبيق يستخدم deep link محلياً بقيمة `anilord://auth/callback` في `supabase.xml`، وManifest يسجل نفس scheme/host/path. هذه القيمة يجب أن تكون ضمن قائمة Redirect URLs في إعدادات Supabase Auth.

أما Google Cloud Authorized redirect URI لمزوّد Google فيجب أن يكون callback الخاص بـ Supabase، وليس deep link الخاص بالتطبيق:

`https://huoekbuqxbwijfmnmikv.supabase.co/auth/v1/callback`

بعد أن يعالج Supabase رد Google، يعيد التوجيه إلى `anilord://auth/callback` عبر `redirect_to` الذي يرسله التطبيق.

## الأدلة

1. توثيق Supabase Redirect URLs يذكر أن `redirectTo` يجب أن يطابق قائمة Redirect URLs في إعدادات المشروع: https://supabase.com/docs/guides/auth/redirect-urls
2. توثيق Supabase Native Mobile Deep Linking يوضح إضافة custom scheme إلى Auth settings، مثل `com.supabase://**`: https://supabase.com/docs/guides/auth/native-mobile-deep-linking
3. توثيق Supabase Login with Google يوضح إعداد مشروع Google Cloud وربط المزود عبر Supabase: https://supabase.com/docs/guides/auth/social-login/auth-google

## حالة المشروع

- Supabase project: `huoekbuqxbwijfmnmikv`
- Project name: `Anilord`
- Status: `ACTIVE_HEALTHY`
- App redirect URI: `anilord://auth/callback`
- Supabase callback for Google Cloud: `https://huoekbuqxbwijfmnmikv.supabase.co/auth/v1/callback`

## قرار التنفيذ

لا نغيّر deep link الحالي؛ تغييره بلا حاجة قد يكسر callback وPassword Reset. يجب تصحيح القيم في لوحتي Supabase/Google: إضافة `anilord://auth/callback` إلى Supabase Redirect URLs، واستخدام callback الخاص بـ Supabase في Google Cloud Authorized redirect URIs. التعديلات البرمجية الحالية تضيف فقط أنيميشن Startup وAuth ولا تمس منطق الجلسات أو OAuth.

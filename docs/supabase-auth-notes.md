# Supabase Auth integration notes

Sources consulted:

1. https://supabase.com/docs/guides/getting-started/quickstarts/kotlin
2. https://supabase.com/docs/reference/kotlin/initializing
3. https://supabase.com/docs/reference/kotlin/auth-signup
4. https://supabase.com/docs/reference/kotlin/auth-signinwithpassword
5. https://supabase.com/docs/reference/kotlin/auth-signinwithoauth
6. https://supabase.com/docs/reference/kotlin/auth-signinanonymously
7. https://github.com/supabase-community/supabase-kt

Key facts: Supabase's Kotlin client is supabase-kt. The current docs describe the BOM and auth-kt module, require a Ktor client engine, and use Kotlinx Serialization. Android OAuth and email/OTP callbacks use deeplinks; the Auth plugin can be configured with a scheme and host, and Android should call supabase.handleDeeplinks(intent). OAuth can be initiated with supabase.auth.signInWith(Google). The client uses the project URL and a publishable key; service-role keys must not be shipped in an Android client. Supabase docs recommend RLS on public tables and least-privilege grants.

Project facts from Supabase MCP: project name Anilord, ref huoekbuqxbwijfmnmikv, status ACTIVE_HEALTHY, URL https://huoekbuqxbwijfmnmikv.supabase.co, region eu-west-1, public schema currently has no tables. A legacy anon key and a publishable key are available; only the publishable/anon public key is suitable for the Android client.

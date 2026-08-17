# Security policy

## Reporting a vulnerability

Please report security issues privately through GitHub Security Advisories. Do not open a public issue containing credentials, user data, private source responses, or a working exploit.

## Secrets and local configuration

The public repository must never contain:

- `local.properties` or `.env` files;
- `app/google-services.json`;
- Android signing keys or signing passwords;
- service-account files, bot tokens, API secrets or crash-report credentials;
- production advertising configuration that should remain tied to a private release process.

Use `local.properties.example` only as a list of supported keys. Put real values in the ignored `local.properties` file on the build machine.

If a credential is committed accidentally, deleting it in a later commit is not enough. Revoke or rotate it immediately, then remove it from the complete Git history.

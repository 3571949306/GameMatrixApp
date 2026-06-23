# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.4.x   | :white_check_mark: |
| < 1.4.0 | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability in GameMatrixApp, please report it
privately via GitHub Security Advisories:

https://github.com/3571949306/GameMatrixApp/security/advisories/new

**Do not** open a public issue for security vulnerabilities.

We aim to:
- Acknowledge new reports within 3 business days
- Provide a fix or mitigation within 14 days for high-severity issues
- Credit reporters in the release notes (unless you prefer to remain anonymous)

## Secrets Management

This project uses the following convention for secrets and API keys:

- API keys and tokens live in `local.properties` (gitignored)
- They're injected into `BuildConfig` at compile time via `app/build.gradle`
- `keystore.properties` (gitignored) holds release signing config
- The `release-key.jks` keystore is **never** committed
- Production deploys use Play App Signing where applicable

If you accidentally commit a secret, rotate the secret **immediately** and
follow GitHub's guide to remove the secret from git history:
https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository

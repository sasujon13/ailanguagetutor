# Dev SMTP catcher (optional — local testing of bcheradip/ailt_api OTP emails)

Use this only when running **`ailt_api`** locally from `D:\VSCode\cheradip\bcheradip` on your PC.

Production OTP mail is sent from **Linux SMTP** configured in `bcheradip/ailt_api/.env`.

## Local flow

```
ailt_api (:8790)  --SMTP-->  127.0.0.1:1025  -->  server/mail/inbox/*.eml
```

- **From address:** set in `bcheradip/ailt_api/.env` (`SMTP_FROM`)
- With `DEV_LOG_OTP=true`, codes also print in the ailt_api console

## Start mail (before local ailt_api)

```powershell
cd server\mail
.\run-dev-smtp.ps1
```

## ailt_api `.env` for local SMTP

```env
SMTP_ENABLED=true
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_USE_TLS=false
DEV_LOG_OTP=true
PUBLIC_BASE_URL=http://127.0.0.1:8790/api/ailt
```

Production: see `D:\VSCode\cheradip\bcheradip\ailt_api\.env.production.example`.

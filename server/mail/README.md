# Mail on your PC (self-hosted production)

You host **cloud-api** on this machine and expose it via **Cloudflare Tunnel** (`ailt.cheradip.com`).  
Auth emails use the **same machine** — no separate mail host is required.

## How it works

```
cloud-api (:8790)  --SMTP-->  127.0.0.1:1025  -->  server/mail/inbox/*.eml
```

- **From address:** `admin@ailanguagetutor.com` (set in `cloud-api/.env`)
- **OTP / password-reset emails** are written to `server/mail/inbox/` as `.eml` files
- With `DEV_LOG_OTP=true`, codes also print in the cloud-api console

This is **production** for your setup as long as **mail + cloud-api** run on the PC that serves the tunnel.

> **Note:** Without public MX DNS for `ailanguagetutor.com`, the world cannot *reply* to `admin@ailanguagetutor.com`.  
> You only need **outbound** OTP delivery to users — which this provides.

## Start mail (every boot / before cloud-api)

```powershell
cd D:\VSCode\android\ailanguagetutor\server\mail
.\run-dev-smtp.ps1
```

Leave this terminal open (listening on **127.0.0.1:1025**).

## cloud-api `.env` (keep as-is for self-host)

```env
SMTP_ENABLED=true
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_FROM=admin@ailanguagetutor.com
SMTP_USER=admin@ailanguagetutor.com
SMTP_PASSWORD=
SMTP_USE_TLS=false
PUBLIC_BASE_URL=https://ailt.cheradip.com/api/ailt
```

## Read OTP emails

1. Trigger forgot-password / email change from the app  
2. Open newest file in `server/mail/inbox/`  
3. Or read cloud-api terminal if `DEV_LOG_OTP=true`

## Optional: real inbox later

If you add MX records for `ailanguagetutor.com`, point `SMTP_HOST` at that provider instead and set `SMTP_USE_TLS=true`.

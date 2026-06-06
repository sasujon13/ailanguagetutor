# Cheradip AILT API — MySQL on XAMPP

Self-hosted backend at **`https://ailt.cheradip.com/api/ailt/`**. Database: **`ailanguagetutor`**.

## One-time setup

### 1. XAMPP

1. Start **Apache** (optional) and **MySQL** in XAMPP Control Panel.
2. Default login: user `root`, empty password, port `3306`.

### 2. Configure

```powershell
cd server\cloud-api
copy .env.example .env
```

Edit `.env` if your MySQL password is not empty:

```env
DATABASE_URL=mysql+pymysql://root:YOUR_PASSWORD@127.0.0.1:3306/ailanguagetutor?charset=utf8mb4
ADMIN_SEED_PASSWORD=your-admin-password
```

Or set `ADMIN_SEED_PASSWORD` in repo root `local.env.properties` (loaded by scripts).

### 3. Create database + tables + seed

```powershell
.\scripts\init-db.ps1
```

Creates database `ailanguagetutor`, all tables, admin user, promo codes, language packs, AI providers.

## Run API

```powershell
.\scripts\run-dev.ps1
```

Health: http://localhost:8790/api/ailt/health → `"database":"ailanguagetutor"`

## Endpoints (MySQL-backed)

| Area | Routes |
|------|--------|
| Auth | `POST auth/login`, `register`, `verify-email`, `verify-whatsapp` |
| Device | `POST device/register` (trial in `device_trials`) |
| Billing | `POST billing/verify` |
| Promo | `GET promo/paywall-config`, `POST promo/validate` |
| Referral | `GET referral/policy`, `referral/balance` |
| Languages | `GET languages/list`, `languages/{code}/download` |
| Admin | promo CRUD, referral policy, AI providers/routing |
| AI fallback | `POST ai/activity-metadata`, `ai/explain-paragraph` |

OTP codes print to the server console when `DEV_LOG_OTP=true`.

Admin login: email from `ADMIN_SEED_EMAIL` + `ADMIN_SEED_PASSWORD`.

## phpMyAdmin

Open http://localhost/phpmyadmin → database **ailanguagetutor** → browse tables.

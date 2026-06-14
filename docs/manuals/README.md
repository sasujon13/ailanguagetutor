# In-app manuals

These markdown files are bundled in the Android app (`feature/help/src/main/assets/manuals/`) and shown under **Profile → User Manual**.

| File | Audience | In-app access |
|------|----------|---------------|
| [USER_MANUAL.md](USER_MANUAL.md) | All users (guest, trial, Pro, Plus) | Always |
| [ADMIN_MANUAL.md](ADMIN_MANUAL.md) | Administrators | Admin login only |
| [DEVELOPER_MANUAL.md](DEVELOPER_MANUAL.md) | Developers | Admin login only |

**Version:** v2.1.0 (updated for scanner editor, Practice hub, multilingual AI, auth/email, Admin Reports)

When editing a manual, update **both** this folder and `feature/help/src/main/assets/manuals/`.

**Formatting tips for in-app display:**

- Use `##` / `###` headings and `-` bullet lists (render well in `ManualParser`)
- Prefer unicode icons (📷 🎤 ⚙️) over markdown tables
- Use fenced code blocks for commands
- Avoid `\|` table syntax — it does not render as tables in the app

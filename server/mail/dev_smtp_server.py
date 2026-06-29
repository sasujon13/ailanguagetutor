"""Local dev SMTP — receives mail for admin@ailanguagetutor.com into server/mail/inbox/."""

from __future__ import annotations

from datetime import datetime
from email import message_from_bytes
from pathlib import Path

from aiosmtpd.controller import Controller

INBOX = Path(__file__).resolve().parent / "inbox"
HOST = "127.0.0.1"
PORT = 1025


class InboxHandler:
    async def handle_DATA(self, server, session, envelope):  # noqa: N802
        INBOX.mkdir(parents=True, exist_ok=True)
        msg = message_from_bytes(envelope.content)
        subject = msg.get("Subject", "no-subject")
        safe_from = (envelope.mail_from or "unknown").replace("@", "_at_")
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = INBOX / f"{ts}_{safe_from}.eml"
        path.write_bytes(envelope.content)
        print(f"[INBOX] saved {path.name} | from={envelope.mail_from} subject={subject}")
        return "250 Message accepted"


def main() -> None:
    handler = InboxHandler()
    controller = Controller(handler, hostname=HOST, port=PORT)
    controller.start()
    print(f"Dev SMTP listening on {HOST}:{PORT}")
    print(f"Inbound mail saved to: {INBOX}")
    print("Point bcheradip/ailt_api SMTP_HOST=127.0.0.1 SMTP_PORT=1025 to deliver outbound mail here.")
    try:
        import time
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        controller.stop()
        print("Stopped.")


if __name__ == "__main__":
    main()

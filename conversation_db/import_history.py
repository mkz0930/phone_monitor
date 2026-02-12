#!/usr/bin/env python3.12
"""Import OpenClaw session history into conversation vector DB."""

import json
import sys
from pathlib import Path
from datetime import datetime, timezone

# Add parent dir to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from conversation_db.db import ConversationDB


def parse_timestamp(ts_ms: int) -> str:
    """Convert Unix timestamp (ms) to ISO 8601 string."""
    return datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def extract_text(content_list: list) -> str:
    """Extract text from message content array."""
    texts = []
    for item in content_list:
        if isinstance(item, dict) and item.get("type") == "text":
            texts.append(item.get("text", ""))
    return "\n".join(texts).strip()


def import_session_history(history_data: dict, db: ConversationDB, session_key: str = "main", channel: str = "feishu"):
    """Import messages from OpenClaw session history JSON."""
    messages = history_data.get("messages", [])
    order = 0
    imported = 0
    skipped = 0

    for msg in messages:
        role = msg.get("role")
        content = msg.get("content", [])
        timestamp_ms = msg.get("timestamp")

        # Skip system messages and empty content
        if role == "system" or not content or not timestamp_ms:
            skipped += 1
            continue

        # Map role to sender
        if role == "user":
            sender = "Horse"
        elif role == "assistant":
            sender = "Claw"
        else:
            skipped += 1
            continue

        # Extract text content
        text = extract_text(content)
        if not text or text in ["HEARTBEAT_OK", "NO_REPLY"]:
            skipped += 1
            continue

        # Insert into DB
        order += 1
        timestamp_iso = parse_timestamp(timestamp_ms)
        
        try:
            msg_id = db.insert(
                content=text,
                sender=sender,
                session_key=session_key,
                order=order,
                channel=channel,
                timestamp=timestamp_iso,
            )
            imported += 1
            print(f"✓ [{order}] {sender}: {text[:60]}... (ID: {msg_id[:8]})")
        except Exception as e:
            print(f"✗ Failed to insert message {order}: {e}")
            skipped += 1

    return imported, skipped


def main():
    if len(sys.argv) < 2:
        print("Usage: python3.12 import_history.py <history.json>")
        print("Example: python3.12 import_history.py /tmp/session_history.json")
        sys.exit(1)

    history_file = Path(sys.argv[1])
    if not history_file.exists():
        print(f"Error: File not found: {history_file}")
        sys.exit(1)

    # Load history
    with open(history_file, "r", encoding="utf-8") as f:
        history_data = json.load(f)

    # Initialize DB
    db = ConversationDB()
    print(f"Initial DB count: {db.count()} messages\n")

    # Import
    session_key = history_data.get("sessionKey", "main")
    imported, skipped = import_session_history(history_data, db, session_key=session_key)

    print(f"\n{'='*60}")
    print(f"Import complete!")
    print(f"  Imported: {imported} messages")
    print(f"  Skipped:  {skipped} messages")
    print(f"  Final DB count: {db.count()} messages")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3.12
"""Import main session history into conversation database."""

import json
import sys
from pathlib import Path
from datetime import datetime, timezone

# Add parent to path
sys.path.insert(0, str(Path(__file__).parent.parent))
from conversation_db.db import ConversationDB

# Session history file
SESSION_FILE = Path.home() / ".openclaw/agents/main/sessions/main.jsonl"

def extract_text_content(content_list):
    """Extract text from message content array."""
    texts = []
    for item in content_list:
        if isinstance(item, dict) and item.get("type") == "text":
            text = item.get("text", "").strip()
            if text:
                texts.append(text)
    return "\n".join(texts)

def import_session():
    """Import main session history."""
    if not SESSION_FILE.exists():
        print(f"❌ Session file not found: {SESSION_FILE}")
        return
    
    db = ConversationDB()
    imported = 0
    skipped = 0
    
    print(f"📖 Reading session file: {SESSION_FILE}")
    
    with open(SESSION_FILE, "r", encoding="utf-8") as f:
        messages = [json.loads(line) for line in f if line.strip()]
    
    print(f"📊 Found {len(messages)} messages in session file")
    
    for idx, msg in enumerate(messages):
        role = msg.get("role")
        content = msg.get("content", [])
        timestamp_ms = msg.get("timestamp")
        
        # Skip system/tool messages
        if role not in ("user", "assistant"):
            skipped += 1
            continue
        
        # Extract text content
        text = extract_text_content(content)
        if not text or text in ("HEARTBEAT_OK", "NO_REPLY"):
            skipped += 1
            continue
        
        # Skip heartbeat prompts
        if "Read HEARTBEAT.md" in text or "A scheduled reminder" in text:
            skipped += 1
            continue
        
        # Convert timestamp
        if timestamp_ms:
            ts = datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc)
            iso_ts = ts.isoformat().replace("+00:00", "Z")
        else:
            iso_ts = None
        
        # Map role to sender
        sender = "Horse" if role == "user" else "Claw"
        
        # Generate message_id
        msg_id = f"main_{idx:06d}"
        
        try:
            db.insert(
                content=text,
                sender=sender,
                session_key="main",
                order=idx,
                channel="feishu",
                timestamp=iso_ts,
                message_id=msg_id,
            )
            imported += 1
            if imported % 100 == 0:
                print(f"  ✓ Imported {imported} messages...")
        except Exception as e:
            print(f"  ⚠️ Failed to import message {idx}: {e}")
            skipped += 1
    
    print(f"\n{'='*60}")
    print(f"Import complete!")
    print(f"Imported: {imported} messages")
    print(f"Skipped: {skipped} messages")
    print(f"Final DB count: {db.count()} messages")
    print(f"{'='*60}")

if __name__ == "__main__":
    import_session()

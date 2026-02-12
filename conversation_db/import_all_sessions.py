#!/usr/bin/env python3.12
"""Import ALL session history files into conversation database."""

import json
import sys
from pathlib import Path
from datetime import datetime, timezone

# Add parent to path
sys.path.insert(0, str(Path(__file__).parent.parent))
from conversation_db.db import ConversationDB

# Sessions directory
SESSIONS_DIR = Path.home() / ".openclaw/agents/main/sessions"

def extract_text_content(content_list):
    """Extract text from message content array."""
    texts = []
    for item in content_list:
        if isinstance(item, dict) and item.get("type") == "text":
            text = item.get("text", "").strip()
            if text:
                texts.append(text)
    return "\n".join(texts)

def import_all_sessions():
    """Import all session history files."""
    if not SESSIONS_DIR.exists():
        print(f"❌ Sessions directory not found: {SESSIONS_DIR}")
        return
    
    db = ConversationDB()
    session_files = sorted(SESSIONS_DIR.glob("*.jsonl"))
    
    print(f"📁 Found {len(session_files)} session files")
    print(f"📊 Current DB count: {db.count()} messages\n")
    
    total_imported = 0
    total_skipped = 0
    
    for session_file in session_files:
        session_key = session_file.stem
        
        try:
            with open(session_file, "r", encoding="utf-8") as f:
                messages = [json.loads(line) for line in f if line.strip()]
        except Exception as e:
            print(f"⚠️ Failed to read {session_file.name}: {e}")
            continue
        
        imported = 0
        skipped = 0
        
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
            
            # Skip heartbeat prompts and system messages
            if any(x in text for x in ["Read HEARTBEAT.md", "A scheduled reminder", "A cron job", "Pre-compaction"]):
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
            msg_id = f"{session_key}_{idx:06d}"
            
            try:
                db.insert(
                    content=text,
                    sender=sender,
                    session_key=session_key,
                    order=idx,
                    channel="feishu",
                    timestamp=iso_ts,
                    message_id=msg_id,
                )
                imported += 1
            except Exception as e:
                # Skip duplicates silently
                skipped += 1
        
        if imported > 0:
            print(f"✓ {session_file.name}: +{imported} messages")
        
        total_imported += imported
        total_skipped += skipped
    
    print(f"\n{'='*60}")
    print(f"Import complete!")
    print(f"Imported: {total_imported} messages")
    print(f"Skipped: {total_skipped} messages")
    print(f"Final DB count: {db.count()} messages")
    print(f"{'='*60}")

if __name__ == "__main__":
    import_all_sessions()

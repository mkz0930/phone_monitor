#!/usr/bin/env python3.12
"""Import Feishu conversation sessions into conversation database."""

import json
import sys
from pathlib import Path
from datetime import datetime, timezone
import re

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

def extract_message_id(text):
    """Extract Feishu message_id from text if present."""
    match = re.search(r'\[message_id:\s*(om_[a-f0-9]+)\]', text)
    return match.group(1) if match else None

def is_feishu_session(session_file):
    """Check if session file contains Feishu messages."""
    try:
        with open(session_file, "r", encoding="utf-8") as f:
            content = f.read(5000)  # Check first 5KB
            return "message_id: om_" in content or "[message_id: om_" in content
    except:
        return False

def import_feishu_sessions():
    """Import Feishu conversation sessions."""
    if not SESSIONS_DIR.exists():
        print(f"❌ Sessions directory not found: {SESSIONS_DIR}")
        return
    
    db = ConversationDB()
    
    # Find Feishu session files
    print(f"🔍 Scanning for Feishu conversation sessions...")
    all_files = list(SESSIONS_DIR.glob("*.jsonl"))
    feishu_files = [f for f in all_files if is_feishu_session(f)]
    
    print(f"📁 Found {len(feishu_files)} Feishu session files (out of {len(all_files)} total)")
    print(f"📊 Current DB count: {db.count()} messages\n")
    
    total_imported = 0
    total_skipped = 0
    
    for session_file in sorted(feishu_files):
        session_key = session_file.stem
        
        try:
            with open(session_file, "r", encoding="utf-8") as f:
                lines = [line for line in f if line.strip()]
                messages = []
                for line in lines:
                    try:
                        obj = json.loads(line)
                        # Handle both direct message format and wrapped format
                        if "message" in obj:
                            messages.append(obj["message"])
                        elif "role" in obj:
                            messages.append(obj)
                    except:
                        continue
        except Exception as e:
            print(f"⚠️ Failed to read {session_file.name}: {e}")
            continue
        
        imported = 0
        skipped = 0
        
        for idx, msg in enumerate(messages):
            role = msg.get("role")
            content = msg.get("content", [])
            timestamp_ms = msg.get("timestamp")
            
            # Skip non-conversation messages
            if role not in ("user", "assistant"):
                skipped += 1
                continue
            
            # Extract text content
            text = extract_text_content(content)
            if not text:
                skipped += 1
                continue
            
            # Skip system responses
            if text in ("HEARTBEAT_OK", "NO_REPLY"):
                skipped += 1
                continue
            
            # Skip system prompts
            if any(x in text for x in ["Read HEARTBEAT.md", "A cron job", "Pre-compaction", "System: [2026"]):
                skipped += 1
                continue
            
            # Extract Feishu message_id if present
            feishu_msg_id = extract_message_id(text)
            
            # Convert timestamp
            if timestamp_ms:
                ts = datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc)
                iso_ts = ts.isoformat().replace("+00:00", "Z")
            else:
                iso_ts = None
            
            # Map role to sender
            sender = "Horse" if role == "user" else "Claw"
            
            # Generate message_id (use Feishu ID if available)
            if feishu_msg_id:
                msg_id = feishu_msg_id
            else:
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
                # Skip duplicates or errors
                skipped += 1
        
        if imported > 0:
            print(f"✓ {session_file.name}: +{imported} messages (skipped {skipped})")
        
        total_imported += imported
        total_skipped += skipped
    
    print(f"\n{'='*60}")
    print(f"Import complete!")
    print(f"Imported: {total_imported} messages")
    print(f"Skipped: {total_skipped} messages")
    print(f"Final DB count: {db.count()} messages")
    print(f"{'='*60}")

if __name__ == "__main__":
    import_feishu_sessions()

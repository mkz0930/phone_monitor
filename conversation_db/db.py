"""Core vector database for Horse/Claw conversations using ChromaDB."""

import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import chromadb

DB_DIR = Path(__file__).parent / "chroma_store"
COLLECTION_NAME = "conversations"
VALID_SENDERS = {"Horse", "Claw"}


def _iso_to_epoch(iso: str) -> float:
    """Convert ISO 8601 string to Unix epoch seconds."""
    ts = iso.replace("Z", "+00:00")
    return datetime.fromisoformat(ts).timestamp()


def _epoch_to_iso(epoch: float) -> str:
    return datetime.fromtimestamp(epoch, tz=timezone.utc).isoformat().replace("+00:00", "Z")


class ConversationDB:
    def __init__(self, db_dir: Optional[Path] = None):
        path = str(db_dir or DB_DIR)
        self._client = chromadb.PersistentClient(path=path)
        self._collection = self._client.get_or_create_collection(
            name=COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )

    # -- CRUD ----------------------------------------------------------------

    def insert(
        self,
        content: str,
        sender: str,
        session_key: str,
        order: int,
        channel: str = "manual",
        timestamp: Optional[str] = None,
        message_id: Optional[str] = None,
    ) -> str:
        if sender not in VALID_SENDERS:
            raise ValueError(f"sender must be one of {VALID_SENDERS}")

        msg_id = message_id or uuid.uuid4().hex
        ts = timestamp or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

        metadata = {
            "timestamp": ts,
            "timestamp_epoch": _iso_to_epoch(ts),
            "sender": sender,
            "session_key": session_key,
            "channel": channel,
            "order": order,
        }

        self._collection.add(
            ids=[msg_id],
            documents=[content],
            metadatas=[metadata],
        )
        return msg_id

    def get(self, message_id: str) -> Optional[dict]:
        result = self._collection.get(ids=[message_id], include=["documents", "metadatas"])
        if not result["ids"]:
            return None
        return self._format_one(result, 0)

    def delete(self, message_id: str) -> bool:
        existing = self._collection.get(ids=[message_id])
        if not existing["ids"]:
            return False
        self._collection.delete(ids=[message_id])
        return True

    # -- Queries -------------------------------------------------------------

    def search(self, query: str, n: int = 10, sender: Optional[str] = None) -> list[dict]:
        where = {"sender": sender} if sender else None
        results = self._collection.query(
            query_texts=[query],
            n_results=n,
            where=where,
            include=["documents", "metadatas", "distances"],
        )
        return self._format_query(results)

    def query_by_time(
        self, start: str, end: str, sender: Optional[str] = None
    ) -> list[dict]:
        start_epoch = _iso_to_epoch(start)
        end_epoch = _iso_to_epoch(end)
        where_clauses = [
            {"timestamp_epoch": {"$gte": start_epoch}},
            {"timestamp_epoch": {"$lte": end_epoch}},
        ]
        if sender:
            where_clauses.append({"sender": sender})

        where = {"$and": where_clauses}
        results = self._collection.get(
            where=where, include=["documents", "metadatas"]
        )
        items = [self._format_one(results, i) for i in range(len(results["ids"]))]
        items.sort(key=lambda x: (x["session_key"], x["order"]))
        return items

    def query_by_session(self, session_key: str) -> list[dict]:
        results = self._collection.get(
            where={"session_key": session_key},
            include=["documents", "metadatas"],
        )
        items = [self._format_one(results, i) for i in range(len(results["ids"]))]
        items.sort(key=lambda x: x["order"])
        return items

    def list_sessions(self) -> list[str]:
        results = self._collection.get(include=["metadatas"])
        sessions = sorted({m["session_key"] for m in results["metadatas"]})
        return sessions

    def count(self) -> int:
        return self._collection.count()

    # -- Helpers -------------------------------------------------------------

    @staticmethod
    def _format_one(result: dict, idx: int) -> dict:
        meta = result["metadatas"][idx]
        return {
            "message_id": result["ids"][idx],
            "content": result["documents"][idx],
            "timestamp": meta["timestamp"],
            "sender": meta["sender"],
            "session_key": meta["session_key"],
            "channel": meta["channel"],
            "order": meta["order"],
        }

    @staticmethod
    def _format_query(result: dict) -> list[dict]:
        items = []
        for i in range(len(result["ids"][0])):
            meta = result["metadatas"][0][i]
            items.append({
                "message_id": result["ids"][0][i],
                "content": result["documents"][0][i],
                "distance": result["distances"][0][i],
                "timestamp": meta["timestamp"],
                "sender": meta["sender"],
                "session_key": meta["session_key"],
                "channel": meta["channel"],
                "order": meta["order"],
            })
        return items

from __future__ import annotations

import uuid
import hashlib
import math
from functools import lru_cache
from dataclasses import dataclass, field
from typing import Any

from app.config import Settings


@dataclass
class KnowledgeRetriever:
    settings: Settings
    citations: list[dict[str, Any]] = field(default_factory=list)

    @property
    def enabled(self) -> bool:
        provider = getattr(self.settings, "embedding_provider", "local")
        has_provider = provider == "local" or bool(self.settings.external_llm_api_key.strip())
        return bool(self.settings.qdrant_url.strip() and has_provider)

    def search(self, query: str) -> dict[str, Any]:
        if not self.enabled:
            return {"evidence": [], "source": "knowledge-unavailable"}
        try:
            client, embeddings = self._clients()
            vector = self._embed_query(embeddings, query)
            response = client.query_points(
                collection_name=self.settings.qdrant_collection,
                query=vector,
                limit=5,
                with_payload=True,
            )
            evidence = []
            self.citations = []
            for point in response.points:
                payload = point.payload or {}
                if payload.get("status") != "PUBLISHED" and payload.get("source_type") == "ITEM":
                    continue
                score = float(point.score)
                if score < 0.35:
                    continue
                citation = {
                    "sourceId": str(payload.get("source_id") or ""),
                    "sourceType": str(payload.get("source_type") or ""),
                    "title": str(payload.get("title") or ""),
                    "score": round(score, 4),
                }
                self.citations.append(citation)
                evidence.append({**citation, "content": str(payload.get("content") or "")[:900]})
            return {"evidence": evidence, "source": "qdrant"}
        except Exception:
            return {"evidence": [], "source": "knowledge-unavailable"}

    def upsert(self, source: dict[str, Any]) -> None:
        if not self.enabled:
            raise RuntimeError("rag_not_configured")
        client, embeddings = self._clients()
        source_id = str(source.get("sourceId") or "")
        source_type = str(source.get("sourceType") or "")
        if not source_id or not source_type:
            raise ValueError("knowledge_source_missing_id")
        point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"campus-agent:{source_type}:{source_id}"))
        content = str(source.get("content") or "").strip()
        if not content:
            client.delete(collection_name=self.settings.qdrant_collection, points_selector=[point_id])
            return
        vector = self._embed_documents(embeddings, content)
        self._ensure_collection(client, len(vector))
        from qdrant_client.models import PointStruct

        client.upsert(
            collection_name=self.settings.qdrant_collection,
            points=[PointStruct(id=point_id, vector=vector, payload={
                "source_id": source_id,
                "source_type": source_type,
                "title": str(source.get("title") or ""),
                "content": content,
                "status": str(source.get("status") or "PUBLISHED"),
                "source_ref": str(source.get("sourceRef") or ""),
                "version_no": source.get("versionNo"),
            })],
        )

    def delete(self, aggregate_type: str, aggregate_id: str) -> None:
        if not self.enabled:
            raise RuntimeError("rag_not_configured")
        client, _ = self._clients()
        point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"campus-agent:{aggregate_type}:{aggregate_id}"))
        client.delete(collection_name=self.settings.qdrant_collection, points_selector=[point_id])

    def _clients(self):
        from qdrant_client import QdrantClient
        if getattr(self.settings, "embedding_provider", "local") == "local":
            return QdrantClient(url=self.settings.qdrant_url), _LocalEmbeddings(self.settings)
        from langchain_openai import OpenAIEmbeddings

        return (
            QdrantClient(url=self.settings.qdrant_url),
            OpenAIEmbeddings(
                api_key=self.settings.external_llm_api_key,
                base_url=self.settings.external_llm_base_url,
                model=self.settings.embedding_model,
                timeout=self.settings.llm_timeout_seconds,
                max_retries=0,
            ),
        )

    def _embed_query(self, embeddings: Any, text: str) -> list[float]:
        try:
            return embeddings.embed_query(text)
        except Exception:
            if not self.settings.local_embedding_fallback:
                raise
            return _local_embedding(text)

    def _embed_documents(self, embeddings: Any, text: str) -> list[float]:
        try:
            return embeddings.embed_documents([text])[0]
        except Exception:
            if not self.settings.local_embedding_fallback:
                raise
            return _local_embedding(text)

    def _ensure_collection(self, client: Any, vector_size: int) -> None:
        from qdrant_client.models import Distance, VectorParams

        try:
            client.get_collection(self.settings.qdrant_collection)
        except Exception:
            client.create_collection(
                collection_name=self.settings.qdrant_collection,
                vectors_config=VectorParams(size=vector_size, distance=Distance.COSINE),
            )


def _local_embedding(text: str, size: int = 384) -> list[float]:
    """Stable offline vector for local integration when provider embeddings are unavailable."""
    values = [0.0] * size
    tokens = text.lower().split() or [text.lower()]
    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        for offset in range(0, len(digest), 4):
            index = int.from_bytes(digest[offset:offset + 4], "big") % size
            values[index] += 1.0 if digest[offset] & 1 else -1.0
    norm = math.sqrt(sum(value * value for value in values)) or 1.0
    return [value / norm for value in values]


class _LocalEmbeddings:
    def __init__(self, settings: Settings):
        self.settings = settings

    def embed_query(self, text: str) -> list[float]:
        return self._model().encode(text, normalize_embeddings=True).tolist()

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return self._model().encode(texts, normalize_embeddings=True).tolist()

    @lru_cache(maxsize=1)
    def _model(self):
        from sentence_transformers import SentenceTransformer

        return SentenceTransformer(
            getattr(self.settings, "local_embedding_model", "BAAI/bge-m3"),
            device=getattr(self.settings, "local_embedding_device", "cpu"),
        )

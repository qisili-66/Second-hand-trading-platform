from types import SimpleNamespace

from app.rag import KnowledgeRetriever, _local_embedding


def test_local_embedding_is_stable_and_normalized():
    first = _local_embedding("校园教材")
    second = _local_embedding("校园教材")

    assert first == second
    assert len(first) == 384
    assert 0.99 < sum(value * value for value in first) ** 0.5 < 1.01


def test_embedding_fallback_is_used_when_provider_fails(monkeypatch):
    settings = SimpleNamespace(
        qdrant_url="http://127.0.0.1:6333",
        external_llm_api_key="configured",
        external_llm_base_url="https://example.invalid/v1",
        embedding_model="text-embedding-v4",
        llm_timeout_seconds=1,
        local_embedding_fallback=True,
    )
    retriever = KnowledgeRetriever(settings)

    class BrokenEmbeddings:
        def embed_query(self, _text):
            raise RuntimeError("provider unavailable")

        def embed_documents(self, _texts):
            raise RuntimeError("provider unavailable")

    vector = retriever._embed_query(BrokenEmbeddings(), "规则")
    assert len(vector) == 384

from __future__ import annotations

from app.config import Settings
from app.rag import KnowledgeRetriever
from app.tool_gateway import ToolGateway


def run_once(settings: Settings | None = None, limit: int = 20) -> int:
    settings = settings or Settings()
    gateway = ToolGateway(settings, None)
    retriever = KnowledgeRetriever(settings)
    claimed = gateway.call("knowledge-outbox/claim", {"limit": limit})
    events = claimed if isinstance(claimed, list) else []
    completed = 0
    for event in events:
        event_id = event.get("id")
        try:
            source = gateway.call("knowledge-outbox/source", event)
            if not isinstance(source, dict):
                raise RuntimeError("knowledge_source_invalid")
            if source.get("deleted") or event.get("eventType") == "DELETE":
                retriever.delete(str(event.get("aggregateType")), str(event.get("aggregateId")))
            else:
                retriever.upsert(source)
            gateway.call("knowledge-outbox/complete", {"id": event_id, "success": True})
            completed += 1
        except Exception as exc:
            gateway.call("knowledge-outbox/complete", {"id": event_id, "success": False, "error": type(exc).__name__})
    return completed


if __name__ == "__main__":
    print(run_once())

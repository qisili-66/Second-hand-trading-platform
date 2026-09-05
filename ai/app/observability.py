from __future__ import annotations

import logging

from app.config import Settings

logger = logging.getLogger(__name__)


def configure_observability(settings: Settings) -> None:
    """Enable local console or enterprise OTLP trace exporting when configured."""
    if not settings.otel_exporter_otlp_endpoint.strip() and not settings.otel_console_exporter:
        return
    try:
        from opentelemetry import trace
        from opentelemetry.sdk.trace.export import BatchSpanProcessor, ConsoleSpanExporter, SimpleSpanProcessor
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider

        provider = TracerProvider(resource=Resource.create({"service.name": settings.otel_service_name}))
        if settings.otel_exporter_otlp_endpoint.strip():
            provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=settings.otel_exporter_otlp_endpoint)))
        if settings.otel_console_exporter:
            provider.add_span_processor(SimpleSpanProcessor(ConsoleSpanExporter()))
        trace.set_tracer_provider(provider)
    except Exception as exc:
        logger.warning("otel_configuration_skipped error_type=%s", type(exc).__name__)

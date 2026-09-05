from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
import time
from typing import Annotated, Any, TypedDict

from langgraph.graph.message import add_messages

from app.agents.buyer_agent import BuyerAgent
from app.config import Settings
from app.llm import ExternalLLM
from app.schemas import AgentRequest, AgentStep, BuyerAgentResponse, ItemRecommendation, KnowledgeCitation
from app.rag import KnowledgeRetriever
from app.tool_gateway import ToolGateway


class BuyerGraphState(TypedDict):
    messages: Annotated[list[Any], add_messages]
    calls: int


class BuyerRunAgent:
    """Bounded LangGraph orchestration around Spring-owned read-only tools."""

    READ_ONLY_TOOL_NAMES = frozenset({
        "search-items",
        "item-realtime",
        "seller-summary",
        "order-status",
        "user-preferences",
        "trade-rules",
        "product-knowledge",
    })

    def __init__(self, settings: Settings):
        self.settings = settings

    def _tool_call_limit(self) -> int:
        return max(1, min(int(self.settings.agent_max_tool_calls), 6))

    def _remaining_tool_call_limit(self, used_calls: int) -> int:
        return max(0, self._tool_call_limit() - used_calls)

    def run(self, request: AgentRequest, run_id: str = "", trace_id: str = "") -> BuyerAgentResponse:
        deadline = time.monotonic() + self.settings.agent_run_timeout_seconds
        gateway = ToolGateway(self.settings, request.user_id, run_id, deadline)
        retriever = KnowledgeRetriever(self.settings)
        llm_configured = ExternalLLM(self.settings).configured
        try:
            from opentelemetry import trace
            span_context = trace.get_tracer("campus-agent").start_as_current_span("agent.buyer.run")
        except Exception:
            span_context = _NoopSpan()
        with span_context as span:
            span.set_attribute("agent.run_id", run_id)
            response = self._fallback(request, gateway)
            if llm_configured and time.monotonic() < deadline:
                response = self._run_graph_before_deadline(request, gateway, retriever, response, deadline) or response
        response.run_id = run_id
        response.trace_id = trace_id
        response.model = self.settings.external_llm_model if llm_configured else "rule-fallback"
        response.steps = [AgentStep.model_validate(step) for step in gateway.steps]
        response.citations = [KnowledgeCitation.model_validate(citation) for citation in retriever.citations]
        return response

    def _run_graph_before_deadline(
        self, request: AgentRequest, gateway: ToolGateway, retriever: KnowledgeRetriever,
        fallback: BuyerAgentResponse, deadline: float,
    ) -> BuyerAgentResponse | None:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return None
        executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix="campus-agent-graph")
        future = executor.submit(self._run_graph, request, gateway, retriever, fallback, deadline)
        try:
            return future.result(timeout=remaining)
        except FutureTimeoutError:
            return None
        finally:
            executor.shutdown(wait=False, cancel_futures=True)

    def _fallback(self, request: AgentRequest, gateway: ToolGateway) -> BuyerAgentResponse:
        # The existing deterministic parser and risk copy remain useful, but data
        # now comes only from Spring's tool gateway.
        fallback_agent = BuyerAgent(self.settings, gateway=gateway)
        return fallback_agent._fallback(request)

    def _run_graph(
        self, request: AgentRequest, gateway: ToolGateway, retriever: KnowledgeRetriever, fallback: BuyerAgentResponse, deadline: float
    ) -> BuyerAgentResponse | None:
        try:
            from langchain_core.messages import HumanMessage, SystemMessage
            from langchain_core.tools import tool
            from langchain_openai import ChatOpenAI
            from langgraph.graph import END, StateGraph
            from langgraph.prebuilt import ToolNode
        except Exception:
            return None

        @tool("search_items")
        def search_items(keyword: str = "", campus: str = "", max_price: float | None = None) -> dict[str, Any]:
            """Search currently on-sale campus items with optional keyword, campus and maximum price."""
            return gateway.call("search-items", {"keyword": keyword, "campus": campus, "maxPrice": max_price})

        @tool("get_item_realtime")
        def get_item_realtime(item_id: int) -> dict[str, Any]:
            """Read the current price and availability for one item."""
            return gateway.call("item-realtime", {"itemId": item_id})

        @tool("get_seller_summary")
        def get_seller_summary(seller_id: int) -> dict[str, Any]:
            """Read public seller credit and aggregate review information."""
            return gateway.call("seller-summary", {"sellerId": seller_id})

        @tool("get_order_status")
        def get_order_status(order_id: int) -> dict[str, Any]:
            """Read the authenticated user's own order status."""
            return gateway.call("order-status", {"orderId": order_id})

        @tool("get_user_preferences")
        def get_user_preferences() -> dict[str, Any]:
            """Read the authenticated user's recent favorites without private profile data."""
            return gateway.call("user-preferences", {})

        @tool("get_trade_rules")
        def get_trade_rules() -> dict[str, Any]:
            """Read the current structured platform trading rules."""
            return gateway.call("trade-rules", {})

        @tool("search_product_knowledge")
        def search_product_knowledge(query: str) -> dict[str, Any]:
            """Retrieve cited product and platform knowledge when real-time tools do not answer the question."""
            return retriever.search(query)

        tools = [
            search_items,
            get_item_realtime,
            get_seller_summary,
            get_order_status,
            get_user_preferences,
            get_trade_rules,
            search_product_knowledge,
        ]
        tool_call_limit = self._tool_call_limit()
        initial_calls = len(gateway.steps)
        remaining_tool_calls = self._remaining_tool_call_limit(initial_calls)
        if remaining_tool_calls == 0:
            return fallback
        model = ChatOpenAI(
            api_key=self.settings.external_llm_api_key,
            base_url=self.settings.external_llm_base_url,
            model=self.settings.external_llm_model,
            temperature=0.2,
            timeout=max(1, min(self.settings.llm_timeout_seconds, int(max(1, deadline - time.monotonic())))),
            max_retries=0,
        ).bind_tools(tools)

        def call_model(state: BuyerGraphState) -> dict[str, Any]:
            message = model.invoke(state["messages"])
            remaining_calls = max(0, tool_call_limit - state["calls"])
            message = self._limit_tool_calls(message, remaining_calls)
            return {"messages": [message], "calls": state["calls"] + len(getattr(message, "tool_calls", []) or [])}

        def route(state: BuyerGraphState) -> str:
            last = state["messages"][-1]
            if getattr(last, "tool_calls", None) and state["calls"] <= tool_call_limit:
                return "tools"
            return END

        graph = StateGraph(BuyerGraphState)
        graph.add_node("agent", call_model)
        graph.add_node("tools", ToolNode(tools))
        graph.set_entry_point("agent")
        graph.add_conditional_edges("agent", route, {"tools": "tools", END: END})
        graph.add_edge("tools", "agent")
        state = graph.compile().invoke(
            {
                "messages": [
                    SystemMessage(content=(
                        "你是校园二手平台的买家导购 Agent。必须通过工具确认价格、在售状态、"
                        "卖家信用和订单状态；不能编造事实，不能执行任何写操作。信息足够时用简洁中文收尾。"
                    )),
                    HumanMessage(content=request.message),
                ],
                "calls": initial_calls,
            },
            {"recursion_limit": remaining_tool_calls * 2 + 2},
        )
        final_text = str(getattr(state["messages"][-1], "content", "")).strip()
        if final_text:
            fallback.summary = final_text[:600]
        if gateway.latest_search:
            fallback.recommendations = self._recommendations_from_tool(request, gateway.latest_search, fallback)
        return fallback

    @staticmethod
    def _limit_tool_calls(message: Any, remaining_calls: int) -> Any:
        tool_calls = list(getattr(message, "tool_calls", []) or [])
        if len(tool_calls) <= remaining_calls:
            return message
        return message.model_copy(update={"tool_calls": tool_calls[:remaining_calls]})

    def _recommendations_from_tool(
        self, request: AgentRequest, rows: list[dict[str, Any]], fallback: BuyerAgentResponse
    ) -> list[ItemRecommendation]:
        return [
            ItemRecommendation(
                item_id=row.get("itemId"),
                title=str(row.get("title") or "校园闲置"),
                price=row.get("price"),
                campus=str(row.get("campus") or ""),
                condition=str(row.get("conditionLevel") or ""),
                reason="已根据你的需求从当前在售商品中筛选，价格与状态以实时查询结果为准。",
                risk="面交前请核对实物、配件和卖家描述。",
                bargain_range="请在确认成色和配件后与卖家协商。",
            )
            for row in rows[:3]
        ]


class _NoopSpan:
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def set_attribute(self, *_args) -> None:
        return None

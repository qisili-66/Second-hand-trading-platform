from __future__ import annotations

import json
from typing import Any, TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


def validate_model(model: type[T], data: dict[str, Any]) -> T:
    if hasattr(model, "model_validate"):
        return model.model_validate(data)
    return model.parse_obj(data)


def dump_json(model: BaseModel) -> str:
    if hasattr(model, "model_dump_json"):
        return json.dumps(model.model_dump(mode="json"), ensure_ascii=False)
    return json.dumps(model.dict(), ensure_ascii=False)

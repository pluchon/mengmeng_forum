"""AI Gateway 路由；仅负责内网鉴权与请求转交。"""

from __future__ import annotations

from flask import jsonify

from api import api
from api.common import RouteResponse, ai_hub_auth_error, json_payload
from services.ai_gateway_service import execute_gateway


@api.route("/gateway/invoke", methods=["POST"])
def invoke_gateway() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    body, status = execute_gateway(json_payload())
    return jsonify(body), status

"""AI Gateway 路由；仅负责内网鉴权与请求转交。"""

from __future__ import annotations

import json

from flask import Response, jsonify, stream_with_context

from api import api
from api.common import RouteResponse, ai_hub_auth_error, json_payload
from services.ai_gateway_service import execute_gateway, stream_gateway


@api.route("/gateway/invoke", methods=["POST"])
def invoke_gateway() -> RouteResponse:
    auth_error = ai_hub_auth_error()
    if auth_error:
        return auth_error
    body, status = execute_gateway(json_payload())
    return jsonify(body), status


@api.route("/gateway/stream", methods=["POST"])
def stream_gateway_route() -> Response:
    auth_error = ai_hub_auth_error()
    if auth_error:
        response, status = auth_error
        response.status_code = status
        return response
    payload = json_payload()

    def generate():
        for event in stream_gateway(payload):
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )

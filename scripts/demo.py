#!/usr/bin/env python3
"""演示完整 API 流程；只创建新的工单，不清理已有数据。"""
import base64
import json
import os
import urllib.request
import urllib.error

base = os.environ.get("BASE_URL", "http://localhost:8080")
credential = base64.b64encode((os.environ.get("APP_USER", "admin") + ":" +
    os.environ.get("APP_PASSWORD", "demo-password")).encode()).decode()


def call(method, path, body=None, expected=200):
    request = urllib.request.Request(base + path, method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": "Basic " + credential, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status, result = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, result = error.code, json.load(error)
    assert status == expected, (method, path, status, result)
    print(method, path, status, json.dumps(result, ensure_ascii=False))
    return result


ticket = call("POST", "/api/tickets", {"title": "支付失败", "description": "重复扣款，请排查",
    "category": "PAYMENT", "priority": "HIGH"}, 201)
id_ = ticket["id"]
call("GET", f"/api/tickets/{id_}")
call("GET", "/api/tickets?status=OPEN&size=5")
for version, target in enumerate(["IN_PROGRESS", "RESOLVED", "CLOSED"]):
    updated = call("POST", f"/api/tickets/{id_}/transitions",
        {"target": target, "version": version, "note": "演示状态流转"})
    assert updated["version"] == version + 1
call("POST", f"/api/tickets/{id_}/transitions",
    {"target": "IN_PROGRESS", "version": 0, "note": "模拟旧版本请求"}, 409)
events = call("GET", f"/api/tickets/{id_}/events")
assert len(events) == 4
print("演示通过：创建、查询、完整状态流转、版本冲突与审计记录。")

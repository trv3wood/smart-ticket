#!/usr/bin/env python3
"""验证真实 Redis 缓存；可选临时停止本项目 Redis 验证回源并自动恢复。"""
import argparse
import base64
import json
import os
import subprocess
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--outage", action="store_true", help="临时停止本项目 Redis，验证数据库回源后恢复")
args = parser.parse_args()
base = os.environ.get("BASE_URL", "http://localhost:8080")
auth = base64.b64encode((os.environ.get("APP_USER", "admin") + ":" +
    os.environ.get("APP_PASSWORD", "demo-password")).encode()).decode()


def call(method, path, data=None):
    request = urllib.request.Request(base + path, method=method,
        data=None if data is None else json.dumps(data).encode(),
        headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


def redis(*command):
    return subprocess.check_output(["podman", "exec", "smart-ticket-redis", "redis-cli", "--raw", *command], text=True).strip()


ticket = call("POST", "/api/tickets", {"title": "缓存验证", "description": "测试专用工单",
    "category": "SYSTEM", "priority": "MEDIUM"})
id_ = ticket["id"]
key = f"ticket:detail:{id_}"
assert call("GET", f"/api/tickets/{id_}")["status"] == "OPEN"
assert json.loads(redis("GET", key))["status"] == "OPEN"
ttl = int(redis("TTL", key))
assert 1 <= ttl <= 44, ttl
print(f"缓存回填通过，id={id_}，TTL={ttl} 秒")
call("POST", f"/api/tickets/{id_}/transitions", {"target": "IN_PROGRESS", "version": 0, "note": "缓存失效验证"})
assert redis("EXISTS", key) == "0"
assert call("GET", f"/api/tickets/{id_}")["version"] == 1
assert json.loads(redis("GET", key))["version"] == 1
print("提交后删除与新版本回填通过")
if args.outage:
    try:
        subprocess.run(["podman", "stop", "-t", "1", "smart-ticket-redis"], check=True, stdout=subprocess.DEVNULL)
        assert call("GET", f"/api/tickets/{id_}")["version"] == 1
        print("Redis 停止时回源数据库通过")
    finally:
        subprocess.run(["podman", "start", "smart-ticket-redis"], check=True, stdout=subprocess.DEVNULL)
        print("Redis 已恢复")

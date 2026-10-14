#!/usr/bin/env python3
"""本地 API 读压测：输出实际吞吐和延迟，不预设性能结论。"""
import argparse
import base64
import concurrent.futures
import json
import os
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--requests", type=int, default=200)
parser.add_argument("--workers", type=int, default=8)
parser.add_argument("--ticket-id", type=int, required=True)
args = parser.parse_args()
if args.requests < 1 or args.workers < 1:
    parser.error("请求数和并发数必须大于 0")
base = os.environ.get("BASE_URL", "http://localhost:8080")
auth = base64.b64encode((os.environ.get("APP_USER", "admin") + ":" +
    os.environ.get("APP_PASSWORD", "demo-password")).encode()).decode()


def read(_):
    start = time.perf_counter()
    try:
        request = urllib.request.Request(f"{base}/api/tickets/{args.ticket_id}",
            headers={"Authorization": "Basic " + auth})
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
        return (time.perf_counter() - start) * 1000, True
    except Exception:
        return (time.perf_counter() - start) * 1000, False


start = time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
    results = list(executor.map(read, range(args.requests)))
elapsed = time.perf_counter() - start
latencies = sorted(ms for ms, ok in results if ok)
print(json.dumps({"requests": args.requests, "workers": args.workers,
    "success": len(latencies), "errors": args.requests-len(latencies),
    "elapsed_seconds": round(elapsed, 3), "requests_per_second": round(args.requests/elapsed, 2),
    "p95_ms": round(latencies[min(len(latencies)-1, int(len(latencies)*.95))], 2) if latencies else None}, indent=2))

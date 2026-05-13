import argparse
import json
import os
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from typing import Dict, List, Optional

import requests


@dataclass
class Endpoint:
    method: str
    path: str
    name: str
    body: Optional[dict] = None
    headers: Optional[dict] = None
    # For multipart upload (e.g. AI tongue diagnosis)
    file_path: Optional[str] = None  # local file path on the pressure-testing machine
    file_field: Optional[str] = None  # form field name, default: "file"


class Stats:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.latencies_ms: List[float] = []
        self.total = 0
        self.ok = 0
        self.failed = 0
        self.status_counter: Dict[int, int] = {}
        self.error_counter: Dict[str, int] = {}

    def record(self, elapsed_ms: float, status: Optional[int], error: Optional[str]) -> None:
        with self.lock:
            self.total += 1
            self.latencies_ms.append(elapsed_ms)
            if error:
                self.failed += 1
                self.error_counter[error] = self.error_counter.get(error, 0) + 1
                return
            if status is not None and 200 <= status < 400:
                self.ok += 1
            else:
                self.failed += 1
            if status is not None:
                self.status_counter[status] = self.status_counter.get(status, 0) + 1

    def summary(self, duration_s: int) -> dict:
        lats = sorted(self.latencies_ms)
        if not lats:
            return {
                "total": 0,
                "ok": 0,
                "failed": 0,
                "error_rate_pct": 0.0,
                "rps": 0.0,
                "avg_ms": 0.0,
                "p50_ms": 0.0,
                "p95_ms": 0.0,
                "p99_ms": 0.0,
                "min_ms": 0.0,
                "max_ms": 0.0,
                "status_counter": {},
                "error_counter": {},
            }

        def pct(p: float) -> float:
            idx = int((len(lats) - 1) * p)
            return lats[idx]

        error_rate = (self.failed / self.total * 100.0) if self.total else 0.0
        return {
            "total": self.total,
            "ok": self.ok,
            "failed": self.failed,
            "error_rate_pct": round(error_rate, 3),
            "rps": round(self.total / max(duration_s, 1), 3),
            "avg_ms": round(statistics.mean(lats), 3),
            "p50_ms": round(pct(0.50), 3),
            "p95_ms": round(pct(0.95), 3),
            "p99_ms": round(pct(0.99), 3),
            "min_ms": round(lats[0], 3),
            "max_ms": round(lats[-1], 3),
            "status_counter": dict(sorted(self.status_counter.items(), key=lambda x: x[0])),
            "error_counter": self.error_counter,
        }


def build_endpoints(config_path: Optional[str]) -> List[Endpoint]:
    if not config_path:
        # 默认压测配置：无需登录即可执行
        return [
            Endpoint("GET", "/", "home"),
            Endpoint("GET", "/api/captcha/image", "captcha"),
            Endpoint("GET", "/api/article/list?pageNum=1&pageSize=10", "article_list"),
        ]

    with open(config_path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    endpoints: List[Endpoint] = []
    for item in raw:
        endpoints.append(
            Endpoint(
                method=str(item["method"]).upper(),
                path=str(item["path"]),
                name=str(item.get("name") or f'{item["method"]} {item["path"]}'),
                body=item.get("body"),
                headers=item.get("headers"),
                file_path=item.get("file_path"),
                file_field=item.get("file_field"),
            )
        )
    return endpoints


def make_session(timeout_s: int, token: Optional[str]) -> requests.Session:
    s = requests.Session()
    s.headers.update({"User-Agent": "pressure-test-script/1.0"})
    if token:
        s.headers.update({"Authorization": f"Bearer {token}"})
    s.request_timeout = timeout_s
    return s


def worker(
    worker_id: int,
    base_url: str,
    deadline: float,
    timeout_s: int,
    endpoints: List[Endpoint],
    think_ms: int,
    token: Optional[str],
    stats: Stats,
    verify_tls: bool,
) -> None:
    session = make_session(timeout_s, token)
    i = 0
    while time.time() < deadline:
        ep = endpoints[i % len(endpoints)]
        i += 1
        url = base_url + ep.path
        start = time.perf_counter()
        status = None
        err = None
        try:
            request_kwargs: dict = {
                "headers": ep.headers,
                "timeout": timeout_s,
                "verify": verify_tls,
            }

            if ep.file_path:
                # Multipart form upload
                files = {}
                field = ep.file_field or "file"
                with open(ep.file_path, "rb") as fobj:
                    files[field] = (os.path.basename(ep.file_path), fobj, "application/octet-stream")
                    # When uploading a file, `body` is treated as regular form fields (data)
                    request_kwargs["files"] = files
                    request_kwargs["data"] = ep.body or {}
                    resp = session.request(ep.method, url, **request_kwargs)
            else:
                # JSON request body (if provided)
                request_kwargs["json"] = ep.body
                resp = session.request(ep.method, url, **request_kwargs)

            status = resp.status_code
        except Exception as e:  # noqa: BLE001
            err = type(e).__name__
        finally:
            elapsed_ms = (time.perf_counter() - start) * 1000.0
            stats.record(elapsed_ms, status, err)

        if think_ms > 0:
            time.sleep(think_ms / 1000.0)


def main() -> None:
    parser = argparse.ArgumentParser(description="Simple HTTP pressure test tool")
    parser.add_argument("--base-url", required=True, help="e.g. http://121.43.140.75")
    parser.add_argument("--concurrency", type=int, default=100)
    parser.add_argument("--duration", type=int, default=60, help="seconds")
    parser.add_argument("--ramp-up", type=int, default=30, help="seconds")
    parser.add_argument("--timeout", type=int, default=10, help="request timeout seconds")
    parser.add_argument("--think-ms", type=int, default=0, help="sleep between requests")
    parser.add_argument("--token", default="", help="Bearer token if needed")
    parser.add_argument("--insecure", action="store_true", help="Disable TLS certificate verification")
    parser.add_argument("--config", default="", help="JSON file for endpoints")
    parser.add_argument("--out", default="", help="save summary json path")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    endpoints = build_endpoints(args.config or None)
    stats = Stats()
    deadline = time.time() + args.duration

    print(f"[start] base_url={base_url}")
    print(f"[start] concurrency={args.concurrency}, duration={args.duration}s, ramp_up={args.ramp_up}s")
    verify_tls = not args.insecure
    print("[start] endpoints:")
    for ep in endpoints:
        print(f"  - {ep.method} {ep.path} ({ep.name})")

    with ThreadPoolExecutor(max_workers=args.concurrency) as ex:
        futures = []
        for i in range(args.concurrency):
            if args.ramp_up > 0:
                delay = (args.ramp_up / args.concurrency) * i
                time.sleep(delay if i == 0 else (args.ramp_up / args.concurrency))
            futures.append(
                ex.submit(
                    worker,
                    i,
                    base_url,
                    deadline,
                    args.timeout,
                    endpoints,
                    args.think_ms,
                    args.token or None,
                    stats,
                    verify_tls,
                )
            )
        for f in futures:
            f.result()

    summary = stats.summary(args.duration)
    print("\n=== Pressure Test Summary ===")
    for k in ["total", "ok", "failed", "error_rate_pct", "rps", "avg_ms", "p50_ms", "p95_ms", "p99_ms", "min_ms", "max_ms"]:
        print(f"{k}: {summary[k]}")
    print(f"status_counter: {summary['status_counter']}")
    if summary["error_counter"]:
        print(f"error_counter: {summary['error_counter']}")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)
        print(f"[saved] {args.out}")


if __name__ == "__main__":
    main()

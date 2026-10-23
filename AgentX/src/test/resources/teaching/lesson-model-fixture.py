#!/usr/bin/env python3
"""OpenAI wire substitute for connected browser evidence, never real-model acceptance.

Only chat/completions is accepted. Inputs, credentials and source content are not logged.
All application authorization, model registry, sources and persistence remain real.
"""
import copy
import json
import pathlib
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 18859
KEY = "lesson-external-fixture-only"
MODEL = "runtime-lesson-fixture"
FIXTURE = json.loads(pathlib.Path(__file__).with_name("derivative-lesson.json").read_text())

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, payload):
        content = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def do_GET(self):
        self.reply(200 if self.path == "/health" else 404,
                   {"fixture": "lesson-openai-wire", "realModelAcceptance": False})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            return self.reply(404, {"error": {"message": "Only chat completions fixture"}})
        if self.headers.get("Authorization") != "Bearer " + KEY:
            return self.reply(401, {"error": {"message": "Fixture key required"}})
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 1000000:
                raise ValueError("Request size")
            request = json.loads(self.rfile.read(size))
            if request.get("stream") or request.get("tools") or request.get("model") not in (MODEL, MODEL + "-invalid"):
                raise ValueError("Unsupported model, streaming or tools")
            messages = request["messages"]
            system = "\n".join(m["content"] for m in messages if m["role"] == "system")
            input_data = json.loads(next(m["content"] for m in messages if m["role"] == "user"))
            if "课时双稿" in system:
                result = copy.deepcopy(FIXTURE)
                actual_id = input_data["sourceData"][0]["id"]
                for group in ("knowledge", "derivations"):
                    for block in result[group]:
                        block["evidence"]["sourceIds"] = [actual_id]
                # 特意保留一个无法解析的表达式，验证浏览器显式错误及未信任命令拒绝。
                result["knowledge"].append({"id": "unsupported-math", "title": "公式错误显示样例", "text": r"$\fixtureUnknown{x}$ <script>window.lessonInjected=true</script> $\href{https://example.invalid/unsafe}{链接}$", "evidence": {"kind": "UNSUPPORTED", "sourceIds": [], "note": "自编自动测试特意输入无效数学，需教师修正"}})
                if request["model"].endswith("-invalid"):
                    result["knowledge"][0]["evidence"]["sourceIds"] = ["unselected-source"]
            elif "教学大纲助手" in system:
                result = {"lessons": [{"title": "导数的概念与几何意义", "objectives": ["理解导数定义", "用导数解释切线斜率"]}]}
            else:
                raise ValueError("Not a supported teaching target")
            content = json.dumps(result, ensure_ascii=False)
            time.sleep(0.15)
            self.reply(200, {"id": "fixture-completion", "object": "chat.completion", "created": int(time.time()), "model": request["model"], "choices": [{"index": 0, "message": {"role": "assistant", "content": content}, "finish_reason": "stop"}], "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}})
            print(json.dumps({"fixture": "lesson-openai-wire", "target": "lesson" if "课时双稿" in system else "outline", "invalid": request["model"].endswith("-invalid")}), flush=True)
        except (ValueError, TypeError, KeyError, StopIteration):
            self.reply(400, {"error": {"message": "Unsupported fixture request"}})

if __name__ == "__main__":
    print(json.dumps({"fixture": "lesson-openai-wire", "port": PORT}), flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()

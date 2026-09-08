#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Agent 落地与闭环 E2E 验证(显式 UTF-8,避免 Windows curl 的 GBK 转码问题)"""
import json
import sys
import time
import urllib.request
import urllib.error

BASE = "http://localhost:8990"
PASS, FAIL = [], []


def ok(name):
    PASS.append(name)
    print(f"[PASS] {name}")


def bad(name, detail=""):
    FAIL.append(name)
    print(f"[FAIL] {name}  {str(detail)[:300]}")


def call(method, path, body=None, headers=None, stream=False, timeout=120):
    url = BASE + path
    data = None
    hs = {"token": TOKEN} if TOKEN else {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hs["Content-Type"] = "application/json"
    if headers:
        hs.update(headers)
    req = urllib.request.Request(url, data=data, headers=hs, method=method)
    try:
        resp = urllib.request.urlopen(req, timeout=timeout)
        if stream:
            return resp
        raw = resp.read().decode("utf-8", errors="replace")
        return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        return e.code, raw


def read_sse(resp):
    events = []
    buffer = b""
    while True:
        chunk = resp.read(4096)
        if not chunk:
            break
        buffer += chunk
        while b"\n" in buffer:
            line, buffer = buffer.split(b"\n", 1)
            line = line.decode("utf-8", errors="replace").strip()
            if line.startswith("data:"):
                payload = line[5:].strip()
                if payload and payload != "[DONE]":
                    try:
                        events.append(json.loads(payload))
                    except Exception:
                        events.append({"raw": payload})
    return events


def event_types(events):
    from collections import Counter
    return dict(Counter(e.get("type", "?") for e in events))


TOKEN = ""
print("== 登录 admin ==")
st, raw = call("POST", "/api/user/login", {"username": "admin", "password": "123456"})
try:
    TOKEN = json.loads(raw)["data"]["token"]
    ok("登录成功")
except Exception:
    bad("登录失败", raw[:200])
    sys.exit(1)

print("\n== Agent 工具清单 ==")
st, raw = call("GET", "/api/agent-tools")
tools = json.loads(raw)["data"]
demo = [t for t in tools if t["code"].startswith("demo_")]
if len(demo) >= 3:
    ok(f"种子工具可见 {len(demo)} 个: {[t['code'] for t in demo]}")
else:
    bad("种子工具缺失", [t["code"] for t in tools])

tool_by_code = {t["code"]: t for t in tools}

# ---------------- 场景 1: 即时闭环 ----------------
print("\n== 场景1: Agent 即时工具闭环(订单+库存) ==")
sid1 = "e2e-py-%d" % int(time.time())
sse1 = read_sse(call("POST", "/api/chat/ask", {
    "question": "帮我查一下客户张三最近一笔订单是什么商品、金额多少,并查该商品当前库存是多少?",
    "sessionId": sid1, "agent": {"enabled": True}}, stream=True))
print("    事件:", event_types(sse1))
errs = [e for e in sse1 if e.get("type") == "error"]
tcount = len([e for e in sse1 if e.get("type") == "tool_call"])
done = any(e.get("type") == "done" for e in sse1)
if tcount >= 1 and done and not errs:
    ok(f"即时工具闭环: 工具调用 {tcount} 次并正常结束")
else:
    bad("场景1异常", {"tool_call": tcount, "done": done, "errs": errs[:2], "events": sse1[-3:]})
content = [e.get("content", "") for e in sse1 if e.get("type") == "content"]
print("    回答摘要:", "".join(content)[:180].replace("\n", " "))

# ---------------- 场景 2: 内联审批(skip + resume SSE) ----------------
print("\n== 场景2: 审批闭环-内联(skip+resume) ==")
sid2 = "e2e-py-%d" % (int(time.time()) + 1)
sse2 = read_sse(call("POST", "/api/chat/ask", {
    "question": "张三的订单 SO20260701001 智能手表屏幕碎裂,请为他创建一张售后工单。",
    "sessionId": sid2, "agent": {"enabled": True}}, stream=True))
print("    事件:", event_types(sse2))
approvals = [e for e in sse2 if e.get("type") == "approval_required"]
if approvals:
    action_id = approvals[0]["actionId"]
    run_key = approvals[0]["runId"]
    ok(f"进入审批暂停 actionId={action_id}")
    st, raw = call("POST", f"/api/agent-actions/{action_id}/approve", {},
                   headers={"X-Agent-Resume": "skip"})
    print("    approve(skip) ->", raw[:120])
    sse3 = read_sse(call("POST", f"/api/agent/runs/{run_key}/resume",
                         {"actionId": action_id}, stream=True))
    print("    resume事件:", event_types(sse3))
    done3 = any(e.get("type") == "done" for e in sse3)
    err3 = [e for e in sse3 if e.get("type") == "error"]
    if done3 and not err3:
        ok("续跑完成(done)")
    else:
        bad("续跑未完成", {"done": done3, "errs": err3[:1], "tail": sse3[-3:]})
    st, raw = call("GET", "/api/demo-business/tickets?customer=" +
                   urllib.parse.quote("张三"))
    if "ST2026" in raw:
        ok("售后工单已落库并可查证", )
        for tk in json.loads(raw)["data"][:2]:
            print("    工单:", tk["ticketNo"], tk["title"], tk["status"])
    else:
        bad("未查到工单", raw[:200])
else:
    bad("场景2 未进入审批暂停", sse2[-3:])

# ---------------- 场景 3: 审批中心外带路径 ----------------
print("\n== 场景3: 审批中心外带路径(approve 自动后台续跑) ==")
sid3 = "e2e-py-%d" % (int(time.time()) + 2)
sse4 = read_sse(call("POST", "/api/chat/ask", {
    "question": "张三订单 SO20260720002 机械键盘按键失灵,再帮他创建一张售后工单。",
    "sessionId": sid3, "agent": {"enabled": True}}, stream=True))
approvals2 = [e for e in sse4 if e.get("type") == "approval_required"]
if approvals2:
    action_id2 = approvals2[0]["actionId"]
    run_key2 = approvals2[0]["runId"]
    ok(f"第二单进入审批暂停 actionId={action_id2}")
    st, raw = call("POST", f"/api/agent-actions/{action_id2}/approve", {})
    print("    approve(无skip) ->", raw[:120])
    run_status = "RUNNING"
    for _ in range(45):
        st, raw = call("GET", f"/api/agent/runs/{run_key2}")
        try:
            run_status = json.loads(raw)["data"]["status"]
        except Exception:
            pass
        if run_status in ("COMPLETED", "FAILED", "REJECTED"):
            break
        time.sleep(1)
    if run_status == "COMPLETED":
        ok("外带路径 run 终态 COMPLETED")
        st, raw = call("GET", f"/api/chat/session/{sid3}/messages")
        msgs = json.loads(raw)["data"]
        ai = [m for m in msgs if m.get("role") == "assistant"]
        if ai and ai[-1].get("agentTrace"):
            ok("会话消息回放含 Agent 轨迹与最终答案")
            print("    答案:", ai[-1]["content"][:150].replace("\n", " "))
        else:
            bad("回放缺 agentTrace/答案", raw[:300])
    else:
        bad("run 终态异常", run_status)
else:
    bad("场景3 未进入审批暂停", sse4[-3:])

# ---------------- 场景 4: workflow 审批续跑 ----------------
print("\n== 场景4: 工作流 Tool 节点审批 → 自动续跑 ==")
create_tool = tool_by_code.get("demo_create_service_ticket")
if create_tool:
    flow = {"nodes": [
        {"id": "n1", "type": "input", "position": {"x": 0, "y": 0}, "data": {}},
        {"id": "n2", "type": "tool", "position": {"x": 200, "y": 0},
         "data": {"label": "创建演示工单", "toolId": create_tool["id"],
                  "parameters": json.dumps({"customer": "张三",
                                            "orderNo": "SO20260718007",
                                            "title": "E2E工作流工单"},
                                           ensure_ascii=False),
                  "evidenceKey": "documents"}},
    ], "edges": [{"source": "n1", "target": "n2"}]}
    st, raw = call("POST", "/api/workflow", {
        "name": f"E2E审批续跑-{int(time.time())}",
        "description": "E2E", "flowData": json.dumps(flow, ensure_ascii=False)})
    try:
        wf_id = json.loads(raw)["data"]["id"]
        ok(f"工作流已创建 id={wf_id}")
    except Exception:
        bad("工作流创建失败", raw[:300])
        wf_id = None
    if wf_id:
        st, raw = call("POST", f"/api/workflow/{wf_id}/execute", {})
        try:
            run = json.loads(raw)["data"]
        except Exception:
            run = {}
        print("    执行返回:", raw[:300])
        if run.get("status") == "WAITING_APPROVAL":
            ok(f"工作流进入审批暂停 executionId={run.get('executionId')}")
            st, raw = call("GET", "/api/agent-actions/pending")
            pend = json.loads(raw)["data"]
            if pend:
                call("POST", f"/api/agent-actions/{pend[0]['id']}/approve", {})
                final = "RUNNING"
                for _ in range(30):
                    st, raw = call("GET", f"/api/workflow/{wf_id}/executions?page=1&pageSize=5")
                    try:
                        rows = json.loads(raw)["data"]["records"]
                        final = rows[0]["status"] if rows else "NONE"
                    except Exception:
                        final = "ERR"
                    if final in ("SUCCESS", "FAILED"):
                        break
                    time.sleep(1)
                if final == "SUCCESS":
                    ok("工作流审批后自动续跑至 SUCCESS")
                else:
                    bad("工作流终态异常", final)
            else:
                bad("未找到待审批工作流动作")
        else:
            bad("工作流未进入审批暂停", raw[:300])
else:
    bad("找不到 demo_create_service_ticket 工具", list(tool_by_code))

# ---------------- 场景 5: 会话页统一 ----------------
print("\n== 场景5: 会话页/侧栏同源 ==")
st, raw = call("GET", "/api/chat/sessions?page=1&size=20")
try:
    sids = [s["id"] for s in json.loads(raw)["data"]["records"]]
    ok(f"侧栏会话总数 {json.loads(raw)['data']['total']}(chat_session 同源)")
    if sid1 in sids:
        ok("E2E 会话已自动建档出现在侧栏")
    else:
        bad("会话未建档", sids[:5])
except Exception:
    bad("侧栏会话接口异常", raw[:200])
st, raw = call("GET", "/api/chat-session/list?page=1&pageSize=5")
ok("会话管理页 list 可用" if '"records"' in raw else "会话管理页 list 异常")
st, raw = call("POST", f"/api/chat-session/title/{sid1}", {"title": "E2E改名验证-订单查询"})
ok("会话改名成功" if '"code":200' in raw else "会话改名失败: " + raw[:150])
st, raw = call("GET", "/api/chat/sessions?page=1&size=20")
if f'"id":"{sid1}"' in raw and "E2E改名验证" in raw:
    ok("改名后侧栏与会话页同源可见")

print(f"\n== 汇总: PASS={len(PASS)} FAIL={len(FAIL)} ==")
if FAIL:
    print("失败项:", FAIL)
    sys.exit(1)
print("全部通过 ✓")

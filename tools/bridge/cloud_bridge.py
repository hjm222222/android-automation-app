#!/usr/bin/env python3
"""
云电脑端 MQTT 安全桥接器
工作方式：
1. 生成一次性会话密钥，保存到文件
2. AI 读取密钥并告知用户
3. 用户本地工具发送密钥验证
4. 验证成功后，接收日志文件，发送分析结果
只传输纯文本数据，不涉及二进制文件。
"""

import paho.mqtt.client as mqtt
import json
import os
import secrets
import string
import time
import sys
import threading
import hashlib

BROKER = "broker.hivemq.com"
PORT = 1883
CLIENT_ID = f"cloud-bridge-{secrets.token_hex(4)}"
WORK_DIR = os.path.dirname(os.path.abspath(__file__))
KEY_FILE = os.path.join(WORK_DIR, "session_key.txt")
VERIFIED_FILE = os.path.join(WORK_DIR, "session_verified.txt")
LOG_FILE = os.path.join(WORK_DIR, "incoming_log.txt")
ANALYSIS_FILE = os.path.join(WORK_DIR, "outgoing_analysis.txt")
LOG_READY_FLAG = os.path.join(WORK_DIR, "_log_ready.txt")

def generate_session_key(length=4, parts=3):
    chars = string.ascii_uppercase + string.digits
    return '-'.join(''.join(secrets.choice(chars) for _ in range(length)) for _ in range(parts))

SESSION_KEY = generate_session_key()
SESSION_ID = hashlib.md5(SESSION_KEY.encode()).hexdigest()[:8]

with open(KEY_FILE, "w") as f:
    f.write(SESSION_KEY)

TOPIC_BASE = f"trae/secure/{SESSION_ID}"
TOPIC_HANDSHAKE = f"{TOPIC_BASE}/handshake"
TOPIC_LOG = f"{TOPIC_BASE}/log"
TOPIC_ANALYSIS = f"{TOPIC_BASE}/analysis"
TOPIC_STATUS = f"{TOPIC_BASE}/status"

verified = False
verified_lock = threading.Lock()
shutdown_flag = False

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[MQTT] \u2713 \u5df2\u8fde\u63a5\u5230 {BROKER}")
        client.subscribe(TOPIC_HANDSHAKE)
        client.subscribe(TOPIC_LOG)
        print(f"[MQTT] \u5df2\u8ba2\u9605\u9a8c\u8bc1\u4e3b\u9898\u548c\u65e5\u5fd7\u4e3b\u9898")
    else:
        print(f"[MQTT] \u2717 \u8fde\u63a5\u5931\u8d25\uff0c\u8fd4\u56de\u7801: {rc}")

def on_message(client, userdata, msg):
    global verified
    topic = msg.topic
    try:
        payload = msg.payload.decode("utf-8")
    except:
        return
    if topic == TOPIC_HANDSHAKE:
        try:
            data = json.loads(payload)
            received_key = data.get("key", "")
            if received_key == SESSION_KEY:
                with verified_lock:
                    verified = True
                with open(VERIFIED_FILE, "w") as f:
                    f.write(f"verified\nsession_id={SESSION_ID}\ntime={time.time()}")
                client.publish(TOPIC_STATUS, json.dumps({"status": "verified", "message": "\u8eab\u4efd\u9a8c\u8bc1\u901a\u8fc7\uff0c\u901a\u8baf\u5df2\u5efa\u7acb"}))
                print(f"\nHandshake \u2713 \u8eab\u4efd\u9a8c\u8bc1\u901a\u8fc7")
            else:
                client.publish(TOPIC_STATUS, json.dumps({"status": "rejected", "message": "\u5bc6\u94a5\u9519\u8bef\uff0c\u62d2\u7edd\u8fde\u63a5"}))
                print(f"Handshake \u2717 \u5bc6\u94a5\u9519\u8bef")
        except json.JSONDecodeError:
            pass
    elif topic == TOPIC_LOG:
        with verified_lock:
            is_verified = verified
        if not is_verified:
            return
        with open(LOG_FILE, "w", encoding="utf-8") as f:
            f.write(payload)
        with open(LOG_READY_FLAG, "w") as f:
            f.write(f"ready\n{time.time()}")
        print(f"\n[Log] \u2713 \u6536\u5230\u65e5\u5fd7 ({len(payload)} \u5b57\u7b26)")

def watch_analysis_file(client):
    global shutdown_flag
    last_mtime = 0
    if os.path.exists(ANALYSIS_FILE):
        last_mtime = os.path.getmtime(ANALYSIS_FILE)
    while not shutdown_flag:
        with verified_lock:
            is_verified = verified
        if is_verified and os.path.exists(ANALYSIS_FILE):
            try:
                mtime = os.path.getmtime(ANALYSIS_FILE)
                if mtime > last_mtime:
                    with open(ANALYSIS_FILE, "r", encoding="utf-8") as f:
                        analysis = f.read().strip()
                    if analysis:
                        client.publish(TOPIC_ANALYSIS, analysis)
                        last_mtime = mtime
                        print(f"\n[Analysis] \u2713 \u5df2\u53d1\u9001\u5206\u6790\u7ed3\u679c")
            except:
                pass
        time.sleep(1)

def main():
    global shutdown_flag
    print(f"\n==================================================")
    print(f"  \u4e91\u7535\u8111\u5b89\u5168\u6865\u63a5\u5668")
    print(f"==================================================")
    print(f"  \u4f1a\u8bdd ID: {SESSION_ID}")
    print(f"  \u901a\u8baf\u5bc6\u94a5: {SESSION_KEY}")
    print(f"  MQTT Broker: {BROKER}:{PORT}")
    print(f"  \u5bc6\u94a5\u6587\u4ef6: {KEY_FILE}")
    print(f"==================================================\n")
    print("\u7b49\u5f85\u672c\u5730\u7535\u8111\u9a8c\u8bc1\u8eab\u4efd...")
    for f in [VERIFIED_FILE, LOG_FILE, LOG_READY_FLAG]:
        if os.path.exists(f):
            os.remove(f)
    client = mqtt.Client(client_id=CLIENT_ID, protocol=mqtt.MQTTv311)
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(BROKER, PORT, 60)
    client.loop_start()
    watcher = threading.Thread(target=watch_analysis_file, args=(client,), daemon=True)
    watcher.start()
    try:
        while not shutdown_flag:
            time.sleep(0.5)
    except KeyboardInterrupt:
        print("\n[Bridge] \u6b63\u5728\u5173\u95ed...")
    finally:
        shutdown_flag = True
        client.loop_stop()
        client.disconnect()
        print("[Bridge] \u5df2\u5173\u95ed")

if __name__ == "__main__":
    main()
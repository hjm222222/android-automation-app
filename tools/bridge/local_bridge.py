#!/usr/bin/env python3
"""
本地电脑端 MQTT 安全桥接器（带 GUI 悬浮窗）
功能：
1. 输入密钥，验证身份
2. 验证成功后，可发送 adb logcat 日志到云电脑
3. 接收云电脑的分析结果
只传输纯文本数据。
"""
import hashlib, json, os, secrets, subprocess, sys, threading, time, tkinter as tk
from tkinter import scrolledtext, messagebox
import paho.mqtt.client as mqtt

BROKER, PORT = "broker.hivemq.com", 1883
CLIENT_ID = f"local-bridge-{secrets.token_hex(4)}"
is_verified = False
session_id = None
mqtt_client = None
root = None

def on_connect(client, userdata, flags, rc):
    update_status("\u5df2\u8fde\u63a5\u5230 MQTT \u670d\u52a1\u5668\uff0c\u7b49\u5f85\u9a8c\u8bc1..." if rc == 0 else f"MQTT \u8fde\u63a5\u5931\u8d25 (\u4ee3\u7801: {rc})")

def on_message(client, userdata, msg):
    global is_verified, session_id
    try:
        payload = msg.payload.decode("utf-8")
    except:
        return
    if msg.topic.endswith("/status"):
        try:
            data = json.loads(payload)
            if data.get("status") == "verified":
                is_verified = True
                update_status("\u2713 \u8eab\u4efd\u9a8c\u8bc1\u901a\u8fc7\uff0c\u901a\u8baf\u5df2\u5efa\u7acb")
                show_connected_ui()
                append_log("[\u7cfb\u7edf] \u9a8c\u8bc1\u901a\u8fc7\uff0c\u4f1a\u8bdd\u5df2\u5efa\u7acb\n")
            elif data.get("status") == "rejected":
                is_verified = False
                update_status("\u2717 \u5bc6\u94a5\u9519\u8bef\uff0c\u62d2\u7edd\u8fde\u63a5")
                append_log("[\u7cfb\u7edf] \u5bc6\u94a5\u9519\u8bef\uff0c\u8bf7\u91cd\u8bd5\n")
                agree_btn.config(state=tk.NORMAL)
                cancel_btn.config(state=tk.NORMAL)
        except:
            pass
    elif msg.topic.endswith("/analysis") and is_verified:
        append_log(f"\n{'='*50}\n[\u4e91\u7535\u8111\u5206\u6790\u7ed3\u679c] {time.strftime('%H:%M:%S')}\n{'='*50}\n{payload}\n{'='*50}\n")

def update_status(text):
    if root:
        root.after(0, lambda: status_label.config(text=f"\u72b6\u6001: {text}"))

def append_log(text):
    if root:
        root.after(0, lambda: log_text.insert(tk.END, text))
        root.after(0, lambda: log_text.see(tk.END))

def show_connected_ui():
    if root:
        root.after(0, lambda: (handshake_frame.pack_forget(), connected_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)))

def send_handshake():
    global session_id, mqtt_client
    key = key_entry.get().strip().upper()
    if not key:
        messagebox.showwarning("\u63d0\u793a", "\u8bf7\u8f93\u5165\u5bc6\u94a5")
        return
    parts = key.split('-')
    if len(parts) != 3 or not all(len(p) == 4 for p in parts):
        messagebox.showwarning("\u63d0\u793a", "\u5bc6\u94a5\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u683c\u5f0f\u5982: A7B3-K9F2-X1M5")
        return
    agree_btn.config(state=tk.DISABLED)
    cancel_btn.config(state=tk.DISABLED)
    update_status("\u6b63\u5728\u9a8c\u8bc1\u8eab\u4efd...")
    def do_handshake():
        global session_id
        try:
            hashed = hashlib.md5(key.encode()).hexdigest()[:8]
            session_id = hashed
            mqtt_client.subscribe(f"trae/secure/{session_id}/status")
            mqtt_client.subscribe(f"trae/secure/{session_id}/analysis")
            mqtt_client.publish(f"trae/secure/{session_id}/handshake", json.dumps({"key": key, "client_id": CLIENT_ID}))
            update_status("\u8eab\u4efd\u9a8c\u8bc1\u8bf7\u6c42\u5df2\u53d1\u9001\uff0c\u7b49\u5f85\u786e\u8ba4...")
            def reenable():
                if not is_verified:
                    agree_btn.config(state=tk.NORMAL)
                    cancel_btn.config(state=tk.NORMAL)
                    update_status("\u9a8c\u8bc1\u8d85\u65f6\uff0c\u8bf7\u91cd\u8bd5")
            threading.Timer(10, reenable).start()
        except Exception as e:
            update_status(f"\u53d1\u9001\u5931\u8d25: {e}")
            agree_btn.config(state=tk.NORMAL)
            cancel_btn.config(state=tk.NORMAL)
    threading.Thread(target=do_handshake, daemon=True).start()

def send_log():
    if not is_verified:
        messagebox.showwarning("\u63d0\u793a", "\u5c1a\u672a\u9a8c\u8bc1\u8eab\u4efd")
        return
    send_log_btn.config(state=tk.DISABLED, text="\u6b63\u5728\u6293\u53d6\u65e5\u5fd7...")
    update_status("\u6b63\u5728\u6293\u53d6 adb \u65e5\u5fd7...")
    def do_send_log():
        try:
            result = subprocess.run(["adb", "logcat", "-v", "time", "-d"], capture_output=True, text=True, timeout=30)
            log_txt = result.stdout
            if not log_txt.strip():
                devices = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=10)
                if "device" not in devices.stdout:
                    append_log("[\u9519\u8bef] \u672a\u68c0\u6d4b\u5230\u5df2\u8fde\u63a5\u7684\u8bbe\u5907\uff0c\u8bf7\u68c0\u67e5 USB \u8fde\u63a5\n")
                else:
                    append_log("[\u63d0\u793a] adb logcat \u8f93\u51fa\u4e3a\u7a7a\n")
                update_status("\u65e0\u65e5\u5fd7\u53ef\u53d1\u9001")
            else:
                mqtt_client.publish(f"trae/secure/{session_id}/log", log_txt)
                append_log(f"[\u7cfb\u7edf] \u2713 \u65e5\u5fd7\u5df2\u53d1\u9001 ({len(log_txt)} \u5b57\u7b26)\n")
                update_status(f"\u65e5\u5fd7\u5df2\u53d1\u9001 ({len(log_txt)} \u5b57\u7b26)")
        except subprocess.TimeoutExpired:
            append_log("[\u9519\u8bef] adb logcat \u8d85\u65f6\n")
        except FileNotFoundError:
            append_log("[\u9519\u8bef] \u672a\u627e\u5230 adb \u547d\u4ee4\uff0c\u8bf7\u786e\u8ba4 ADB \u5df2\u5b89\u88c5\u5e76\u6dfb\u52a0\u5230 PATH\n")
        except Exception as e:
            append_log(f"[\u9519\u8bef] {e}\n")
        finally:
            root.after(0, lambda: send_log_btn.config(state=tk.NORMAL, text="\ud83d\udce4 \u53d1\u9001\u65e5\u5fd7"))
    threading.Thread(target=do_send_log, daemon=True).start()

def on_close():
    global mqtt_client
    if mqtt_client:
        mqtt_client.loop_stop()
        mqtt_client.disconnect()
    root.destroy()
    sys.exit(0)

def create_gui():
    global root, status_label, key_entry, agree_btn, cancel_btn, log_text, send_log_btn, handshake_frame, connected_frame
    root = tk.Tk()
    root.title("\u81ea\u52a8\u5316\u6d4b\u8bd5\u6865\u63a5\u5668")
    root.geometry("520x500")
    root.minsize(400, 400)
    root.attributes('-topmost', True)
    bg, fg, accent, success, err, inp, fbg = "#1e1e2e", "#cdd6f4", "#89b4fa", "#a6e3a1", "#f38ba8", "#313244", "#181825"
    root.configure(bg=bg)
    main_frame = tk.Frame(root, bg=bg)
    main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
    tk.Label(main_frame, text="\ud83e\udd16 \u81ea\u52a8\u5316\u6d4b\u8bd5\u6865\u63a5\u5668", font=("Microsoft YaHei", 14, "bold"), bg=bg, fg=fg).pack(pady=(0, 10))
    status_label = tk.Label(main_frame, text="\u72b6\u6001: \u7b49\u5f85\u8fde\u63a5...", font=("Microsoft YaHei", 10), bg=bg, fg=accent, anchor="w")
    status_label.pack(fill=tk.X, pady=(0, 10))
    handshake_frame = tk.Frame(main_frame, bg=bg)
    tk.Label(handshake_frame, text="\u901a\u8baf\u5bc6\u94a5:", font=("Microsoft YaHei", 10), bg=bg, fg=fg, anchor="w").pack(fill=tk.X)
    key_entry = tk.Entry(handshake_frame, font=("Consolas", 16), bg=inp, fg=accent, insertbackground=fg, relief=tk.FLAT, bd=8, justify="center")
    key_entry.pack(fill=tk.X, pady=(5, 15))
    key_entry.focus()
    btn_f = tk.Frame(handshake_frame, bg=bg)
    btn_f.pack(fill=tk.X)
    cancel_btn = tk.Button(btn_f, text="\u2715 \u53d6\u6d88", font=("Microsoft YaHei", 11), bg=err, fg="#1e1e2e", relief=tk.FLAT, bd=0, activebackground="#e64553", cursor="hand2", command=on_close)
    cancel_btn.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 5), ipady=8)
    agree_btn = tk.Button(btn_f, text="\u2713 \u540c\u610f", font=("Microsoft YaHei", 11, "bold"), bg=success, fg="#1e1e2e", relief=tk.FLAT, bd=0, activebackground="#7ecb7e", cursor="hand2", command=send_handshake)
    agree_btn.pack(side=tk.RIGHT, fill=tk.X, expand=True, padx=(5, 0), ipady=8)
    handshake_frame.pack(fill=tk.X, padx=10, pady=5)
    connected_frame = tk.Frame(main_frame, bg=bg)
    send_log_btn = tk.Button(connected_frame, text="\ud83d\udce4 \u53d1\u9001\u65e5\u5fd7", font=("Microsoft YaHei", 11, "bold"), bg=accent, fg="#1e1e2e", relief=tk.FLAT, bd=0, activebackground="#7aa2f7", cursor="hand2", command=send_log)
    send_log_btn.pack(fill=tk.X, ipady=8, pady=(0, 10))
    lf = tk.Frame(connected_frame, bg=fbg, relief=tk.FLAT, bd=1)
    lf.pack(fill=tk.BOTH, expand=True)
    tk.Label(lf, text="\ud83d\udccb \u901a\u8baf\u65e5\u5fd7", font=("Microsoft YaHei", 9, "bold"), bg=fbg, fg=fg, anchor="w").pack(fill=tk.X, padx=8, pady=(5, 0))
    log_text = scrolledtext.ScrolledText(lf, font=("Consolas", 9), bg=inp, fg=fg, insertbackground=fg, relief=tk.FLAT, bd=0, wrap=tk.WORD, height=15)
    log_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
    append_log("[\u7cfb\u7edf] \u6865\u63a5\u5668\u5df2\u542f\u52a8\uff0c\u8bf7\u8f93\u5165\u5bc6\u94a5\u5e76\u70b9\u51fb\u300c\u540c\u610f\u300d\u9a8c\u8bc1\u8eab\u4efd\n")
    def connect_mqtt():
        global mqtt_client
        try:
            client = mqtt.Client(client_id=CLIENT_ID, protocol=mqtt.MQTTv311)
            client.on_connect = on_connect
            client.on_message = on_message
            client.connect(BROKER, PORT, 60)
            client.loop_start()
            mqtt_client = client
        except Exception as e:
            update_status(f"MQTT \u8fde\u63a5\u5931\u8d25: {e}")
    root.after(500, connect_mqtt)
    root.protocol("WM_DELETE_WINDOW", on_close)
    root.mainloop()

if __name__ == "__main__":
    create_gui()
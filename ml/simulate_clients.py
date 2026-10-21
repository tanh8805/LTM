# Giả lập nhiều client TCP thật để demo: đăng nhập, PRECHECK đạt, gửi FEATURES, nộp bài khi hết giờ.
import argparse
import json
import random
import socket
import threading

ch = random.choice


def normal_values():
    # newProcesses, focusLostCount, focusLostSeconds, newRemoteHosts, violations, answers
    return [ch([0, 0, 0, 1]), ch([0, 0, 1]), ch([0, 0, 1, 2]), ch([0, 0, 1]), 0, random.randint(0, 3)]


def cheater_values():
    return [random.randint(0, 1), random.randint(4, 8), random.randint(15, 25), random.randint(0, 2), 0,
            random.randint(5, 10)]


class SimClient(threading.Thread):
    def __init__(self, host, port, student_id, password, make_values, interval):
        super().__init__(daemon=True)
        self.host, self.port, self.student_id, self.password = host, port, student_id, password
        self.make_values, self.interval = make_values, interval
        self.write_lock = threading.Lock()  # luồng đọc và luồng FEATURES cùng ghi vào socket
        self.done = threading.Event()
        self.questions = []
        self.started = False

    def send(self, msg):
        with self.write_lock:
            self.wfile.write(json.dumps(msg, ensure_ascii=False) + "\n")
            self.wfile.flush()

    def run(self):
        try:
            sock = socket.create_connection((self.host, self.port), timeout=10)
            sock.settimeout(None)
        except OSError as e:
            print(f"[{self.student_id}] không kết nối được: {e}")
            return
        self.rfile = sock.makefile("r", encoding="utf-8")
        self.wfile = sock.makefile("w", encoding="utf-8")
        try:
            self.send({"type": "LOGIN", "studentId": self.student_id, "password": self.password})
            for line in self.rfile:
                if self.handle(json.loads(line)):
                    break
            else:
                print(f"[{self.student_id}] mất kết nối")  # server đóng, không kết nối lại
        except (OSError, ValueError) as e:
            print(f"[{self.student_id}] mất kết nối: {e}")
        finally:
            self.done.set()
            sock.close()

    def handle(self, msg):
        """Trả True khi client này kết thúc."""
        kind = msg.get("type")
        if kind == "LOGIN_OK":
            self.send({"type": "PRECHECK_RESULT", "ok": True, "issues": []})
        elif kind == "LOGIN_FAIL":
            print(f"[{self.student_id}] đăng nhập lỗi: {msg.get('reason')}")
            return True
        elif kind == "EXAM_START" and not self.started:
            self.started = True
            self.questions = msg.get("questions", [])
            threading.Thread(target=self.feature_loop, daemon=True).start()
        elif kind == "EXAM_END":
            answers = {str(q["id"]): random.randrange(len(q["options"])) for q in self.questions}
            self.send({"type": "SUBMIT", "answers": answers})
        elif kind == "RESULT":
            print(f"[{self.student_id}] điểm {msg['score']} ({msg['correct']}/{msg['total']})")
            return True
        return False

    def feature_loop(self):
        window = 1
        while not self.done.wait(self.interval):
            try:
                self.send({"type": "FEATURES", "window": window, "values": self.make_values()})
            except OSError:
                return
            window += 1


def main():
    parser = argparse.ArgumentParser(description="Giả lập client thi để demo")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5000)
    parser.add_argument("--password", default="123456", help="mật khẩu của các tài khoản SIMxx và SIM-X")
    parser.add_argument("--count", type=int, default=5)
    parser.add_argument("--cheater", action="store_true", help="thêm một client có hành vi lệch (SIM-X)")
    parser.add_argument("--interval", type=float, default=20, help="số giây giữa hai FEATURES")
    args = parser.parse_args()

    clients = [SimClient(args.host, args.port, f"SIM{i:02d}", args.password, normal_values, args.interval)
               for i in range(1, args.count + 1)]
    if args.cheater:
        clients.append(SimClient(args.host, args.port, "SIM-X", args.password, cheater_values, args.interval))
    for c in clients:
        c.start()
    try:
        for c in clients:
            c.join()
    except KeyboardInterrupt:
        print("Dừng giả lập")


if __name__ == "__main__":
    main()

"""End-to-end test with real tracker/peer JARs and a LOCAL fake VirusTotal.
No real document, API key or public VirusTotal request is used.
Run after packaging the project. Prefers isolated *-scan-jobs JARs when present.
"""
import email
import hashlib
import http.cookiejar
import http.server
import json
import pathlib
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[5]
RUN = ROOT / "source/target" / ("scan-job-e2e-" + uuid.uuid4().hex[:8])
RUN.mkdir(parents=True)
DATA = b"Harmless fixture for testing the peer-to-tracker upload workflow."
HASH = hashlib.sha256(DATA).hexdigest()
uploaded = threading.Event()
requests = []


def port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class VT(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def respond(self, status, value):
        body = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        requests.append(("GET", self.path))
        assert self.headers.get("x-apikey") == "local-test-key"
        if self.path == "/api/v3/analyses/test-analysis":
            self.respond(200, {"data": {"attributes": {"status": "completed"}}, "meta": {"file_info": {"sha256": HASH}}})
        elif self.path == "/api/v3/files/" + HASH:
            if not uploaded.is_set():
                self.respond(404, {"error": {"code": "NotFoundError"}})
            else:
                self.respond(200, {"data": {"id": HASH, "attributes": {
                    "last_analysis_date": int(time.time()),
                    "last_analysis_stats": {"malicious": 2, "undetected": 68},
                    "last_analysis_results": {"Test engine": {"category": "malicious", "result": "SIMULATED-DETECTION"}}}}})
        else:
            self.respond(404, {})

    def do_POST(self):
        requests.append(("POST", self.path))
        assert self.path == "/api/v3/files"
        body = self.rfile.read(int(self.headers["Content-Length"]))
        msg = email.message_from_bytes(("Content-Type: " + self.headers["Content-Type"] + "\r\nMIME-Version: 1.0\r\n\r\n").encode() + body)
        payload = next(part.get_payload(decode=True) for part in msg.walk() if part.get_filename())
        assert payload == DATA, "Peer/server must send the exact original content"
        uploaded.set()
        self.respond(200, {"data": {"id": "test-analysis"}})


class API:
    def __init__(self, base):
        self.base = base
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, data=None, multipart=False):
        headers = {"X-PeerMesh": "1"}
        if multipart:
            boundary = "fixture" + uuid.uuid4().hex
            data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="fixture.pdf"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode()
                    + DATA + f"\r\n--{boundary}--\r\n".encode())
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        elif data is not None:
            data = json.dumps(data).encode()
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        with self.http.open(req, timeout=12) as response:
            return json.load(response)


def wait(check, timeout=60):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            value = check()
            if value:
                return value
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.5)
    raise AssertionError("Timed out waiting for the workflow")


processes = []
logs = []


def launch(role, args):
    log = open(RUN / (role + "-" + str(len(logs)) + ".log"), "wb")
    logs.append(log)
    jar = ROOT / f"source/{role}/target/{role}-scan-jobs-1.0.0.jar"
    if not jar.exists():
        jar = ROOT / f"source/{role}/target/{role}-1.0.0.jar"
    process = subprocess.Popen(["java", "-Xmx256m", "-Djava.io.tmpdir=" + str(RUN), "-jar", str(jar), *args],
                               cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    processes.append(process)
    return process


fake = http.server.ThreadingHTTPServer(("127.0.0.1", 0), VT)
threading.Thread(target=fake.serve_forever, daemon=True).start()
try:
    tracker_port, peer_port, tcp_port = port(), port(), port()
    tracker_args = [f"--server.port={tracker_port}", "--server.address=127.0.0.1",
                    "--spring.datasource.url=jdbc:h2:file:" + str(RUN / "registry"),
                    "--peermesh.admin.username=admin", "--peermesh.admin.password=local-test-password-123",
                    "--virustotal.api-key=local-test-key",
                    f"--virustotal.base-url=http://127.0.0.1:{fake.server_port}/api/v3"]
    tracker_process = launch("server", tracker_args)
    admin = API(f"http://127.0.0.1:{tracker_port}")
    wait(lambda: admin.call("GET", "/api/health"))
    admin.call("POST", "/api/auth/login", {"username": "admin", "password": "local-test-password-123"})
    admin.call("POST", "/api/auth/register", {"username": "alice", "password": "local-user-password"})
    admin.call("PATCH", "/api/admin/accounts/alice", {"status": "APPROVED"})

    peer_process = launch("client", [f"--server.port={peer_port}", f"--peer.tcp-port={tcp_port}",
                            f"--peer.tracker=http://127.0.0.1:{tracker_port}", "--peer.data=" + str(RUN / "peer")])
    peer = API(f"http://127.0.0.1:{peer_port}")
    wait(lambda: peer.call("GET", "/api/health"))
    login = {"username": "alice", "password": "local-user-password", "name": "Fixture peer", "host": "127.0.0.1"}
    state = peer.call("POST", "/api/auth/login", login)
    admin.call("PATCH", "/api/admin/peers/" + state["peerId"], {"status": "APPROVED"})
    wait(lambda: peer.call("GET", "/api/status").get("peerStatus") == "APPROVED")
    peer.call("POST", "/api/library", multipart=True)

    def file_state():
        return next(f for f in admin.call("GET", "/api/admin/dashboard")["files"] if f["manifest"]["id"] == HASH)

    admin.call("POST", f"/api/admin/files/{HASH}/scan-request")
    wait(lambda: file_state().get("scanStage") == "WAIT_SOURCE")
    assert not uploaded.is_set(), "No file may be disclosed without owner consent"
    print("PASS: requested scan waits without consent; no upload", flush=True)
    admin.call("PATCH", f"/api/admin/files/{HASH}", {"status": "APPROVED"})
    assert file_state()["status"] == "APPROVED"
    print("PASS: waiting scan does not prevent manual approval", flush=True)

    # Restart the tracker with the same H2 file while the request is pending.
    tracker_process.terminate()
    tracker_process.wait(timeout=20)
    tracker_process = launch("server", tracker_args)
    wait(lambda: admin.call("GET", "/api/health"))
    admin = API(f"http://127.0.0.1:{tracker_port}")
    admin.call("POST", "/api/auth/login", {"username": "admin", "password": "local-test-password-123"})
    assert file_state()["scanStage"] == "WAIT_SOURCE"
    print("PASS: pending request survives a real tracker restart", flush=True)

    peer.call("POST", "/api/auth/login", login)
    wait(lambda: peer.call("GET", "/api/status").get("peerStatus") == "APPROVED")
    peer.call("PATCH", f"/api/library/{HASH}/scan-consent", {"enabled": True})
    wait(lambda: uploaded.is_set(), timeout=80)
    print("PASS: real peer uploads the original file automatically after opt-in", flush=True)
    wait(lambda: file_state().get("scanStage") == "DONE", timeout=90)
    result = file_state()
    assert result["status"] == "BLOCKED"
    assert result["malware"]["malicious"] == 2
    assert result["malware"]["engines"][0]["result"] == "SIMULATED-DETECTION"
    assert sum(method == "POST" for method, _ in requests) == 1
    print("PASS: report collected automatically, detection blocks file, no duplicate upload", flush=True)
    print("PASS: full workflow used only local fixture data and the local fake API", flush=True)
    print("Logs:", RUN, flush=True)
finally:
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
    for log in logs:
        log.close()
    fake.shutdown()

"""Real-process LAN simulation: tracker + three peers, using only Python's standard library.
Run from the repository root after `mvn -f source/pom.xml package`.
Generated files and logs stay under source/target; no existing profile is touched.
"""
import argparse
import base64
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[5]
SOURCE = ROOT / "source"


class Api:
    def __init__(self, port):
        self.base = f"http://127.0.0.1:{port}"
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def call(self, path, method="GET", body=None, raw=None, content_type=None):
        headers = {"X-PeerMesh": "1"}
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        if data is not None:
            headers["Content-Type"] = content_type or "application/json"
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        with self.opener.open(request, timeout=45) as response:
            content = response.read()
            return json.loads(content) if "json" in response.headers.get("Content-Type", "") else content


class SocketStream:
    """Small RFC 6455 test client: validates upgrade, handles ping/pong and masked client frames."""
    def __init__(self, api, path):
        host = urllib.parse.urlparse(api.base)
        self.socket = socket.create_connection((host.hostname, host.port), timeout=5)
        self.socket.settimeout(8)
        self.buffer = b""
        key = base64.b64encode(os.urandom(16)).decode()
        request = urllib.request.Request(api.base + path)
        api.cookies.add_cookie_header(request)
        cookie = request.get_header("Cookie", "")
        headers = (f"GET {path} HTTP/1.1\r\nHost: {host.netloc}\r\nUpgrade: websocket\r\n"
                   f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
                   f"Origin: {api.base}\r\nCookie: {cookie}\r\n\r\n")
        self.socket.sendall(headers.encode())
        while b"\r\n\r\n" not in self.buffer:
            chunk = self.socket.recv(4096)
            if not chunk:
                raise AssertionError("WebSocket upgrade closed unexpectedly")
            self.buffer += chunk
        header, self.buffer = self.buffer.split(b"\r\n\r\n", 1)
        assert b" 101 " in header.split(b"\r\n", 1)[0], header
        expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest())
        assert expected in header, "Invalid Sec-WebSocket-Accept"

    def read(self, count):
        while len(self.buffer) < count:
            chunk = self.socket.recv(max(4096, count-len(self.buffer)))
            if not chunk:
                raise AssertionError("WebSocket disconnected before snapshot")
            self.buffer += chunk
        data, self.buffer = self.buffer[:count], self.buffer[count:]
        return data

    def send(self, opcode, payload):
        mask = os.urandom(4)
        size = len(payload)
        header = bytes([0x80 | opcode])
        if size < 126:
            header += bytes([0x80 | size])
        elif size < 65536:
            header += b"\xfe" + struct.pack(">H", size)
        else:
            header += b"\xff" + struct.pack(">Q", size)
        self.socket.sendall(header + mask + bytes(value ^ mask[i % 4] for i, value in enumerate(payload)))

    def snapshot(self):
        text = bytearray()
        while True:
            first, second = self.read(2)
            assert not second & 0x80, "Server frames must not be masked"
            opcode, size = first & 0x0f, second & 0x7f
            if size == 126:
                size = struct.unpack(">H", self.read(2))[0]
            elif size == 127:
                size = struct.unpack(">Q", self.read(8))[0]
            assert size <= 1024*1024, "Unexpectedly large test frame"
            payload = self.read(size)
            if opcode == 9:
                self.send(10, payload)
                continue
            if opcode == 8:
                raise AssertionError("Server closed WebSocket: " + repr(payload))
            if opcode in (0, 1):
                text.extend(payload)
                if first & 0x80:
                    result = json.loads(text)
                    assert result["type"] == "snapshot"
                    return result["data"]

    def __enter__(self):
        return self

    def __exit__(self, *exception):
        try:
            self.send(8, struct.pack(">H", 1000))
        finally:
            self.socket.close()


def wait_for(probe, description, timeout=60):
    end = time.monotonic() + timeout
    last = None
    while time.monotonic() < end:
        try:
            value = probe()
            if value:
                return value
        except Exception as exc:
            last = exc
        time.sleep(0.25)
    raise AssertionError(f"Timeout: {description}. Last error: {last}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18080, help="Tracker HTTP port; peers use the next 3 ports")
    parser.add_argument("--tcp-port", type=int, default=19001)
    parser.add_argument("--hold", type=int, default=0, help="Keep test processes alive for UI inspection, in seconds")
    args = parser.parse_args()
    for port in [args.port, args.port+1, args.port+2, args.port+3, args.tcp_port, args.tcp_port+1, args.tcp_port+2]:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", port))
    run = SOURCE / "target" / ("e2e-" + uuid.uuid4().hex[:8])
    run.mkdir(parents=True)
    temp = run / "temp"
    temp.mkdir()
    processes = {}
    logs = []
    password = "e2e-password-2026"
    admin_password = "e2e-admin-password-2026"

    def start(name, module, *config):
        log = open(run / (name + ".log"), "ab")
        logs.append(log)
        command = ["java", "-Xmx192m", f"-Djava.io.tmpdir={temp}", "-jar",
                   str(SOURCE / module / "target" / f"{module}-1.0.0.jar"), *config]
        if module == 'server':
            command.append('--peermesh.admin.username=admin')
        processes[name] = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                           creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))

    def start_peer(i):
        start(f"peer-{i}", "client", f"--server.port={args.port+i}", f"--peer.tcp-port={args.tcp_port+i-1}",
              f"--peer.data={run / ('peer-' + str(i))}", f"--peer.tracker=http://127.0.0.1:{args.port}",
              "--peer.upload-delay-ms=200")

    def login_peer(api, i):
        return api.call("/api/auth/login", "POST", {"username": f"peeruser{i}", "password": password,
                                                     "name": f"Peer {chr(64+i)}", "host": "127.0.0.1"})

    def moderate(kind, id_, status="APPROVED"):
        return admin.call(f"/api/admin/{kind}/{id_}", "PATCH", {"status": status})

    try:
        start("tracker", "server", f"--server.port={args.port}", f"--spring.datasource.url=jdbc:h2:file:{run / 'tracker' / 'registry'}",
              f"--peermesh.admin.password={admin_password}")
        admin = Api(args.port)
        wait_for(lambda: admin.call("/api/health"), "tracker startup")
        admin.call("/api/auth/login", "POST", {"username": "admin", "password": admin_password})
        for i in range(1,4):
            admin.call("/api/auth/register", "POST", {"username": f"peeruser{i}", "password": password})
            if i != 3:
                moderate("accounts", f"peeruser{i}")
            start_peer(i)
        apis = [Api(args.port+i) for i in range(1,4)]
        for api in apis:
            wait_for(lambda api=api: api.call("/api/health"), "peer startup")
        for i, api in enumerate(apis,1):
            login_peer(api,i)
        assert apis[2].call("/api/status")["user"]["status"] == "PENDING"
        moderate("accounts", "peeruser3")
        wait_for(lambda: len(admin.call("/api/admin/dashboard")["peers"]) == 3, "auto-registration after approval")
        for peer in admin.call("/api/admin/dashboard")["peers"]:
            moderate("peers",peer["id"])
        for api in apis:
            wait_for(lambda api=api: api.call("/api/status")["peerStatus"] == "APPROVED", "peer approval heartbeat")
        print("PASS: account and device approvals, including automatic pending-account refresh", flush=True)

        payload = bytes(range(256)) * (32*1024*1024//256)
        file_id = hashlib.sha256(payload).hexdigest()
        boundary = "peermesh-"+uuid.uuid4().hex
        upload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="network-demo.bin"\r\n'
                  'Content-Type: application/octet-stream\r\n\r\n').encode()+payload+f"\r\n--{boundary}--\r\n".encode()
        for api in apis[:2]:
            result = api.call("/api/library", "POST", raw=upload, content_type="multipart/form-data; boundary="+boundary)
            assert result["manifest"]["id"] == file_id
        assert not apis[2].call("/api/catalog")
        moderate("files",file_id)
        assert len(apis[2].call(f"/api/files/{file_id}/sources")) == 2
        seeders = {s['id']: s for s in apis[2].call(f"/api/files/{file_id}/sources")}
        # A raw TCP request without a valid tracker ticket must be rejected.
        with socket.create_connection(("127.0.0.1",args.tcp_port),timeout=5) as sock:
            sock.settimeout(10)
            header=json.dumps({"ticket":"invalid-ticket","fileId":file_id,"chunk":0}).encode()
            sock.sendall(struct.pack(">II",0x504D3031,len(header))+header)
            assert sock.recv(1) == b"\x01"
        print("PASS: identical content from two seeders, catalog moderation, unauthorized TCP rejected", flush=True)
        # Demo lab: authentication, real TCP comparison, bounded samples and failed benchmark.
        for path, body in [("/api/lab/fault", {"mode":"CORRUPT","delayMs":0,"seconds":1}),
                           ("/api/lab/benchmark", {"fileId":file_id})]:
            try:
                Api(args.port+3).call(path,"POST",body)
                raise AssertionError("Anonymous lab mutation accepted")
            except urllib.error.HTTPError as error:
                assert error.code == 401
        apis[2].call("/api/lab/benchmark","POST",{"fileId":file_id})
        wait_for(lambda: apis[2].call('/api/status')['lab']['links'], 'live benchmark TCP edges')
        try:
            apis[2].call('/api/downloads','POST',{'fileId':file_id})
            raise AssertionError('Download allowed during benchmark')
        except urllib.error.HTTPError as error:
            assert error.code == 400
        measurement = wait_for(lambda: (t if (t:=apis[2].call('/api/status')['lab']['benchmark'])['status'] != 'RUNNING' else None), 'TCP comparison', 90)
        assert measurement['status'] == 'COMPLETED', measurement
        first, second = measurement['results']
        assert first['bytes'] == second['bytes'] == 16*1024*1024
        assert first['chunks'] == second['chunks'] == 64
        assert first['workers'] == second['workers'] == 4
        assert len(first['sourceBytes']) == 1 and len(second['sourceBytes']) == 2
        assert first['speed'] > 0 and second['speed'] > 0
        status=apis[2].call('/api/status')
        assert not status['library'] and not status['jobs']
        assert status['lab']['samples'] and len(status['lab']['samples']) <= 120
        assert any(sample['receive'] > 0 for sample in status['lab']['samples'])
        apis[0].call('/api/lab/fault','POST',{'mode':'CORRUPT','delayMs':0,'seconds':30})
        apis[2].call('/api/lab/benchmark','POST',{'fileId':file_id})
        failed=wait_for(lambda: (t if (t:=apis[2].call('/api/status')['lab']['benchmark'])['status'] != 'RUNNING' else None), 'benchmark rejects corrupt bytes', 45)
        assert failed['status']=='ERROR' and 'SHA-256' in failed['error'], failed
        print('PASS: authenticated lab, live edges, real equal-work TCP comparison, speed samples, corrupt benchmark fails',flush=True)
        # Keep corruption active during the first download: receiver must fail over to B.
        job_id = apis[2].call("/api/downloads", "POST", {"fileId":file_id})["id"]

        with SocketStream(apis[2], "/ws/peer") as stream:
            def live_progress():
                live = stream.snapshot()
                if any('SHA-256' in event for event in live.get('events', [])):
                    apis[0].call('/api/lab/fault','POST',{'mode':'NORMAL','delayMs':0,'seconds':1})
                assert "network" in live and "downloadWorkers" in live["network"]
                return next((j for j in live["jobs"] if j["id"] == job_id
                             and j["bytes"] >= 2*1024*1024 and len(j["sourceBytes"]) == 2), None)
            wait_for(live_progress, "verified multi-source progress pushed through WebSocket", 20)
        print("PASS: authenticated WebSocket pushes actual multi-source progress and thread/socket metrics", flush=True)
        assert any('SHA-256' in event for event in apis[2].call('/api/status')['events'])
        assert apis[0].call('/api/status')['network']['upload']['injected'] > 0
        apis[0].call('/api/lab/fault','POST',{'mode':'DISCONNECT','delayMs':0,'seconds':2})
        wait_for(lambda: apis[0].call('/api/status')['lab']['fault']['mode']=='NORMAL', 'fault expiration', 10)
        print('PASS: injected corrupt source rejected, download fails over, fault expires automatically',flush=True)


        def job():
            return next(j for j in apis[2].call("/api/status")["jobs"] if j["id"]==job_id)

        wait_for(lambda: job()["bytes"] >= 2*1024*1024 and len(job()["sourceBytes"])==2, "multi-source progress")
        apis[2].call(f"/api/downloads/{job_id}/pause","POST",{})
        checkpoint=job()["bytes"]
        assert 0 < checkpoint < len(payload)
        processes["peer-3"].kill()
        processes["peer-3"].wait(timeout=20)
        # Corrupt one checkpointed part to prove startup verification discards it.
        parts=list((run / "peer-3" / "chunks" / file_id).glob("*.part"))
        assert parts
        corrupt=parts[0]
        corrupt.write_bytes(b"corrupt")
        start_peer(3)
        apis[2]=Api(args.port+3)
        wait_for(lambda: apis[2].call("/api/health"),"restarted peer")
        login_peer(apis[2],3)
        restored=job()
        assert restored["status"]=="PAUSED" and restored["bytes"]==checkpoint-256*1024
        assert corrupt.stem not in apis[2].call(f'/api/downloads/{job_id}/chunks')
        print("PASS: forced process restart preserves progress; corrupted chunk is excluded", flush=True)
        apis[2].call(f"/api/downloads/{job_id}/resume","POST",{})
        wait_for(lambda: job()["bytes"]>checkpoint,"resume progress")
        processes["peer-1"].kill()
        processes["peer-1"].wait(timeout=20)
        completed=wait_for(lambda: (j if (j:=job())["status"]=="COMPLETED" else None),"failover and completion",120)
        downloaded=apis[2].call(f"/api/library/{file_id}/content")
        assert hashlib.sha256(downloaded).hexdigest()==file_id and downloaded==payload
        assert completed["bytes"]==len(payload) and len(completed["sourceBytes"])==2
        history = apis[2].call(f'/api/downloads/{job_id}/chunks')
        assert set(history) == {str(i) for i in range(len(payload)//(256*1024))}
        assert {s['peerId'] for s in history.values()} == set(seeders)
        for source in history.values():
            original = seeders[source['peerId']]
            assert all(source[key] == original[key] for key in ['name', 'owner', 'host', 'port'])
            assert source['receivedAt'] > 0
        processes['peer-3'].terminate(); processes['peer-3'].wait(timeout=30)
        start_peer(3)
        apis[2] = Api(args.port+3)
        wait_for(lambda: apis[2].call('/api/health'), 'completed peer restart')
        login_peer(apis[2],3)
        assert job()['status'] == 'COMPLETED' and apis[2].call(f'/api/downloads/{job_id}/chunks') == history
        print('PASS: per-chunk peer/account/address history survives completed peer restart', flush=True)
        print("PASS: source killed mid-download; remaining peer completes the file with matching SHA-256",flush=True)
        wait_for(lambda: any(p["id"]==apis[2].call('/api/status')["peerId"] for p in apis[1].call(f'/api/files/{file_id}/sources')),
                 "completed downloader becomes seeder")
        wait_for(lambda: any(t["status"]=="COMPLETED" for t in admin.call('/api/admin/dashboard')["transfers"]),"tracker progress report")
        print("PASS: automatic reseeding and tracker reporting",flush=True)
        # Restart tracker to prove registry data is durable, independent of its sessions.
        processes["tracker"].terminate();processes["tracker"].wait(timeout=30)
        start("tracker", "server", f"--server.port={args.port}", f"--spring.datasource.url=jdbc:h2:file:{run / 'tracker' / 'registry'}")
        wait_for(lambda: admin.call('/api/health'),"tracker restart")
        admin=Api(args.port)
        admin.call('/api/auth/login','POST',{"username":"admin","password":admin_password})
        persisted=admin.call('/api/admin/dashboard')
        assert len(persisted['accounts'])==4 and len(persisted['peers'])==3 and len(persisted['files'])==1
        print("PASS: H2 account, peer, file and transfer persistence after tracker restart",flush=True)
        # Restore test sessions for optional visual inspection.
        login_peer(apis[1],2);login_peer(apis[2],3)
        result={"result":"PASS","size":len(payload),"sha256":file_id,"sources":completed["sourceBytes"],
                "checkpointBeforeRestart":checkpoint,"bytesAfterCorruptionCheck":restored["bytes"],
                "websocketPeerPush":True,"labComparison":measurement,"labFaultRecovery":True,"logs":str(run)}
        (run/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
        print(json.dumps(result,indent=2),flush=True)
        if args.hold:
            # Restore A as well, so UI benchmark has two remote seeders after the failover scenario.
            start_peer(1)
            apis[0]=Api(args.port+1)
            wait_for(lambda: apis[0].call('/api/health'), 'source A restored for UI')
            login_peer(apis[0],1)
            print(f"UI inspection: {admin.base}, {apis[1].base}, {apis[2].base}; holding {args.hold}s",flush=True)
            end=time.monotonic()+args.hold
            while time.monotonic()<end:
                time.sleep(min(1,end-time.monotonic()))
    finally:
        for process in processes.values():
            if process.poll() is None:
                process.terminate()
        for process in processes.values():
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill();process.wait()
        for log in logs:
            log.close()
        print("Test logs:",run,flush=True)


if __name__ == "__main__":
    main()

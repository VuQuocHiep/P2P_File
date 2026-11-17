"""Real tracker + three peers: live limits, pause, multi-file transfers, hash and restart.

Run after packaging: python source/client/src/test/resources/bandwidth-workflow.py
Only standard-library modules are required; generated data stays under source/target.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import urllib.error
import uuid

from e2e import Api, SOURCE, ROOT, wait_for


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=28180)
    parser.add_argument('--tcp-port', type=int, default=29101)
    args = parser.parse_args()
    for port in [args.port + i for i in range(4)] + [args.tcp_port + i for i in range(3)]:
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
    run = SOURCE / 'target' / ('bandwidth-' + uuid.uuid4().hex[:8])
    run.mkdir(parents=True)
    java = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    executable = str(java) if java.is_file() else shutil.which('java')
    processes, logs = {}, []
    admin = Api(args.port)
    peers = [Api(args.port + i) for i in range(1, 4)]
    password = 'bandwidth-test-password-2026'

    def start(name, module, *config):
        log = open(run / (name + '.log'), 'ab')
        logs.append(log)
        command = [executable, '-Xmx192m', '-jar', str(SOURCE / module / 'target' / f'{module}-1.0.0.jar'),
                   '--logging.level.root=WARN', *config]
        processes[name] = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                          creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))

    def start_peer(i):
        start(f'peer-{i}', 'client', f'--server.port={args.port+i}', f'--peer.tcp-port={args.tcp_port+i-1}',
              f'--peer.data={run / ("peer-"+str(i))}', f'--peer.tracker=http://127.0.0.1:{args.port}')
        wait_for(lambda: peers[i-1].call('/api/health'), f'peer {i} startup')

    def login(i):
        return peers[i-1].call('/api/auth/login', 'POST', {
            'username': f'bandwidth{i}', 'password': password, 'name': f'Peer {i}', 'host': '127.0.0.1'})

    def limits(peer, upload, download):
        return peer.call('/api/settings/bandwidth', 'PATCH', {'uploadLimitKiB': upload, 'downloadLimitKiB': download})

    def status():
        return peers[2].call('/api/status')

    def stop(name):
        process = processes[name]
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=20)

    try:
        start('tracker', 'server', f'--server.port={args.port}',
              f'--spring.datasource.url=jdbc:h2:file:{run / "tracker" / "database"}',
              '--peermesh.admin.username=admin', f'--peermesh.admin.password={password}')
        wait_for(lambda: admin.call('/api/health'), 'tracker startup')
        admin.call('/api/auth/login', 'POST', {'username': 'admin', 'password': password})
        for i in range(1, 4):
            admin.call('/api/auth/register', 'POST', {'username': f'bandwidth{i}', 'password': password})
            admin.call(f'/api/admin/accounts/bandwidth{i}', 'PATCH', {'status': 'APPROVED'})
            start_peer(i)
            peer_id = login(i)['peerId']
            admin.call(f'/api/admin/peers/{peer_id}', 'PATCH', {'status': 'APPROVED'})
        wait_for(lambda: all(peer.call('/api/status')['peerStatus'] == 'APPROVED' for peer in peers),
                 'all peers receive their approvals')

        payloads = [bytes(range(256)) * 2048, bytes(reversed(range(256))) * 2048]
        ids = [hashlib.sha256(data).hexdigest() for data in payloads]
        for index, payload in enumerate(payloads):
            boundary = 'peermesh-' + uuid.uuid4().hex
            multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="sample-{index}.bin"\r\n'
                         'Content-Type: application/octet-stream\r\n\r\n').encode() + payload + f'\r\n--{boundary}--\r\n'.encode()
            for peer in peers[:2]:
                peer.call('/api/library', 'POST', raw=multipart, content_type='multipart/form-data; boundary=' + boundary)
            admin.call(f'/api/admin/files/{ids[index]}', 'PATCH', {'status': 'APPROVED'})
        for peer in peers[:2]:
            limits(peer, 32, 0)
        limits(peers[2], 0, 1)
        jobs = [peers[2].call('/api/downloads', 'POST', {'fileId': file_id})['id'] for file_id in ids]
        wait_for(lambda: status()['network']['bandwidth']['downloadBytesPerSecond'] > 0, 'low-rate live counters')
        peers[2].call(f'/api/downloads/{jobs[0]}/pause', 'POST', {})
        wait_for(lambda: status()['network']['activeDownloads'] == 1, 'pause frees throttled workers', 5)
        assert next(job for job in status()['jobs'] if job['id'] == jobs[0])['status'] == 'PAUSED'
        peers[2].call(f'/api/downloads/{jobs[0]}/resume', 'POST', {})
        limits(peers[2], 0, 64)
        sample = wait_for(lambda: (value if (value := status()['network']['bandwidth']['downloadBytesPerSecond']) >= 16 * 1024 else None),
                          'changed live download limit', 15)
        assert sample <= 96 * 1024, f'Aggregate rate unexpectedly exceeds the limit: {sample}'
        print('PASS: two active files share limits; raw counters update; pause/resume and live changes work', flush=True)

        for peer in peers:
            limits(peer, 0, 0)
        wait_for(lambda: all(job['status'] == 'COMPLETED' for job in status()['jobs']), 'unlimited completion', 30)
        for file_id, payload in zip(ids, payloads):
            assert peers[2].call(f'/api/library/{file_id}/content') == payload
        print('PASS: removing limits finishes both files with unchanged content and valid SHA-256', flush=True)

        limits(peers[2], 128, 64)
        stop('peer-3')
        start_peer(3)
        login(3)
        restored = peers[2].call('/api/settings/bandwidth')
        assert restored['uploadLimitKiB'] == 128 and restored['downloadLimitKiB'] == 64
        assert all(job['status'] == 'COMPLETED' for job in status()['jobs'])
        peers[2].call('/api/auth/logout', 'POST', {})
        try:
            peers[2].call('/api/settings/bandwidth')
            raise AssertionError('Anonymous settings must be rejected')
        except urllib.error.HTTPError as error:
            assert error.code == 401
        result = {'result': 'PASS', 'files': 2, 'totalBytes': sum(map(len, payloads)),
                  'sampleDownloadBytesPerSecond': sample, 'restoredLimits': restored}
        (run / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
        print('PASS: settings and completed downloads survive restart; settings require authentication', flush=True)
        print('Test logs:', run, flush=True)
    finally:
        for name in reversed(processes):
            stop(name)
        for log in logs:
            log.close()


if __name__ == '__main__':
    main()

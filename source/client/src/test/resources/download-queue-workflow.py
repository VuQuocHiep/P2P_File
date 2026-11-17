"""Real tracker + two peers: queue, priorities, pause/resume, restart and verified content.

Run after packaging: python source/client/src/test/resources/download-queue-workflow.py
Generated profiles and logs stay under source/target; only the standard library is used.
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
    parser.add_argument('--port', type=int, default=28380)
    parser.add_argument('--tcp-port', type=int, default=29301)
    args = parser.parse_args()
    for port in [args.port + i for i in range(3)] + [args.tcp_port + i for i in range(2)]:
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
    run = SOURCE / 'target' / ('download-queue-' + uuid.uuid4().hex[:8])
    run.mkdir(parents=True)
    java = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    executable = str(java) if java.is_file() else shutil.which('java')
    processes, logs = {}, []
    admin, seed, receiver = [Api(args.port + i) for i in range(3)]
    password = 'queue-test-password-2026'

    def start(name, module, *config):
        log = open(run / (name + '.log'), 'ab')
        logs.append(log)
        command = [executable, '-Xmx192m', '-jar', str(SOURCE / module / 'target' / f'{module}-1.0.0.jar'),
                   '--logging.level.root=WARN', *config]
        processes[name] = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                          creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))

    def stop(name):
        process = processes[name]
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=20)

    def start_peer(i):
        start(f'peer-{i}', 'client', f'--server.port={args.port+i}', f'--peer.tcp-port={args.tcp_port+i-1}',
              f'--peer.data={run / ("peer-"+str(i))}', f'--peer.tracker=http://127.0.0.1:{args.port}')
        wait_for(lambda: Api(args.port+i).call('/api/health'), f'peer {i} startup')

    def login(peer, i):
        return peer.call('/api/auth/login', 'POST', {
            'username': f'queueuser{i}', 'password': password, 'name': f'Queue peer {i}', 'host': '127.0.0.1'})

    def status():
        value = receiver.call('/api/status')
        assert value['network']['activeDownloads'] <= 2, value['network']
        return value

    def jobs():
        return {job['id']: job for job in status()['jobs']}

    def queue():
        return [job['id'] for job in sorted(jobs().values(), key=lambda job: job['queuePosition'])
                if job['status'] == 'QUEUED']

    def action(job_id, action):
        receiver.call(f'/api/downloads/{job_id}/{action}', 'POST', {})

    def priority(job_id, priority):
        receiver.call(f'/api/downloads/{job_id}/priority', 'PATCH', {'priority': priority})

    try:
        start('tracker', 'server', f'--server.port={args.port}',
              f'--spring.datasource.url=jdbc:h2:file:{run / "tracker" / "database"}',
              '--peermesh.admin.username=admin', f'--peermesh.admin.password={password}')
        wait_for(lambda: admin.call('/api/health'), 'tracker startup')
        admin.call('/api/auth/login', 'POST', {'username': 'admin', 'password': password})
        for i, peer in enumerate([seed, receiver], 1):
            admin.call('/api/auth/register', 'POST', {'username': f'queueuser{i}', 'password': password})
            admin.call(f'/api/admin/accounts/queueuser{i}', 'PATCH', {'status': 'APPROVED'})
            start_peer(i)
            peer_id = login(peer, i)['peerId']
            admin.call(f'/api/admin/peers/{peer_id}', 'PATCH', {'status': 'APPROVED'})
        wait_for(lambda: all(peer.call('/api/status')['peerStatus'] == 'APPROVED' for peer in [seed, receiver]),
                 'both peers receive their approvals')

        payloads = [bytes([i]) * (128 * 1024) for i in range(1, 6)]
        ids = [hashlib.sha256(payload).hexdigest() for payload in payloads]
        for i, payload in enumerate(payloads):
            boundary = 'peermesh-' + uuid.uuid4().hex
            multipart = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="queue-{i}.bin"\r\n'
                         'Content-Type: application/octet-stream\r\n\r\n').encode() + payload + f'\r\n--{boundary}--\r\n'.encode()
            seed.call('/api/library', 'POST', raw=multipart, content_type='multipart/form-data; boundary=' + boundary)
            admin.call(f'/api/admin/files/{ids[i]}', 'PATCH', {'status': 'APPROVED'})
        receiver.call('/api/settings/bandwidth', 'PATCH', {'uploadLimitKiB': 0, 'downloadLimitKiB': 1})
        job_ids = [receiver.call('/api/downloads', 'POST', {'fileId': file_id})['id'] for file_id in ids]
        assert status()['network']['activeDownloads'] == 2
        assert queue() == job_ids[2:]
        priority(job_ids[2], 'LOW')
        priority(job_ids[4], 'HIGH')
        assert queue() == [job_ids[4], job_ids[3], job_ids[2]]
        assert jobs()[job_ids[0]]['status'] in ['DOWNLOADING', 'WAITING']
        wait_for(lambda: any(transfer['id'] == job_ids[4] and transfer['status'] == 'QUEUED'
                             for transfer in admin.call('/api/admin/dashboard')['transfers']),
                 'tracker records queued transfers')
        print('PASS: five downloads accepted, at most two active; priority reorders waiting files only', flush=True)

        action(job_ids[3], 'pause')
        assert queue() == [job_ids[4], job_ids[2]]
        action(job_ids[0], 'pause')
        wait_for(lambda: jobs()[job_ids[4]]['status'] in ['DOWNLOADING', 'WAITING'], 'high priority starts next', 10)
        assert queue() == [job_ids[2]]
        action(job_ids[1], 'pause')
        wait_for(lambda: jobs()[job_ids[2]]['status'] in ['DOWNLOADING', 'WAITING'], 'remaining queued file starts', 10)
        action(job_ids[3], 'resume')
        action(job_ids[0], 'resume')
        assert queue() == [job_ids[3], job_ids[0]]
        assert receiver.call('/api/downloads', 'POST', {'fileId': ids[0]})['id'] == job_ids[0]
        assert queue() == [job_ids[3], job_ids[0]]
        priority(job_ids[0], 'HIGH')
        assert queue() == [job_ids[0], job_ids[3]]
        print('PASS: pausing queued files removes them; pausing active files starts the next; resume preserves FIFO', flush=True)

        stop('peer-2')
        receiver = Api(args.port + 2)
        start_peer(2)
        restored = login(receiver, 2)
        assert restored['network']['bandwidth']['downloadLimitKiB'] == 1
        restored_jobs = jobs()
        assert restored_jobs[job_ids[0]]['priority'] == 'HIGH'
        assert restored_jobs[job_ids[2]]['priority'] == 'LOW'
        assert restored_jobs[job_ids[2]]['status'] == 'PAUSED'
        assert restored_jobs[job_ids[4]]['status'] == 'PAUSED'
        wait_for(lambda: all(jobs()[job_id]['status'] in ['DOWNLOADING', 'WAITING']
                             for job_id in [job_ids[0], job_ids[3]]), 'restored queue starts after login')
        receiver.call('/api/auth/logout', 'POST', {})
        login(receiver, 2)
        wait_for(lambda: status()['network']['activeDownloads'] == 0, 'logout stops all jobs')
        assert all(job['status'] == 'PAUSED' for job in jobs().values())
        print('PASS: queue and priorities survive restart; existing active jobs restore paused; logout pauses all', flush=True)

        receiver.call('/api/settings/bandwidth', 'PATCH', {'uploadLimitKiB': 0, 'downloadLimitKiB': 64})
        for job_id in job_ids:
            action(job_id, 'resume')
        assert queue() == [job_ids[4], job_ids[3], job_ids[2]]
        wait_for(lambda: all(job['status'] == 'COMPLETED' for job in jobs().values()), 'automatic queue completion', 40)
        for file_id, payload in zip(ids, payloads):
            assert receiver.call(f'/api/library/{file_id}/content') == payload
        assert queue() == []
        receiver.call('/api/auth/logout', 'POST', {})
        try:
            priority(job_ids[0], 'LOW')
            raise AssertionError('Anonymous priority updates must be rejected')
        except urllib.error.HTTPError as error:
            assert error.code == 401
        (run / 'result.json').write_text(json.dumps({'result': 'PASS', 'files': 5,
            'totalBytes': sum(map(len, payloads)), 'maxActiveDownloads': 2}, indent=2), encoding='utf-8')
        print('PASS: all queued files finish automatically with valid SHA-256; priority API requires authentication', flush=True)
        print('Test logs:', run, flush=True)
    finally:
        for name in reversed(processes):
            stop(name)
        for log in logs:
            log.close()


if __name__ == '__main__':
    main()

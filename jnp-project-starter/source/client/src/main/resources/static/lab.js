'use strict';
const PeerLab = (() => {
  let selected = '', busy = false;
  const {escape: e, bytes: b} = UI;
  const modes = {NORMAL:'Bình thường', SLOW:'Nguồn chậm +1500 ms', DISCONNECT:'Ngắt kết nối', CORRUPT:'Gửi chunk sai SHA-256'};
  function topology(s) {
    const lab = s.lab, active = lab.links || [], peers = new Map();
    for (const link of [...(lab.recentLinks || []), ...active]) peers.set(link.peerId, link);
    const list = [...peers.values()].slice(0, 10), height = Math.max(300, list.length * 78 + 60);
    const incoming = s.network.upload.remotes || [];
    return `<svg class="lab-topology" viewBox="0 0 900 ${height}" role="img" aria-label="Nguồn TCP đang gửi cho peer này và kết nối điều phối với tracker">
      <defs><marker id="lab-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10z" fill="currentColor"/></marker></defs>
      <path class="lab-control" d="M540 ${height/2} L760 ${height/2}"/><text x="650" y="${height/2-15}" text-anchor="middle">HTTP · metadata / vé tải</text>
      ${list.map((p,i) => {
        const y=45+i*78, requests=active.filter(l=>l.peerId===p.peerId), stats=lab.sourceStats?.[p.peerId];
        return `<path class="lab-edge ${requests.length?'flow':''}" d="M220 ${y} Q340 ${y} 440 ${height/2}" marker-end="url(#lab-arrow)"/>
          <rect x="10" y="${y-26}" width="210" height="56" rx="14"/><text x="115" y="${y-5}" text-anchor="middle">${e(p.name.slice(0,25))}</text><text class="lab-small" x="115" y="${y+15}" text-anchor="middle">${e(p.host)}:${p.port}</text>
          <text class="lab-small" x="260" y="${y-10}">${requests.length?requests.length+' TCP · chunk '+requests.map(r=>r.chunk).join(', '):'Vừa nhận · '+Math.floor((Date.now()-p.at)/1000)+'s trước'}</text><text class="lab-small" x="260" y="${y+10}">${stats?b(stats.speed)+'/s · '+stats.chunks+' chunk đã nhận':''}</text>`;
      }).join('')}
      <circle cx="490" cy="${height/2}" r="50" class="lab-self"/><text x="490" y="${height/2-4}" text-anchor="middle">${e((s.name||'Peer này').slice(0,18))}</text><text x="490" y="${height/2+17}" text-anchor="middle" class="lab-small">Đang nhận ${active.length} TCP</text>
      <rect x="760" y="${height/2-30}" width="130" height="60" rx="16"/><text x="825" y="${height/2+5}" text-anchor="middle">Tracker</text>
      ${!list.length?`<text x="20" y="40">Chờ phiên tải hoặc phép đo để hiện nguồn.</text>`:''}
    </svg><p class="section-note">Đường chuyển động: yêu cầu TCP đang mở đến nguồn; đường xám: nguồn vừa gửi trong 30 giây. Đây là góc nhìn của peer này. Tracker chỉ điều phối. Tốc độ là số liệu tổng tại biểu đồ bên dưới.</p>
    <div class="source-row"><span class="source-chip">↑ ${incoming.length} kết nối gửi đang mở</span>${incoming.slice(0,8).map(address=>`<span class="source-chip mono">TCP → ${e(address)}</span>`).join('')}</div>`;
  }
  function chart(samples) {
    if (!samples.length) return UI.empty('Đang thu mẫu tốc độ…');
    const max = Math.max(1,...samples.flatMap(p=>[p.receive,p.send])), w=860, h=160;
    const line = key => samples.map((p,i)=>`${40+i*w/Math.max(1,samples.length-1)},${190-p[key]/max*h}`).join(' ');
    const last=samples[samples.length-1], span=(last.at-samples[0].at)/1000;
    return `<div class="lab-legend"><span class="lab-rx">● Nhận đã xác minh: ${b(last.receive)}/s</span><span class="lab-tx">● Gửi: ${b(last.send)}/s</span><span>${Math.round(span)} giây gần nhất</span></div>
      <svg class="lab-chart" viewBox="0 0 930 225" role="img" aria-label="Biểu đồ tốc độ nhận đã xác minh và gửi qua TCP theo thời gian">
      ${[0,.5,1].map(n=>`<line x1="40" y1="${190-n*h}" x2="900" y2="${190-n*h}" class="lab-grid"/><text x="42" y="${182-n*h}" class="lab-small">${b(max*n)}/s</text>`).join('')}
      <polyline points="${line('receive')}" class="lab-rx-line"/><polyline points="${line('send')}" class="lab-tx-line"/>
      <text x="40" y="215">${e(new Date(samples[0].at).toLocaleTimeString('vi-VN'))}</text><text x="900" y="215" text-anchor="end">${e(new Date(last.at).toLocaleTimeString('vi-VN'))}</text></svg>
      <p class="section-note">Tối đa 120 mẫu trong RAM; mỗi mẫu dùng số byte tăng thêm / thời gian thực giữa hai lần đo. Nhận tính sau xác minh SHA-256, gồm cả phép đo. Lấy mẫu mỗi giây trên luồng riêng; thời gian tính bằng đồng hồ đơn điệu.</p>`;
  }
  function render(s) {
    if (!s.lab) return UI.empty('Cần khởi động lại peer với bản mới để dùng phòng thí nghiệm.');
    const lab=s.lab, test=lab.benchmark, results=test.results||[], fault=lab.fault;
    const files=(s.catalog||[]).filter(f=>f.sources>=2);
    if (!files.some(f=>f.manifest.id===selected)) selected=files[0]?.manifest.id||'';
    const allowed=s.user.status==='APPROVED'&&s.peerStatus==='APPROVED';
    const speedup=results.length===2?results[1].speed/results[0].speed:0;
    return `<section class="panel"><div class="panel-head"><h2>Mạng truyền file trực tiếp</h2><span class="badge ONLINE">TCP thực tế</span></div>${topology(s)}</section>
      <section class="panel"><h2>Tốc độ truyền theo thời gian</h2>${chart(lab.samples||[])}</section>
      <section class="panel"><div class="panel-head"><h2>So sánh một nguồn / nhiều nguồn</h2><span class="badge ${test.status==='RUNNING'?'DOWNLOADING':test.status}">${e({IDLE:'Chưa đo',RUNNING:'Đang đo',COMPLETED:'Hoàn tất',ERROR:'Lỗi'}[test.status]||test.status)}</span></div>
      <p class="section-note">Hai lượt đọc cùng tối đa 64 chunk đầu (16 MiB), mỗi lượt 4 worker. Lượt một dùng một nguồn, lượt hai chia cho nhiều nguồn. Mỗi chunk đều qua TCP và xác minh SHA-256; dữ liệu đo được bỏ khỏi RAM sau xử lý. Cần 2 nguồn khác peer này có đủ chunk. Tạm dừng tải trước khi đo.</p>
      <div class="lab-controls"><select id="lab-file" aria-label="File dùng để đo">${files.length?files.map(f=>`<option value="${f.manifest.id}" ${selected===f.manifest.id?'selected':''}>${e(f.manifest.name)} · ${f.sources} nguồn</option>`).join(''):'<option value="">Chưa có file với ít nhất 2 nguồn</option>'}</select><button class="primary" data-lab="benchmark" ${busy||test.status==='RUNNING'||!selected||!allowed?'disabled':''}>▶ Đo hai lượt</button></div>
      ${test.phase&&test.status==='RUNNING'?`<p role="status">Đang đo: ${e(test.phase)}…</p>`:''}${test.error?`<p class="status-message" role="alert">${e(test.error)}</p>`:''}
      ${results.length?UI.table(['Lượt đo','Dữ liệu / chunk','Thời gian','Tốc độ','Dữ liệu từng nguồn'],results.map(r=>`<tr><td><strong>${e(r.mode)}</strong></td><td>${b(r.bytes)} / ${r.chunks}</td><td>${(r.durationMs/1000).toFixed(3)} s</td><td>${b(r.speed)}/s</td><td>${Object.entries(r.sourceBytes).map(([name,n])=>`${e(name)}: ${b(n)}`).join('<br>')}</td></tr>`)):''}
      ${speedup?`<p class="lab-comparison">Tốc độ nhiều nguồn / một nguồn: <strong>${speedup.toFixed(2)}×</strong></p>`:''}
      <p class="section-note">Kết quả phụ thuộc mạng, ổ đĩa, HTTP xác thực vé và cache. Nhiều nguồn có thể chậm hơn trên cùng máy. Nên đo lại và ghi điều kiện. Chế độ chậm mô phỏng độ trễ từng yêu cầu, không giới hạn tổng băng thông nguồn. Kết quả giữ đến lần đo tiếp theo hoặc khi khởi động lại.</p></section>
      <section class="panel"><div class="panel-head"><h2>Mô phỏng lỗi tại nguồn này</h2><span class="badge ${fault.mode==='NORMAL'?'ONLINE':'WAITING'}">${e(modes[fault.mode]||fault.mode)}</span></div>
      <p class="section-note">Bật tại peer đang gửi, quan sát tải tại peer nhận. Mỗi chế độ tự hết sau 30 giây; file gốc giữ nguyên. Chế độ hỏng lật một byte trong bản gửi sau khi nguồn kiểm tra hash để peer nhận thực sự phát hiện SHA-256 sai.</p>
      <div class="actions">${Object.entries(modes).map(([mode,label])=>`<button class="${mode==='NORMAL'?'primary':'ghost'} small" data-lab="fault" data-mode="${mode}" ${busy||!allowed?'disabled':''}>${e(label)}</button>`).join('')}</div>
      <p class="section-note">${fault.until?'Tự khôi phục lúc '+e(new Date(fault.until).toLocaleTimeString('vi-VN'))+' · ':''}${s.network.upload.injected||0} yêu cầu đã chịu mô phỏng trong lần chạy này. Các kết nối đã xử lý lỗi cần được peer nhận thử lại.</p></section>
      <section class="panel"><h2>Sự kiện phục hồi</h2><div class="activity">${s.events.slice(-12).reverse().map(line=>`<div class="activity-row"><span class="activity-mark">↻</span><div>${e(line)}</div></div>`).join('')||'<p class="section-note">Chưa có sự kiện.</p>'}</div></section>`;
  }
  document.addEventListener('change', event=>{if(event.target.id==='lab-file')selected=event.target.value;});
  document.addEventListener('click',async event=>{
    const button=event.target.closest('[data-lab]'); if(!button||busy)return;
    busy=true; button.disabled=true;
    try {
      const action=button.dataset.lab;
      const body=action==='fault'?{mode:button.dataset.mode,delayMs:1500,seconds:30}:{fileId:selected};
      const result=await UI.api('/api/lab/'+action,{method:'POST',body}); UI.toast(result.message);
      await poll();
    } catch(error) { UI.toast(error.message,true); }
    finally { busy=false; button.disabled=false; }
  });
  return {render};
})();

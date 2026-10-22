'use strict';
window.UI = {
  escape(value) { return String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); },
  bytes(n) { n=Number(n)||0; if(n<1024)return n+' B'; const units=['KiB','MiB','GiB']; let i=-1; do{n/=1024;i++}while(n>=1024&&i<2); return n.toFixed(n<10?1:0)+' '+units[i]; },
  time(n) {return n?new Date(n).toLocaleString('vi-VN'):'Chưa hoạt động';},
  labels: {APPROVED:'Đã duyệt',PENDING:'Chờ duyệt',BLOCKED:'Đã khóa / ẩn',ONLINE:'Trực tuyến',OFFLINE:'Ngoại tuyến',DOWNLOADING:'Đang tải',WAITING:'Đang tìm nguồn',PAUSED:'Tạm dừng',COMPLETED:'Hoàn tất',ERROR:'Lỗi'},
  badge(value) {return `<span class="badge ${this.escape(value)}">${this.escape(this.labels[value]||value)}</span>`;},
  empty(message) {return `<div class="empty"><div class="empty-icon">◇</div>${this.escape(message)}</div>`;},
  async api(path, options={}) {
    const headers={'X-PeerMesh':'1',...(options.headers||{})};
    let body=options.body;
    if(body!==undefined && !(body instanceof FormData)){headers['Content-Type']='application/json';body=JSON.stringify(body);}
    const response=await fetch(path,{...options,headers,body,credentials:'same-origin'});
    const data=await response.json().catch(()=>({message:'Phản hồi không hợp lệ.'}));
    if(!response.ok){const error=new Error(data.message||data.detail||'Yêu cầu thất bại.');error.status=response.status;throw error;}
    return data;
  },
  toast(message,error=false){let node=document.querySelector('#toast');if(!node){node=document.createElement('div');node.id='toast';node.role='status';document.body.append(node);}node.className='toast'+(error?' error':'');node.textContent=message;clearTimeout(this.timer);this.timer=setTimeout(()=>node.remove(),6500);},
  stat(label,value,note='',accent=false){return `<div class="stat"><div class="stat-label">${this.escape(label)}</div><div class="stat-number ${accent?'accent':''}">${this.escape(value)}</div><div class="stat-note">${this.escape(note)}</div></div>`;},
  table(head,rows){return `<div class="table-wrap"><table><thead><tr>${head.map(h=>`<th>${this.escape(h)}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;}
};
// CSP-friendly progress widths: create fixed classes once, avoiding inline styles.
document.addEventListener('DOMContentLoaded',()=>{for(let i=0;i<document.styleSheets.length;i++){const sheet=document.styleSheets[i];try{for(let n=0;n<=100;n++)sheet.insertRule(`.progress-fill.p${n}{width:${n}%}`,sheet.cssRules.length);break;}catch(_){}}});

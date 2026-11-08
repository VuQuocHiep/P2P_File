import { bytes } from '/assets/js/ui.js';

export function bandwidthPanel(bandwidth = {}) {
  const limit = (value) => (value ? bytes(value * 1024) + '/s' : 'Không giới hạn');
  return /* HTML */ `<section class="panel">
    <div class="panel-head">
      <h2>Băng thông truyền file</h2>
      <button class="small ghost" data-action="bandwidth">Cài đặt băng thông</button>
    </div>
    <div class="two-col">
      <div>
        <strong>Upload: ${bytes(bandwidth.uploadBytesPerSecond || 0)}/s</strong>
        <p class="section-note" data-bandwidth="upload">
          Giới hạn: ${limit(bandwidth.uploadLimitKiB)}
        </p>
      </div>
      <div>
        <strong>Download: ${bytes(bandwidth.downloadBytesPerSecond || 0)}/s</strong>
        <p class="section-note" data-bandwidth="download">
          Giới hạn: ${limit(bandwidth.downloadLimitKiB)}
        </p>
      </div>
    </div>
    <p class="section-note">
      Tốc độ dữ liệu TCP đo mỗi giây, gồm cả chunk đang truyền. Giới hạn dùng chung cho mọi kết nối
      của peer, kể cả phép đo trong Phòng thí nghiệm.
    </p>
  </section>`;
}

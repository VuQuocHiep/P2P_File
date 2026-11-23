export default function BandwidthDialog() {
  return (
    <dialog id="bandwidth-dialog">
      <div className="dialog-top">
        <h2>Cài đặt băng thông</h2>
        <button type="button" className="ghost small" data-close="bandwidth-dialog" aria-label="Đóng">
          ✕
        </button>
      </div>
      <p className="section-note" id="bandwidth-help">
        Nhập KiB/s (1024 KiB/s = 1 MiB/s). Đặt 0 để không giới hạn. Cài đặt áp dụng ngay cho dữ liệu
        TCP đang truyền và được giữ sau khi khởi động lại peer.
      </p>
      <form id="bandwidth-form">
        <div className="field-row">
          <div className="field">
            <label htmlFor="upload-limit-kib">Giới hạn upload (KiB/s)</label>
            <input
              id="upload-limit-kib"
              name="uploadLimitKiB"
              type="number"
              min="0"
              max="1048576"
              step="1"
              defaultValue="0"
              required
              aria-describedby="bandwidth-help"
            />
          </div>
          <div className="field">
            <label htmlFor="download-limit-kib">Giới hạn download (KiB/s)</label>
            <input
              id="download-limit-kib"
              name="downloadLimitKiB"
              type="number"
              min="0"
              max="1048576"
              step="1"
              defaultValue="0"
              required
              aria-describedby="bandwidth-help"
            />
          </div>
        </div>
        <p className="status-message" id="bandwidth-error" role="alert"></p>
        <button className="primary" type="submit" data-request-key="bandwidth">Lưu giới hạn</button>
      </form>
    </dialog>
  );
}

export default function UploadDialog() {
  return (
    <dialog id="upload-dialog">
      <div className="dialog-top">
        <h2>Thêm file vào mạng</h2>
        <button type="button" className="ghost small" data-close="upload-dialog" aria-label="Đóng">
          ✕
        </button>
      </div>
      <p className="section-note">
        File được lưu ở peer này. Tracker tự quét VirusTotal; file sạch sẽ được duyệt để các máy
        khác tải trực tiếp qua TCP.
      </p>
      <form id="upload-form">
        <div className="upload-zone">
          <div className="empty-icon">↑</div>
          <strong>Chọn file bạn muốn chia sẻ</strong
          ><input type="file" name="file" aria-label="Chọn file chia sẻ" required />
          <div className="section-note">Tối đa 2 GiB · Tự tạo manifest và mã băm</div>
        </div>
        <p className="section-note">
          Nếu VirusTotal chưa có báo cáo, hệ thống tự gửi nội dung file tối đa 32 MiB để quét.
          Nội dung có thể được chia sẻ với đối tác và khách hàng VirusTotal.
        </p>
        <button className="primary" type="submit" data-request-key="upload">
          Tạo manifest & chia sẻ →
        </button>
        <p id="upload-status" className="section-note" role="status"></p>
      </form>
    </dialog>
  );
}

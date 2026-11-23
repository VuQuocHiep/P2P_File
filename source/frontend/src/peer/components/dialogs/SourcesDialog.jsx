export default function SourcesDialog() {
  return (
    <dialog id="sources-dialog">
      <div className="dialog-top">
        <h2>Thông tin file & nguồn tải</h2>
        <button type="button" className="ghost small" data-close="sources-dialog" aria-label="Đóng">
          ✕
        </button>
      </div>
      <div id="sources-content"></div>
    </dialog>
  );
}

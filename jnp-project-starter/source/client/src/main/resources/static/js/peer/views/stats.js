'use strict';

function statCards() {
  let jobs = state.jobs || [],
    library = state.library || [];
  return (
    UI.stat(
      'FILE TRONG THƯ VIỆN',
      library.filter((e) => e.complete).length,
      'Lưu trên thiết bị này',
    ) +
    UI.stat(
      'PHIÊN ĐANG HOẠT ĐỘNG',
      jobs.filter((j) => ['DOWNLOADING', 'WAITING'].includes(j.status)).length,
      'Tối đa 2 file đồng thời',
      true,
    ) +
    UI.stat(
      'DỮ LIỆU ĐÃ NHẬN',
      bytes(jobs.reduce((n, j) => n + j.bytes, 0)),
      'Tổng phần đã xác minh',
    ) +
    UI.stat('DỮ LIỆU ĐÃ GỬI', bytes(state.uploaded), 'Trong lần chạy peer này')
  );
}

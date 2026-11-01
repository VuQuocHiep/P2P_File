'use strict';

const { escape: esc, bytes, time } = UI;

let mode = 'login',
  tab = 'explore',
  lastTab = '',
  state = null,
  catalog = [],
  query = '',
  polling = false,
  catalogLoading = false;

const titles = {
  lab: 'Phòng thí nghiệm P2P',
  explore: 'Khám phá file',
  downloads: 'Phiên tải của tôi',
  library: 'Thư viện chia sẻ',
  activity: 'Hoạt động peer',
};

const notes = {
  lab: 'Quan sát kết nối thật, đo hiệu năng và thử khả năng phục hồi.',
  explore: 'Tìm file trong mạng và kết nối trực tiếp đến các nguồn.',
  downloads: 'Theo dõi từng phần, từng nguồn và tiến độ đã lưu.',
  library: 'Những file và phần file bạn đang đóng góp cho mạng.',
  activity: 'Thông tin thiết bị, kết nối và nhật ký cục bộ.',
};

const $ = (s) => document.querySelector(s);

function applyState(next) {
  state = next;
  if (Array.isArray(next.catalog))
    catalog = next.catalog.filter((file) =>
      file.manifest.name.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
    );
}

function ready() {
  return state?.user?.status === 'APPROVED' && state?.peerStatus === 'APPROVED';
}

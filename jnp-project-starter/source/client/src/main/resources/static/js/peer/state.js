export function createPeerState() {
  return {
    mode: 'login',
    tab: 'explore',
    snapshot: null,
    catalog: [],
    query: '',
    lab: { selected: '', busy: false },
  };
}

export const titles = {
  lab: 'Phòng thí nghiệm P2P',
  explore: 'Khám phá file',
  downloads: 'Phiên tải của tôi',
  library: 'Thư viện chia sẻ',
  activity: 'Hoạt động peer',
};

export const notes = {
  lab: 'Quan sát kết nối thật, đo hiệu năng và thử khả năng phục hồi.',
  explore: 'Tìm file trong mạng và kết nối trực tiếp đến các nguồn.',
  downloads: 'Theo dõi từng phần, từng nguồn và tiến độ đã lưu.',
  library: 'Những file và phần file bạn đang đóng góp cho mạng.',
  activity: 'Thông tin thiết bị, kết nối và nhật ký cục bộ.',
};

export function applyState(model, next) {
  model.snapshot = next;
  if (Array.isArray(next.catalog))
    model.catalog = next.catalog.filter((file) =>
      file.manifest.name.toLocaleLowerCase().includes(model.query.toLocaleLowerCase()),
    );
}

export function ready(state) {
  return state?.user?.status === 'APPROVED' && state?.peerStatus === 'APPROVED';
}

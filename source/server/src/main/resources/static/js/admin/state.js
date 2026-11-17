export function createAdminState() {
  return {
    current: 'overview',
    data: null,
    signedIn: false,
    malware: { scanningFiles: new Set(), scanErrors: new Map() },
  };
}

export const titles = {
  overview: 'Tổng quan mạng',
  accounts: 'Quản lý tài khoản',
  peers: 'Quản lý thiết bị peer',
  files: 'Danh mục chia sẻ',
  transfers: 'Theo dõi phiên tải',
  audit: 'Nhật ký hệ thống',
};

export const notes = {
  overview: 'Một góc nhìn rõ ràng về mạng chia sẻ của bạn.',
  accounts: 'Kiểm soát ai được tham gia mạng chia sẻ.',
  peers: 'Phê duyệt thiết bị và theo dõi kết nối.',
  files: 'Duyệt nội dung trước khi xuất hiện trong tìm kiếm.',
  transfers: 'Tiến độ do các peer báo cáo, cập nhật theo heartbeat.',
  audit: 'Theo dõi các thay đổi và thao tác quản trị.',
};

import { UI } from '/assets/js/ui.js';
import { peerApi } from '../api/index.js';

export function createRoomActions({ model, data, requests, client = peerApi }) {
  async function create(form) {
    const values = new FormData(form);
    const body = {
      name: String(values.get('name') || '').trim(),
      id: String(values.get('id') || '').trim() || null,
      password: values.get('password') || null,
    };
    await requests.run('room-create', async () => {
      const room = await client.createRoom(body);
      UI.toast('Đã tạo phòng ' + room.name + ' (' + room.id + ').');
      form.reset();
      // Owners land straight in the room they just created.
      model.roomId = room.id;
      await data.loadRooms();
    });
  }

  async function join(form) {
    const values = new FormData(form);
    const id = String(values.get('id') || '').trim();
    const body = { password: values.get('password') || null };
    await requests.run('room-join', async () => {
      const room = await client.joinRoom(id, body);
      form.reset();
      if (room.membership === 'APPROVED') UI.toast('Bạn đã ở trong phòng ' + room.name + '.');
      else UI.toast('Đã gửi yêu cầu vào phòng ' + room.name + '. Chờ chủ phòng duyệt.');
      await data.loadRooms();
    });
  }

  async function joinCard(roomId, hasPassword) {
    let password = null;
    if (hasPassword) {
      password = window.prompt('Mật khẩu phòng ' + roomId);
      if (password === null) return;
    }
    await requests.run('room-join:' + roomId, async () => {
      const room = await client.joinRoom(roomId, { password });
      if (room.membership === 'APPROVED') UI.toast('Bạn đã ở trong phòng ' + room.name + '.');
      else UI.toast('Đã gửi yêu cầu vào phòng ' + room.name + '. Chờ chủ phòng duyệt.');
      await data.loadRooms();
    });
  }

  async function decide(roomId, username, status) {
    await requests.run(`room:${roomId}:${username}`, async () => {
      const member = await client.roomDecide(roomId, username, { status });
      UI.toast(
        'Đã ' + (member.status === 'APPROVED' ? 'duyệt ' : 'từ chối ') + member.username + '.',
      );
      await data.loadRooms();
    });
  }

  async function share(roomId) {
    const select = document.getElementById('room-share-' + roomId);
    const fileId = select ? select.value : '';
    if (!fileId) return;
    await requests.run(`room-share:${roomId}:${fileId}`, async () => {
      const result = await client.shareRoomFile(roomId, fileId);
      UI.toast(result.message);
      await data.loadRooms();
      await data.poll();
    });
  }

  async function rename(roomId, currentName) {
    const name = window.prompt('Tên phòng mới', currentName || '');
    if (name === null) return;
    await requests.run(`room-rename:${roomId}`, async () => {
      const room = await client.roomUpdate(roomId, { name });
      UI.toast('Đã đổi tên phòng thành ' + room.name + '.');
      await data.loadRooms();
    });
  }

  async function password(roomId, hasPassword) {
    const value = window.prompt(
      hasPassword
        ? 'Mật khẩu mới (để trống để bỏ mật khẩu)'
        : 'Mật khẩu mới (để trống nếu không đặt)',
      '',
    );
    if (value === null) return;
    await requests.run(`room-password:${roomId}`, async () => {
      await client.roomUpdate(roomId, { password: value });
      UI.toast(value ? 'Đã đổi mật khẩu phòng.' : 'Đã bỏ mật khẩu phòng.');
      await data.loadRooms();
    });
  }

  async function kick(roomId, username) {
    if (!window.confirm('Đuổi ' + username + ' khỏi phòng?')) return;
    await requests.run(`room-kick:${roomId}:${username}`, async () => {
      const result = await client.roomRemoveMember(roomId, username);
      UI.toast(result.message);
      await data.loadRooms();
      await data.poll();
    });
  }

  async function destroy(roomId, name) {
    if (
      !window.confirm(
        'Xoá phòng "' +
          (name || roomId) +
          '"? Toàn bộ file của phòng sẽ bị xoá khỏi mạng và các peer sẽ dọn bản lưu.',
      )
    )
      return;
    await requests.run(`room-delete:${roomId}`, async () => {
      const result = await client.roomDelete(roomId);
      UI.toast(result.message);
      if (model.roomId === roomId) model.roomId = '';
      await data.loadRooms();
      await data.poll();
    });
  }

  return { create, join, joinCard, decide, share, rename, password, kick, destroy };
}

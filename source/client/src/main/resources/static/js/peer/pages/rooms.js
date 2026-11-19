import { UI, esc, bytes } from '/assets/js/ui.js';
import { ready } from '../store/state.js';

const ID_PATTERN = '[a-zA-Z0-9][a-zA-Z0-9_-]{2,23}';

function roleBadge(room) {
  if (room.mine) return '<span class="badge">Chủ phòng</span>';
  if (room.membership === 'APPROVED') return '<span class="badge">Thành viên</span>';
  if (room.membership === 'PENDING') return '<span class="badge PENDING">Chờ duyệt</span>';
  if (room.membership === 'REJECTED') return '<span class="badge">Bị từ chối</span>';
  return '<span class="badge">Chưa tham gia</span>';
}

function statusBadge(room) {
  if (room.membership === 'PENDING') return UI.badge('PENDING');
  if (room.membership === 'REJECTED') return '<span class="badge BLOCKED">Bị từ chối</span>';
  return '<span class="badge">Chưa tham gia</span>';
}

function openButton(room) {
  return /* HTML */ `<button
    class="small ghost"
    data-action="room-open"
    data-room="${esc(room.id)}"
  >
    Mở phòng
  </button>`;
}

function joinButton(room) {
  return /* HTML */ `<button
    class="small primary"
    data-action="room-join-card"
    data-request-key="room-join:${esc(room.id)}"
    data-room="${esc(room.id)}"
    data-password="${room.hasPassword}"
  >
    Xin vào phòng
  </button>`;
}

function requestRows(roomId, requests = []) {
  if (!requests.length) return '';
  return /* HTML */ `<h3>Yêu cầu vào phòng</h3>
    ${UI.table(
      ['Tài khoản', 'Thao tác'],
      requests.map(
        (m) =>
          /* HTML */ `<tr>
            <td class="mono">${esc(m.username)}</td>
            <td>
              <div class="actions">
                <button
                  class="small primary"
                  data-action="room-approve"
                  data-room="${esc(roomId)}"
                  data-user="${esc(m.username)}"
                >
                  Duyệt</button
                ><button
                  class="small ghost"
                  data-action="room-reject"
                  data-room="${esc(roomId)}"
                  data-user="${esc(m.username)}"
                >
                  Từ chối
                </button>
              </div>
            </td>
          </tr>`,
      ),
    )}`;
}

function memberRows(roomId, members = []) {
  const others = members.filter((m) => m.status !== 'OWNER');
  if (!others.length) return '<p class="section-note">Chưa có thành viên nào khác.</p>';
  return /* HTML */ `<h3>Thành viên</h3>
    ${UI.table(
      ['Tài khoản', 'Vai trò', 'Thao tác'],
      others.map(
        (m) =>
          /* HTML */ `<tr>
            <td class="mono">${esc(m.username)}</td>
            <td>${UI.badge(m.status)}</td>
            <td>
              <button
                class="small ghost"
                data-action="room-kick"
                data-room="${esc(roomId)}"
                data-user="${esc(m.username)}"
              >
                Đuổi
              </button>
            </td>
          </tr>`,
      ),
    )}`;
}

function fileRows(roomId, files = []) {
  if (!files.length) return '<p class="section-note">Phòng chưa có file nào.</p>';
  return UI.table(
    ['File', 'Dung lượng', 'Trạng thái', 'Nguồn', 'Thao tác'],
    files.map(
      (f) =>
        /* HTML */ `<tr>
          <td>
            <strong>${esc(f.manifest.name)}</strong
            ><small class="mono">${esc(f.manifest.id.slice(0, 16))}…</small>
          </td>
          <td>${bytes(f.manifest.size)}</td>
          <td>${UI.badge(f.status)}</td>
          <td>${f.sources}</td>
          <td>
            <div class="actions">
              <button
                class="small"
                data-action="download"
                data-id="${esc(f.manifest.id)}"
                ${f.status !== 'APPROVED' || !f.sources ? 'disabled' : ''}
              >
                ↓ Tải về
              </button>
            </div>
          </td>
        </tr>`,
    ),
  );
}

function shareControl(roomId, library = []) {
  const choices = library
    .filter((e) => e.sharing)
    .map(
      (e) =>
        `<option value="${esc(e.manifest.id)}">${esc(e.manifest.name)} (${bytes(
          e.manifest.size,
        )})</option>`,
    )
    .join('');
  if (!choices)
    return '<p class="section-note">Bật chia sẻ một file trong Thư viện để thêm vào phòng.</p>';
  return /* HTML */ `<div class="actions">
    <label for="room-share-${esc(roomId)}" class="section-note">Thêm file vào phòng</label>
    <select id="room-share-${esc(roomId)}">
      ${choices}
    </select>
    <button class="small" data-action="room-share" data-room="${esc(roomId)}">
      Chia sẻ vào phòng
    </button>
  </div>`;
}

function ownerControls(room) {
  return /* HTML */ `<div class="actions">
    <button
      class="small ghost"
      data-action="room-rename"
      data-room="${esc(room.id)}"
      data-name="${esc(room.name)}"
    >
      Đổi tên
    </button>
    <button
      class="small ghost"
      data-action="room-password"
      data-room="${esc(room.id)}"
      data-has-password="${room.hasPassword}"
    >
      ${room.hasPassword ? 'Đổi mật khẩu' : 'Đặt mật khẩu'}
    </button>
    <button
      class="small ghost"
      data-action="room-delete"
      data-room="${esc(room.id)}"
      data-name="${esc(room.name)}"
    >
      Xoá phòng
    </button>
  </div>`;
}

function roomSummary(room) {
  return /* HTML */ `<p class="section-note">
    Mã phòng <span class="mono">${esc(room.id)}</span> · Chủ phòng
    <span class="mono">${esc(room.owner)}</span> · ${room.members} thành viên · ${room.files} file${room
      .hasPassword
      ? ' · Có mật khẩu'
      : ''}
  </p>`;
}

function roomCard(room, state, requests, files, members) {
  const member = room.mine || room.membership === 'APPROVED';
  return /* HTML */ `<section class="panel" data-key="room-${esc(room.id)}">
    <div class="dialog-top">
      <h2>${esc(room.name)}</h2>
      ${roleBadge(room)}
    </div>
    ${roomSummary(room)}
    <div class="actions">${openButton(room)}</div>
    ${room.mine ? ownerControls(room) : ''} ${room.mine ? requestRows(room.id, requests) : ''}
    ${room.mine ? memberRows(room.id, members) : ''}
    ${member
      ? fileRows(room.id, files)
      : '<p class="section-note">Đang chờ chủ phòng duyệt yêu cầu của bạn.</p>'}
    ${member ? shareControl(room.id, state.library) : ''}
  </section>`;
}

function roomDetail(room, state, requests, files, members) {
  const back = /* HTML */ `<div class="actions">
    <button class="small ghost" data-action="room-back">← Danh sách phòng</button>
  </div>`;
  const member = room.mine || room.membership === 'APPROVED';
  if (!member)
    return (
      back +
      /* HTML */ `<section class="panel" id="room-detail">
        <div class="dialog-top">
          <h2>${esc(room.name)}</h2>
          ${roleBadge(room)}
        </div>
        ${roomSummary(room)}
        <p class="section-note">
          ${room.membership === 'PENDING'
            ? 'Yêu cầu vào phòng đang chờ chủ phòng duyệt.'
            : 'Bạn chưa phải thành viên của phòng này.'}
        </p>
        <div class="actions">
          ${room.membership === 'PENDING' ? '' : joinButton(room)}
        </div>
      </section>`
    );
  return back + /* HTML */ `<div id="room-detail">${roomCard(room, state, requests, files, members)}</div>`;
}

function openRooms(others) {
  if (!others.length)
    return UI.empty('Chưa có phòng nào khác trong mạng. Tạo phòng mới hoặc dùng mã phòng để vào.');
  return /* HTML */ `<section class="panel">
    <div class="panel-head">
      <h2>Phòng đang mở</h2>
      <span class="badge">${others.length} phòng</span>
    </div>
    ${UI.table(
      ['Phòng', 'Chủ phòng', 'Thành viên', 'File', 'Trạng thái', 'Thao tác'],
      others.map(
        (room) =>
          /* HTML */ `<tr data-key="open-${esc(room.id)}">
            <td>
              <strong>${esc(room.name)}</strong
              ><small class="mono"
                >${esc(room.id)}${room.hasPassword ? ' · có mật khẩu' : ''}</small
              >
            </td>
            <td class="mono">${esc(room.owner)}</td>
            <td>${room.members}</td>
            <td>${room.files}</td>
            <td>${statusBadge(room)}</td>
            <td>
              <div class="actions">
                ${openButton(room)}${room.membership === 'PENDING' ? '' : joinButton(room)}
              </div>
            </td>
          </tr>`,
      ),
    )}
  </section>`;
}

export function rooms(state, list = [], requestsByRoom = {}, filesByRoom = {}, membersByRoom = {}, selectedRoomId = '') {
  if (!ready(state))
    return UI.empty('Cần tài khoản và thiết bị được duyệt trước khi dùng phòng chia sẻ.');
  const create = /* HTML */ `<section class="panel">
    <h2>Tạo phòng</h2>
    <form id="room-create-form">
      <div class="field-row">
        <div class="field">
          <label for="room-name">Tên phòng</label>
          <input
            id="room-name"
            name="name"
            required
            minlength="2"
            maxlength="60"
            placeholder="Nhóm học tập"
          />
        </div>
        <div class="field">
          <label for="room-id">Mã phòng (tùy chọn)</label>
          <input
            id="room-id"
            name="id"
            pattern="${ID_PATTERN}"
            maxlength="24"
            placeholder="Để trống để tự sinh"
          />
        </div>
        <div class="field">
          <label for="room-password">Mật khẩu (tùy chọn)</label>
          <input id="room-password" name="password" type="password" minlength="6" maxlength="64" />
        </div>
      </div>
      <p class="status-message" id="room-create-status" role="status"></p>
      <button class="primary" type="submit" data-request-key="room-create">Tạo phòng</button>
    </form>
  </section>`;
  const join = /* HTML */ `<section class="panel">
    <h2>Vào phòng</h2>
    <form id="room-join-form">
      <div class="field-row">
        <div class="field">
          <label for="join-room-id">Mã phòng</label>
          <input id="join-room-id" name="id" required pattern="${ID_PATTERN}" maxlength="24" />
        </div>
        <div class="field">
          <label for="join-room-password">Mật khẩu (nếu phòng có)</label>
          <input id="join-room-password" name="password" type="password" />
        </div>
      </div>
      <p class="status-message" id="room-join-status" role="status"></p>
      <button class="primary" type="submit" data-request-key="room-join">
        Gửi yêu cầu vào phòng
      </button>
    </form>
  </section>`;
  const own = list.filter((room) => room.mine || room.membership === 'APPROVED');
  const others = list.filter((room) => !(room.mine || room.membership === 'APPROVED'));
  const selected = selectedRoomId ? list.find((room) => room.id === selectedRoomId) : null;
  if (selected)
    return roomDetail(
      selected,
      state,
      requestsByRoom[selected.id] || [],
      filesByRoom[selected.id] || [],
      membersByRoom[selected.id] || [],
    );
  return (
    create +
    join +
    `<p class="eyebrow">PHÒNG CỦA BẠN</p>` +
    (own.length
      ? own
          .map((room) =>
            roomCard(
              room,
              state,
              requestsByRoom[room.id] || [],
              filesByRoom[room.id] || [],
              membersByRoom[room.id] || [],
            ),
          )
          .join('')
      : UI.empty('Bạn chưa ở trong phòng nào. Tạo phòng mới hoặc xin vào một phòng đang mở.')) +
    openRooms(others)
  );
}

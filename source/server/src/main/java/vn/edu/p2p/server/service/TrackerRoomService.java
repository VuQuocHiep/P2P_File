package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.entity.*;
import vn.edu.p2p.server.mapper.FileMapper;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Rooms: creation, join requests, owner decisions and room-scoped file listing. */
@Service
public class TrackerRoomService {
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_-]{2,23}");
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TrackerContext context;
    private final TrackerAccess access;
    private final FileMapper fileMapper;

    public TrackerRoomService(TrackerContext context, TrackerAccess access, FileMapper fileMapper) {
        this.context = context;
        this.access = access;
        this.fileMapper = fileMapper;
    }

    public List<RoomResponse> rooms(String token) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                var result = new ArrayList<RoomResponse>();
                for (RoomEntity room : context.rooms.findAllByOrderByPositionAsc()) {
                    // Every active room is discoverable; membership tells the UI what to offer.
                    if (!"ACTIVE".equals(room.status)) continue;
                    result.add(response(room, membership(room, a.username)));
                }
                return result;
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    /** Every room with its pending queue, for the admin dashboard. */
    public List<RoomResponse> adminRooms(String token) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                access.admin(token);
                var result = new ArrayList<RoomResponse>();
                for (RoomEntity room : context.rooms.findAllByOrderByPositionAsc())
                    result.add(response(room, null, true));
                return result;
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public RoomResponse create(String token, RoomCreate input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                String name = input.name() == null ? "" : input.name().trim();
                access.require(name.length() >= 2 && name.length() <= 60, "Tên phòng cần 2–60 ký tự.");
                String id = input.id() == null ? "" : input.id().trim();
                if (id.isEmpty()) {
                    do id = randomId();
                    while (context.rooms.findRoomById(id).isPresent());
                } else {
                    access.require(ID.matcher(id).matches(), "Mã phòng cần 3–24 ký tự chữ, số, _ hoặc -.");
                    access.require(context.rooms.findRoomById(id).isEmpty(), "Mã phòng đã được sử dụng.");
                }
                String password = input.password() == null ? "" : input.password();
                access.require(
                        password.isEmpty() || password.length() >= 6 && password.length() <= 64,
                        "Mật khẩu phòng cần 6–64 ký tự hoặc để trống.");
                RoomEntity room = new RoomEntity();
                room.id = id;
                room.name = name;
                room.owner = a.username;
                room.passwordHash = password.isEmpty() ? null : context.passwords.encode(password);
                room.status = "ACTIVE";
                room.created = System.currentTimeMillis();
                room.position = context.nextRoomPosition++;
                context.rooms.save(room);
                RoomMemberEntity owner = new RoomMemberEntity();
                owner.id = memberId(room.id, a.username);
                owner.roomId = room.id;
                owner.username = a.username;
                owner.status = "APPROVED";
                owner.requestedAt = room.created;
                owner.decidedAt = room.created;
                context.roomMembers.save(owner);
                context.log(a.username, "CREATE_ROOM", room.id);
                return response(room, "OWNER");
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public RoomResponse join(String token, String roomId, RoomJoin input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = room(roomId);
                access.require("ACTIVE".equals(room.status), "Phòng đã đóng.");
                access.require(!room.owner.equals(a.username), "Bạn là chủ phòng này.");
                RoomMemberEntity member =
                        context.roomMembers.findByRoomIdAndUsername(room.id, a.username).orElse(null);
                access.require(
                        member == null || !"APPROVED".equals(member.status),
                        "Bạn đã ở trong phòng.");
                access.require(
                        member == null || !"PENDING".equals(member.status),
                        "Yêu cầu vào phòng đang chờ chủ phòng duyệt.");
                if (room.passwordHash != null) {
                    String password = input == null || input.password() == null ? "" : input.password();
                    access.require(
                            context.passwords.matches(password, room.passwordHash),
                            "Mật khẩu phòng không đúng.");
                }
                if (member == null) {
                    member = new RoomMemberEntity();
                    member.id = memberId(room.id, a.username);
                    member.roomId = room.id;
                    member.username = a.username;
                }
                member.status = "PENDING";
                member.requestedAt = System.currentTimeMillis();
                member.decidedAt = 0;
                context.roomMembers.save(member);
                context.log(a.username, "ROOM_JOIN_REQUEST", room.id);
                return response(room, "PENDING");
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public List<RoomMemberResponse> requests(String token, String roomId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = ownedRoom(a, roomId);
                return context.roomMembers.findByRoomIdAndStatusOrderByRequestedAtAsc(room.id, "PENDING")
                        .stream()
                        .map(m -> new RoomMemberResponse(m.username, m.status, m.requestedAt))
                        .toList();
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public RoomMemberResponse decide(String token, String roomId, String username, RoomDecision input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = ownedRoom(a, roomId);
                String status = input == null || input.status() == null ? "" : input.status();
                access.require(
                        "APPROVED".equals(status) || "REJECTED".equals(status),
                        "Trạng thái duyệt phải là APPROVED hoặc REJECTED.");
                RoomMemberEntity member =
                        context.roomMembers.findByRoomIdAndUsername(room.id, username).orElse(null);
                access.require(member != null, "Không có yêu cầu của tài khoản này.");
                access.require(
                        !room.owner.equals(username), "Chủ phòng không cần duyệt.");
                access.require(
                        "PENDING".equals(member.status), "Yêu cầu này đã được xử lý.");
                member.status = status;
                member.decidedAt = System.currentTimeMillis();
                context.roomMembers.save(member);
                context.log(a.username, "ROOM_" + status, room.id + ":" + username);
                return new RoomMemberResponse(member.username, member.status, member.requestedAt);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public RoomResponse update(String token, String roomId, RoomUpdate input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = ownedRoom(a, roomId);
                boolean changed = false;
                if (input != null && input.name() != null) {
                    String name = input.name().trim();
                    access.require(
                            name.length() >= 2 && name.length() <= 60, "Tên phòng cần 2–60 ký tự.");
                    room.name = name;
                    changed = true;
                }
                if (input != null && input.password() != null) {
                    String password = input.password();
                    access.require(
                            password.isEmpty()
                                    || password.length() >= 6 && password.length() <= 64,
                            "Mật khẩu phòng cần 6–64 ký tự hoặc để trống.");
                    room.passwordHash =
                            password.isEmpty() ? null : context.passwords.encode(password);
                    changed = true;
                }
                access.require(changed, "Không có thay đổi nào.");
                context.rooms.save(room);
                context.log(a.username, "UPDATE_ROOM", room.id);
                return response(room, "OWNER");
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public void removeMember(String token, String roomId, String username) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = ownedRoom(a, roomId);
                access.require(!room.owner.equals(username), "Không thể đuổi chủ phòng.");
                RoomMemberEntity member =
                        context.roomMembers.findByRoomIdAndUsername(room.id, username).orElse(null);
                access.require(member != null, "Tài khoản không có trong phòng.");
                member.status = "REJECTED";
                member.decidedAt = System.currentTimeMillis();
                context.roomMembers.save(member);
                context.log(a.username, "ROOM_KICK", room.id + ":" + username);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    /** Deletes the room, its memberships and its files, leaving deletion markers for peers. */
    public void delete(String token, String roomId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = ownedRoom(a, roomId);
                long now = System.currentTimeMillis();
                for (FileEntity file : context.files.findByRoomIdOrderByPositionAsc(room.id)) {
                    context.deletions.save(new FileDeletionEntity(file.id, now));
                    context.files.delete(file);
                    context.transfers.deleteByFileId(file.id);
                    context.tickets.entrySet().removeIf(e -> file.id.equals(e.getValue().fileId()));
                    context.log(a.username, "DELETE_ROOM_FILE", file.id);
                }
                for (RoomMemberEntity member :
                        context.roomMembers.findByRoomIdOrderByRequestedAtAsc(room.id)) {
                    context.roomMembers.delete(member);
                }
                context.rooms.delete(room);
                context.log(a.username, "DELETE_ROOM", room.id);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public List<RoomMemberResponse> members(String token, String roomId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = room(roomId);
                requireMember(a.username, room.id);
                var result = new ArrayList<RoomMemberResponse>();
                result.add(new RoomMemberResponse(room.owner, "OWNER", room.created));
                for (RoomMemberEntity m :
                        context.roomMembers.findByRoomIdAndStatusOrderByRequestedAtAsc(
                                room.id, "APPROVED")) {
                    if (!room.owner.equals(m.username))
                        result.add(new RoomMemberResponse(m.username, m.status, m.requestedAt));
                }
                return result;
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public List<FileResponse> files(String token, String roomId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                RoomEntity room = room(roomId);
                requireMember(a.username, room.id);
                return context.files.findByRoomIdOrderByPositionDesc(room.id).stream()
                        .map(f -> fileMapper.toResponse(f, access.onlineSources(f)))
                        .toList();
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    /** Throws unless the account owns the room or is an approved member. */
    public void requireMember(String username, String roomId) {
        RoomEntity room = room(roomId);
        if (room.owner.equals(username)) return;
        RoomMemberEntity member =
                context.roomMembers.findByRoomIdAndUsername(room.id, username).orElse(null);
        if (member == null || !"APPROVED".equals(member.status))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn chưa được duyệt vào phòng.");
    }

    public boolean member(String username, String roomId) {
        RoomEntity room = context.rooms.findRoomById(roomId).orElse(null);
        if (room == null) return false;
        if (room.owner.equals(username)) return true;
        return context.roomMembers
                .findByRoomIdAndUsername(room.id, username)
                .map(m -> "APPROVED".equals(m.status))
                .orElse(false);
    }

    public RoomEntity room(String roomId) {
        return context.rooms
                .findRoomById(roomId == null ? "" : roomId)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Phòng không tồn tại."));
    }

    private RoomEntity ownedRoom(AccountEntity account, String roomId) {
        RoomEntity room = room(roomId);
        access.require(room.owner.equals(account.username), "Chỉ chủ phòng được thực hiện thao tác này.");
        return room;
    }

    private String membership(RoomEntity room, String username) {
        if (room.owner.equals(username)) return "OWNER";
        return context.roomMembers
                .findByRoomIdAndUsername(room.id, username)
                .map(m -> m.status)
                .orElse(null);
    }

    private RoomResponse response(RoomEntity room, String membership) {
        return response(room, membership, "OWNER".equals(membership));
    }

    private RoomResponse response(RoomEntity room, String membership, boolean includePending) {
        long members = 1 + context.roomMembers.findByRoomIdAndStatusOrderByRequestedAtAsc(room.id, "APPROVED")
                .stream()
                .filter(m -> !room.owner.equals(m.username))
                .count();
        long pending = includePending
                ? context.roomMembers.findByRoomIdAndStatusOrderByRequestedAtAsc(room.id, "PENDING").size()
                : 0;
        long files = context.files.findByRoomIdOrderByPositionAsc(room.id).size();
        return new RoomResponse(
                room.id,
                room.name,
                room.owner,
                "OWNER".equals(membership),
                membership,
                room.passwordHash != null,
                members,
                pending,
                files,
                room.created,
                room.status);
    }

    private static String memberId(String roomId, String username) {
        return roomId + ":" + username;
    }

    private String randomId() {
        var builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return builder.toString();
    }
}

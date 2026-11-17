-- PeerMesh Tracker — MySQL schema
-- Hibernate ddl-auto=update sẽ tự tạo bảng, file này dùng để tạo thủ công nếu cần.

CREATE DATABASE IF NOT EXISTS peermesh
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE peermesh;

CREATE TABLE IF NOT EXISTS tracker_accounts (
    username          VARCHAR(255) NOT NULL PRIMARY KEY,
    password_hash     VARCHAR(255),
    role              VARCHAR(255),
    status            VARCHAR(255),
    created           BIGINT NOT NULL DEFAULT 0,
    registry_position BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracker_peers (
    id                VARCHAR(255) NOT NULL PRIMARY KEY,
    owner             VARCHAR(255),
    name              VARCHAR(255),
    host              VARCHAR(255),
    status            VARCHAR(255),
    port              INT NOT NULL DEFAULT 0,
    last_seen         BIGINT NOT NULL DEFAULT 0,
    registry_position BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracker_files (
    id                  VARCHAR(255) NOT NULL PRIMARY KEY,
    manifest            LONGTEXT,
    status              VARCHAR(255),
    submitted_by        VARCHAR(255),
    malware             LONGTEXT,
    scan_consent_peers  LONGTEXT,
    scan_stage          VARCHAR(255),
    scan_request_id     VARCHAR(255),
    scan_actor          VARCHAR(255),
    scan_lease_peer     VARCHAR(255),
    scan_lease_token    VARCHAR(255),
    scan_lease_until    BIGINT,
    scan_next_at        BIGINT,
    scan_failures       INT,
    holders             LONGTEXT,
    registry_position   BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracker_transfers (
    transfer_key      VARCHAR(255) NOT NULL PRIMARY KEY,
    id                VARCHAR(255),
    owner             VARCHAR(255),
    peer_id           VARCHAR(255),
    file_id           VARCHAR(255),
    name              LONGTEXT,
    status            VARCHAR(255),
    transferred_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at        BIGINT NOT NULL DEFAULT 0,
    registry_position BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracker_audit (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_time BIGINT NOT NULL DEFAULT 0,
    actor      LONGTEXT,
    action     VARCHAR(255),
    target     LONGTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tracker_migrations (
    name VARCHAR(255) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS registry (
    id      INT NOT NULL PRIMARY KEY,
    payload LONGTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

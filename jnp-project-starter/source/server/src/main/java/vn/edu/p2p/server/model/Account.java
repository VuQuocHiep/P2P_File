package vn.edu.p2p.server.model;

public class Account {
    public String username, passwordHash, role, status;
    public long created;
    public Account() {}
    public Account(String username, String passwordHash, String role, String status) {
        this.username = username; this.passwordHash = passwordHash; this.role = role; this.status = status;
        this.created = System.currentTimeMillis();
    }
}

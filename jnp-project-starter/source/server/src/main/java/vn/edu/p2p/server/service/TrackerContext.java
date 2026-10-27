package vn.edu.p2p.server.service;

import vn.edu.p2p.server.repository.TrackerState;
import vn.edu.p2p.server.model.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;

/** Shared registry state, accessed while holding the TrackerService monitor. */
final class TrackerContext {
    final TrackerState store;
    final Snapshot state;
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    final Map<String, Session> sessions = new HashMap<>();
    final Map<String, Ticket> tickets = new HashMap<>();
    TrackerContext(TrackerState store) { this.store = store; this.state = store.snapshot(); }
}

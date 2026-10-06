package com.reteclock.core;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memory-only challenge login and possession-bound sessions (RFC-0017). A session's ID is not a
 * bearer credential: every request is signed with a key that never crosses the network.
 *
 * <p><b>The limits are each peer's own.</b> The first version kept one count of failures and one
 * pool of challenges for the whole server, which let any peer on the network keep the owner out
 * without knowing anything (review of 2026-10-06): five wrong proofs paused every login, and
 * thirty-two unanswered challenges refused the next. Now a pause belongs to the address that
 * earned it; a challenge is never refused for want of room — the asker's own oldest goes first,
 * then the oldest of all; and a challenge answers only to the address that asked for it, so one
 * read off the network cannot be spent by somebody else. A much higher bar still pauses everybody,
 * so guessing from many addresses at once is slowed as a whole.
 */
public final class WebSessions {

    public interface Clock {
        long now();
    }

    public static final long CHALLENGE_MS = 60000, IDLE_MS = 600000, LIFE_MS = 1800000;
    public static final int MAX_CHALLENGES = 32, MAX_SESSIONS = 8;
    /** How many unanswered challenges one peer may hold; a further one replaces its oldest. */
    public static final int PEER_CHALLENGES = 4;
    /** Wrong proofs from one peer before that peer waits {@link #PAUSE_MS}. */
    public static final int PEER_FAILURES = 5;
    /** Wrong proofs from everybody within {@link #ALL_WINDOW_MS} before everybody waits. */
    public static final int ALL_FAILURES = 40;
    public static final long PAUSE_MS = 30000, ALL_WINDOW_MS = 60000;
    /** How many peers' failures are remembered at once. */
    public static final int MAX_PEERS = 64;

    public static final class Challenge {
        public final String id, nonce, salt, authority;
        final String peer;
        final boolean known;
        final long expires;

        Challenge(String salt, String authority, String peer, boolean known, long now) {
            id = WebAuth.random();
            nonce = WebAuth.random();
            this.salt = salt;
            this.authority = authority;
            this.peer = peer;
            this.known = known;
            expires = now + CHALLENGE_MS;
        }
    }

    public static final class Session {
        public final String id;
        final byte[] key;
        final long born;
        long touched, counter;

        Session(String id, byte[] key, long now) {
            this.id = id;
            this.key = key;
            born = touched = now;
        }
    }

    /** One peer's recent wrong proofs. */
    private static final class Strikes {
        int failures;
        long blockedUntil, touched;
    }

    private final WebAuth auth;
    private final Clock clock;
    private final LinkedHashMap<String, Challenge> challenges = new LinkedHashMap<String, Challenge>();
    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<String, Session>();
    private final LinkedHashMap<String, Strikes> peers = new LinkedHashMap<String, Strikes>();
    private int allFailures;
    private long allWindowStart, allBlockedUntil;
    private boolean revoked;

    public WebSessions(WebAuth auth, Clock clock) {
        this.auth = auth;
        this.clock = clock;
    }

    private void purge() {
        long now = clock.now();
        for (Iterator<Challenge> i = challenges.values().iterator(); i.hasNext();) {
            if (i.next().expires < now) {
                i.remove();
            }
        }
        for (Iterator<Session> i = sessions.values().iterator(); i.hasNext();) {
            Session s = i.next();
            if (now - s.touched > IDLE_MS || now - s.born > LIFE_MS) {
                Arrays.fill(s.key, (byte) 0);
                i.remove();
            }
        }
        for (Iterator<Strikes> i = peers.values().iterator(); i.hasNext();) {
            Strikes s = i.next();
            if (now >= s.blockedUntil && now - s.touched > ALL_WINDOW_MS) {
                i.remove();
            }
        }
    }

    private boolean paused(String peer, long now) {
        if (now < allBlockedUntil) {
            return true;
        }
        Strikes strikes = peers.get(peer);
        return strikes != null && now < strikes.blockedUntil;
    }

    private void failed(String peer, long now) {
        Strikes strikes = peers.get(peer);
        if (strikes == null) {
            if (peers.size() >= MAX_PEERS) {
                // The longest-remembered goes; a peer still paused is asked again soon enough.
                Iterator<String> oldest = peers.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            strikes = new Strikes();
            peers.put(peer, strikes);
        }
        strikes.touched = now;
        if (++strikes.failures >= PEER_FAILURES) {
            strikes.blockedUntil = now + PAUSE_MS;
            strikes.failures = 0;
        }
        if (now - allWindowStart > ALL_WINDOW_MS) {
            allWindowStart = now;
            allFailures = 0;
        }
        if (++allFailures >= ALL_FAILURES) {
            allBlockedUntil = now + PAUSE_MS;
            allFailures = 0;
        }
    }

    /** For callers with no peer to name: tests, and nothing on the network. */
    public synchronized Challenge challenge(String user, String authority) {
        return challenge(user, authority, "");
    }

    /**
     * A fresh challenge for this peer, or null while it is paused or the account is revoked.
     * Unknown IDs get the same shape of answer; the password verifier is never returned.
     */
    public synchronized Challenge challenge(String user, String authority, String peer) {
        purge();
        long now = clock.now();
        if (revoked || paused(peer, now)) {
            return null;
        }
        int held = 0;
        String oldestOfPeer = null;
        for (Challenge c : challenges.values()) {
            if (c.peer.equals(peer)) {
                if (oldestOfPeer == null) {
                    oldestOfPeer = c.id;
                }
                held++;
            }
        }
        if (held >= PEER_CHALLENGES) {
            challenges.remove(oldestOfPeer);
        }
        if (challenges.size() >= MAX_CHALLENGES) {
            Iterator<String> oldest = challenges.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        Challenge c = new Challenge(auth.salt(), authority, peer, auth.userEquals(user), now);
        challenges.put(c.id, c);
        return c;
    }

    public static String loginText(Challenge c, String clientNonce) {
        return "reteclock-login-v1\n" + c.authority + "\n" + c.id + "\n" + c.nonce + "\n"
                + clientNonce;
    }

    public static String sessionText(Challenge c, String clientNonce) {
        return "reteclock-session-v1\n" + c.authority + "\n" + c.id + "\n" + c.nonce + "\n"
                + clientNonce;
    }

    public synchronized Session login(String id, String clientNonce, String proof) {
        return login(id, clientNonce, proof, "");
    }

    /**
     * Spends the challenge on this proof, right or wrong — but only for the peer that asked for
     * it: anybody else is refused and the challenge stays for its owner.
     */
    public synchronized Session login(String id, String clientNonce, String proof, String peer) {
        purge();
        long now = clock.now();
        Challenge c = challenges.get(id);
        if (c != null && !c.peer.equals(peer)) {
            failed(peer, now);
            return null;
        }
        challenges.remove(id);
        boolean ok = false;
        try {
            ok = !revoked && c != null && c.known && !paused(peer, now)
                    && WebAuth.unbase64(clientNonce).length == 24
                    && WebAuth.same(auth.sign(loginText(c, clientNonce)), WebAuth.unbase64(proof));
        } catch (Exception invalid) {
            // Not Base64, or not there at all: a wrong proof like any other.
        }
        if (!ok) {
            failed(peer, now);
            return null;
        }
        peers.remove(peer);
        if (sessions.size() >= MAX_SESSIONS) {
            logout(sessions.keySet().iterator().next());
        }
        Session s = new Session(c.id, auth.sign(sessionText(c, clientNonce)), now);
        sessions.put(s.id, s);
        return s;
    }

    /** How many peers' failures are held just now; bounded by {@link #MAX_PEERS}. */
    public synchronized int rememberedPeers() {
        return peers.size();
    }

    public synchronized boolean exists(String id, long counter) {
        purge();
        Session s = sessions.get(id);
        return s != null && counter > s.counter && counter <= 9007199254740991L;
    }

    public synchronized boolean alive(String id) {
        purge();
        return sessions.containsKey(id);
    }

    /**
     * Whether this proof is the session's signature over this text, without spending the counter.
     * Asked of a request's head before its body is read (see {@link #headText}).
     */
    public synchronized boolean signed(String id, long counter, String text, String proof) {
        if (!exists(id, counter)) {
            return false;
        }
        Session s = sessions.get(id);
        try {
            return WebAuth.same(WebAuth.hmac(s.key, text.getBytes("UTF-8")), WebAuth.unbase64(proof));
        } catch (Exception invalid) {
            return false;
        }
    }

    public synchronized boolean accept(String id, long counter, String text, String proof) {
        if (!signed(id, counter, text, proof)) {
            return false;
        }
        Session s = sessions.get(id);
        s.counter = counter;
        s.touched = clock.now();
        return true;
    }

    public synchronized byte[] unmask(String id, String user, String salt, String nonce,
            byte[] masked) {
        purge();
        Session s = sessions.get(id);
        if (s == null) {
            throw new IllegalArgumentException("Session expired");
        }
        try {
            if (WebAuth.unbase64(salt).length != 16 || WebAuth.unbase64(nonce).length != 24) {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid account envelope");
        }
        return WebAuth.mask(s.key, user, salt, nonce, masked);
    }

    public synchronized void logout(String id) {
        Session s = sessions.remove(id);
        if (s != null) {
            Arrays.fill(s.key, (byte) 0);
        }
    }

    public synchronized void clear() {
        challenges.clear();
        for (Session s : sessions.values()) {
            Arrays.fill(s.key, (byte) 0);
        }
        sessions.clear();
        peers.clear();
        allFailures = 0;
        allBlockedUntil = 0;
    }

    /** Whether the account these sessions belonged to has been replaced; a restart follows. */
    public synchronized boolean revoked() {
        return revoked;
    }

    public synchronized void revoke() {
        revoked = true;
        clear();
    }

    /** What a request's final proof signs: everything about it, and the digest of its body. */
    public static String requestText(WebHttp.Request r, String id, long counter, String digest) {
        return "reteclock-request-v1\n" + id + "\n" + counter + "\n" + r.method + "\n" + r.target
                + "\n" + r.header("host") + "\n" + r.header("content-type") + "\n"
                + r.header("x-csrf-token") + "\n" + r.header("x-settings-revision") + "\n" + digest;
    }

    /**
     * What a request's head proof signs: everything known before the body arrives, and how long
     * the body will be. A body is read — and written to disk — only for a head that carries this,
     * so somebody who merely saw a session ID on the network cannot make the clock store their
     * megabytes (review of 2026-10-06).
     */
    public static String headText(WebHttp.Request r, String id, long counter) {
        return "reteclock-head-v1\n" + id + "\n" + counter + "\n" + r.method + "\n" + r.target
                + "\n" + r.header("host") + "\n" + r.header("content-type") + "\n"
                + r.header("x-csrf-token") + "\n" + r.header("x-settings-revision") + "\n"
                + r.length;
    }
}

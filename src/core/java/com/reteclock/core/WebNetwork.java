package com.reteclock.core;

import java.util.Locale;

/**
 * Which of the device's network interfaces the web administrator may listen on.
 *
 * An allow-list, by the names Android gives the local kinds: Wi-Fi, Ethernet, the phone's own
 * hotspot and its bridges. It was a block-list of four mobile-data names first (review of
 * 2026-10-06), which left every interface nobody had thought of — a carrier's under another name,
 * a VPN's tunnel — listened on whenever it happened to hold a private address. What is not known
 * to be local is now not listened on. Loopback is the caller's own business and is not asked here.
 */
public final class WebNetwork {

    private static final String[] LOCAL = {
        "wlan", "wifi", "wl", "eth", "en", "ap", "swlan", "softap", "br", "p2p", "rndis",
    };

    private WebNetwork() {
    }

    /** Whether an interface of this name is a local one: a known prefix, then nothing or a digit. */
    public static boolean local(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        for (String prefix : LOCAL) {
            if (lower.startsWith(prefix)) {
                String rest = lower.substring(prefix.length());
                if (rest.matches("[-_]?[0-9]*")) {
                    return true;
                }
            }
        }
        return false;
    }
}

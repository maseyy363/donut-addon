package com.example.addon.modules;

import com.example.addon.AddonTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs a module's event code so that an error in it can never escape into Meteor's own
 * event loop or GUI. The first error per module is written to latest.log.
 */
public final class Safe {
    private Safe() {}

    private static final Set<String> logged = new HashSet<>();

    public static void run(String module, Runnable code) {
        try {
            code.run();
        } catch (Throwable t) {
            if (logged.add(module)) {
                AddonTemplate.LOG.error("[Donut addon] error in " + module + " (further errors hidden)", t);
            }
        }
    }
}

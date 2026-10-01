package com.acme.other;

import java.lang.invoke.MethodHandles;

/**
 * A lookup rooted in a <em>different</em> package of the same child loader, used
 * to exercise the package rule for non-public targets.
 */
public final class OtherLookups {

    /**
     * Returns this class's own lookup.
     *
     * @return a full-privilege lookup rooted in {@code com.acme.other}
     */
    public static MethodHandles.Lookup self() {
        return MethodHandles.lookup();
    }
}

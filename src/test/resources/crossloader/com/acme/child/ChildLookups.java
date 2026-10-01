package com.acme.child;

import java.lang.invoke.MethodHandles;

/**
 * Produces full-privilege lookups rooted in this (child) loader, the way a
 * framework inside a plugin loader would.
 */
public final class ChildLookups {

    /**
     * Returns a private lookup for a class in this loader.
     *
     * @param name the binary name to look up
     * @return a full-privilege lookup whose class is the named class
     * @throws Exception if the class cannot be found or accessed
     */
    public static MethodHandles.Lookup lookupFor(String name) throws Exception {
        Class<?> target = Class.forName(name, false,
                ChildLookups.class.getClassLoader());
        return MethodHandles.privateLookupIn(target, MethodHandles.lookup());
    }
}

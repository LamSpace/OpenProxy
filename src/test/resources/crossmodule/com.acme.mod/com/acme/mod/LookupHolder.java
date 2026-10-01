package com.acme.mod;

import java.lang.invoke.MethodHandles;

public final class LookupHolder {
    public static MethodHandles.Lookup lookupFor(String name) throws Exception {
        Class<?> target = Class.forName(name, false,
                LookupHolder.class.getClassLoader());
        return MethodHandles.privateLookupIn(target, MethodHandles.lookup());
    }
}

package com.acme.child;

interface ChildPackageIface {
    String greet(String name);

    default String pkgShout(String name) {
        return "pkg-shout " + name;
    }
}

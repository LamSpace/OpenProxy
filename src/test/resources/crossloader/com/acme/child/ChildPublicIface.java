package com.acme.child;

public interface ChildPublicIface {
    String greet(String name);

    default String shout(String name) {
        return "SHOUT " + name;
    }
}

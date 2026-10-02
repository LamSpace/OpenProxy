module com.acme.proxyable {
    requires io.github.lamspace.openproxy;
    // exported for the test entry point only; the target package
    // (com.acme.proxyable) is exported to no one
    exports com.acme.proxyable.driver;
}

package com.acme.proxyable.driver;

import com.acme.proxyable.Widget;

import io.github.lamspace.Interceptor;
import io.github.lamspace.OpenProxy;

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;

/**
 * Proxy entry points called from INSIDE the named module. The library runs as
 * its own copy in this module layer, so interceptors must be created here too;
 * everything crossing back to the classpath test is a String, boolean or
 * WeakReference, never a type from this module (whose package is deliberately
 * exported to no one).
 */
public final class ProxyDriver {

    private ProxyDriver() {
    }

    private static MethodHandles.Lookup lookup()
            throws IllegalAccessException {
        return MethodHandles.privateLookupIn(Widget.class,
                MethodHandles.lookup());
    }

    /** Intercepts hello with delegation to the real implementation. */
    public static String helloDelegating() throws Throwable {
        Interceptor i = (o, m, a) ->
                "SUPER:" + OpenProxy.invokeSuper(o, m, a);
        Widget w = OpenProxy.proxy(Widget.class, lookup(), i);
        return w.hello("x");
    }

    /** Two constant proxies: same class must be reused, values kept apart. */
    public static String twoProxiesOneClass() throws Throwable {
        Widget a = OpenProxy.proxy(Widget.class, lookup(),
                (Interceptor) (o, m, args) -> "V1");
        Widget b = OpenProxy.proxy(Widget.class, lookup(),
                (Interceptor) (o, m, args) -> "V2");
        if (a.getClass() != b.getClass()) {
            return "REGEN";
        }
        return a.hello("p") + "/" + b.hello("q");
    }

    /** Where the generated class ended up: module and defining loader. */
    public static String proxyPlacement() throws Throwable {
        Widget w = OpenProxy.proxy(Widget.class, lookup(),
                (Interceptor) (o, m, args) -> "x");
        Class<?> pc = w.getClass();
        return pc.getModule().getName() + "|"
                + (pc.getClassLoader()
                        == ProxyDriver.class.getClassLoader() ? "same" : "diff");
    }

    /** The loader this driver lives in, weakly — for the eviction probe. */
    public static WeakReference<ClassLoader> selfLoaderRef() {
        return new WeakReference<>(ProxyDriver.class.getClassLoader());
    }

    /** Evicts everything cached for this layer's loader, from inside. */
    public static void evictSelf() {
        OpenProxy.evictClassLoader(ProxyDriver.class.getClassLoader());
    }
}

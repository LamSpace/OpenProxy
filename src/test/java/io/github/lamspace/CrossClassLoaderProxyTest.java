/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proxies targets owned by a loader the library cannot see, using a
 * caller-supplied definition lookup.
 *
 * <p>Every test keeps the no-lookup behavior intact: the sibling
 * {@link CrossClassLoaderLimitsTest} still pins those failures, and none of
 * them needed flipping — a supplied lookup is purely additive.
 */
class CrossClassLoaderProxyTest {

    private static final String CHILD_CLASS =
            CrossLoaderFixtures.PKG + ".ChildPublicClass";
    private static final String CHILD_PKG_CLASS =
            CrossLoaderFixtures.PKG + ".ChildPackageClass";
    private static final String CHILD_IFACE =
            CrossLoaderFixtures.PKG + ".ChildPublicIface";
    private static final String CHILD_PKG_IFACE =
            CrossLoaderFixtures.PKG + ".ChildPackageIface";
    private static final String LOOKUPS =
            CrossLoaderFixtures.PKG + ".ChildLookups";

    @BeforeAll
    static void compileFixtures() throws Exception {
        CrossLoaderFixtures.urls();
    }

    // ------------------------------------------------------------------
    // 3.2 / spec "Class proxy of a child-loader target"
    // ------------------------------------------------------------------

    @Test
    void suppliedLookupProxiesChildClassAndDelegatesToIt() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            Object proxy = OpenProxy.proxy(target, lookupFor(child, CHILD_CLASS),
                    sup());
            assertEquals("Hello, k", invoke1(proxy, "hello", "k"));
            assertSame(child, proxy.getClass().getClassLoader(),
                    "the hidden class must live in the target's loader");
            assertEquals(CrossLoaderFixtures.PKG,
                    proxy.getClass().getPackageName(),
                    "placement follows the supplied lookup's package");
        }
    }

    @Test
    void suppliedLookupProxiesPackagePrivateChildClass() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_PKG_CLASS);
            Object proxy = OpenProxy.proxy(target,
                    lookupFor(child, CHILD_PKG_CLASS), sup());
            assertEquals("Pkg p", invoke1(proxy, "hello", "p"));
            assertFalse(java.lang.reflect.Modifier.isPublic(
                    target.getModifiers()), "premise: target is not public");
        }
    }

    // ------------------------------------------------------------------
    // 3.1 / spec "Public interface invisible to the library's loader"
    // ------------------------------------------------------------------

    @Test
    void suppliedProxyChildInterfaceImplementsThatExactClass()
            throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> itf = child.loadClass(CHILD_IFACE);
            Object proxy = OpenProxy.proxy(new Class<?>[]{itf},
                    lookupFor(child, CHILD_CLASS), sup());
            assertSame(itf, proxy.getClass().getInterfaces()[0],
                    "the proxy must implement the child's own interface");
            assertSame(child, proxy.getClass().getClassLoader());
            // invokeSuper reaches the interface's default implementation.
            // (greet is abstract: delegating to it stays an AbstractMethodError
            // — existing behavior, covered by the interface proxy suite.)
            assertEquals("SHOUT d", invoke1(proxy, "shout", "d"));
        }
    }

    @Test
    void suppliedProxyPackagePrivateChildInterfaceStillNeedsItsPackage()
            throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> itf = child.loadClass(CHILD_PKG_IFACE);
            Object proxy = OpenProxy.proxy(new Class<?>[]{itf},
                    lookupFor(child, CHILD_PKG_IFACE), sup());
            assertSame(itf, proxy.getClass().getInterfaces()[0]);
            assertEquals("pkg-shout f", invoke1(proxy, "pkgShout", "f"));
        }
    }

    // ------------------------------------------------------------------
    // 2.1 / spec "Per-lookup proxy caching"
    // ------------------------------------------------------------------

    @Test
    void sameLookupReusesOneClassDifferentLookupsDoNotShare() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            MethodHandles.Lookup first = lookupFor(child, CHILD_CLASS);
            MethodHandles.Lookup second = lookupFor(child, LOOKUPS);

            Object a = OpenProxy.proxy(target, first, konst("a"));
            Object b = OpenProxy.proxy(target, first, konst("b"));
            assertSame(a.getClass(), b.getClass(),
                    "an equal lookup must hit the cache, not regenerate");

            Object c = OpenProxy.proxy(target, second, konst("c"));
            assertNotSame(a.getClass(), c.getClass(),
                    "a different lookup must not share the cached class");
            assertEquals("a", invoke1(a, "hello", "1"));
            assertEquals("c", invoke1(c, "hello", "2"));
        }
    }

    // ------------------------------------------------------------------
    // 2.2 / spec "Lookup lacking private access is rejected with a clear reason"
    // ------------------------------------------------------------------

    @Test
    void lookupFromAnotherLoaderIsRejectedWithBothLoadersNamed()
            throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            IllegalArgumentException diag = expectLookupDiagnostic(
                    () -> OpenProxy.proxy(target, MethodHandles.lookup(),
                            konst("never")));
            assertTrue(diag.getMessage().contains(CHILD_CLASS),
                    diag.getMessage());
            assertTrue(diag.getMessage().contains(
                    "the supplied Lookup is rooted in"), diag.getMessage());
            assertTrue(diag.getMessage().contains(
                    "its loader must be the loader that owns the target"),
                    diag.getMessage());
        }
    }

    @Test
    void lookupWithoutFullPrivilegeAccessIsRejected() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            IllegalArgumentException diag = expectLookupDiagnostic(
                    () -> OpenProxy.proxy(target, MethodHandles.publicLookup(),
                            konst("never")));
            assertTrue(diag.getMessage().contains("full privilege access"),
                    diag.getMessage());
        }
    }

    @Test
    void nonPublicTargetNeedsLookupInItsOwnPackage() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_PKG_CLASS);
            Class<?> other = child.loadClass(
                    CrossLoaderFixtures.OTHER_PKG + ".OtherLookups");
            MethodHandles.Lookup wrongPackage = (MethodHandles.Lookup)
                    other.getMethod("self").invoke(null);
            assertSame(child, target.getClassLoader(),
                    "premise: same loader, different package");
            assertSame(child, wrongPackage.lookupClass().getClassLoader());
            IllegalArgumentException diag = expectLookupDiagnostic(
                    () -> OpenProxy.proxy(target, wrongPackage, konst("never")));
            assertTrue(diag.getMessage().contains("not public"),
                    diag.getMessage());
            assertTrue(diag.getMessage().contains(CrossLoaderFixtures.PKG),
                    diag.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 2.3 / spec "Loader that cannot see the library reports the visibility gap"
    // ------------------------------------------------------------------

    @Test
    void loaderThatCannotResolveLibraryTypesIsDiagnosed() throws Exception {
        // Isolated loader: fixtures only, no delegation to the library's loader.
        ChildFirstClassLoader isolated = new ChildFirstClassLoader(
                new URL[]{CrossLoaderFixtures.urls()[0]},
                ClassLoader.getPlatformClassLoader(),
                CrossLoaderFixtures.PKG + ".",
                CrossLoaderFixtures.OTHER_PKG + ".");
        Class<?> target = isolated.loadClass(CHILD_CLASS);
        MethodHandles.Lookup lookup = lookupFor(isolated, CHILD_CLASS);
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName(Interceptor.class.getName(), false,
                        isolated),
                "premise: the isolated loader cannot see the library");

        IllegalArgumentException diag = assertThrows(
                IllegalArgumentException.class,
                () -> OpenProxy.proxy(target, lookup, konst("never")));
        assertTrue(diag.getMessage().contains("cannot resolve"),
                diag.getMessage());
        assertTrue(diag.getMessage().contains(Interceptor.class.getName()),
                diag.getMessage());
        assertTrue(diag.getMessage().contains(
                "Delegate to the library's loader"), diag.getMessage());
    }

    @Test
    void namedModuleTargetIsDiagnosedNotLeftAsIllegalAccessError()
            throws Exception {
        // A target in a named module: defineHiddenClass would place the proxy
        // in that module, where it cannot read the library's unnamed module.
        NamedModuleFixture named = NamedModuleFixture.create();
        Class<?> target = named.loadClass("com.acme.mod.Widget");
        MethodHandles.Lookup lookup = named.lookupFor("com.acme.mod.Widget");
        assertTrue(target.getModule().isNamed(),
                "premise: the target lives in a named module");
        assertTrue(lookup.hasFullPrivilegeAccess(),
                "premise: the lookup is as privileged as it can be");
        IllegalArgumentException diag = assertThrows(
                IllegalArgumentException.class,
                () -> OpenProxy.proxy(target, lookup, konst("never")));
        assertTrue(diag.getMessage().contains("cannot read module"),
                diag.getMessage());
        assertTrue(diag.getMessage().contains(target.getModule().getName()),
                diag.getMessage());
    }

    // ------------------------------------------------------------------
    // 3.3 / annotation-driven entry through a supplied lookup
    // ------------------------------------------------------------------

    @Intercept
    public static class HelloInterceptor {
        @Around("hello")
        public Object around(Object proxy, Method method, Object[] args)
                throws Throwable {
            return "measured:" + OpenProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void annotationDrivenProxyWorksWithSuppliedLookup() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            Object proxy = OpenProxy.intercept(target,
                    lookupFor(child, CHILD_CLASS), new HelloInterceptor());
            assertEquals("measured:Hello, m", invoke1(proxy, "hello", "m"));
            assertSame(child, proxy.getClass().getClassLoader());
        }
    }

    // ------------------------------------------------------------------
    // 3.3 / static proxies through a supplied lookup
    // ------------------------------------------------------------------

    @Test
    void staticProxyWithLookupShadowsChildStaticsInItsLoader()
            throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            Class<?> proxyClass = OpenProxy.proxyStatic(target,
                    lookupFor(child, CHILD_CLASS),
                    Group.otherwise(konst("S")));
            assertSame(child, proxyClass.getClassLoader());
            assertEquals(CrossLoaderFixtures.PKG,
                    proxyClass.getPackageName());
            assertEquals("S", proxyClass.getMethod("stat", String.class)
                    .invoke(null, "x"));
        }
    }

    @Test
    void staticProxyStateIsPerLoaderAndNeverCached() throws Exception {
        Class<?> first;
        Class<?> second;
        try (ChildFirstClassLoader a = CrossLoaderFixtures.childLoader()) {
            first = OpenProxy.proxyStatic(a.loadClass(CHILD_CLASS),
                    lookupFor(a, CHILD_CLASS), Group.otherwise(konst("A")));
        }
        try (ChildFirstClassLoader b = CrossLoaderFixtures.childLoader()) {
            second = OpenProxy.proxyStatic(b.loadClass(CHILD_CLASS),
                    lookupFor(b, CHILD_CLASS), Group.otherwise(konst("B")));
        }
        assertNotSame(first, second, "static proxies are never cached");
        assertNotEquals(first.getClassLoader(), second.getClassLoader());
        assertEquals("A", first.getMethod("stat", String.class)
                .invoke(null, "x"));
        assertEquals("B", second.getMethod("stat", String.class)
                .invoke(null, "x"));
    }

    // ------------------------------------------------------------------
    // 3.4 / constructor interception through a supplied lookup
    // ------------------------------------------------------------------

    @Test
    void constructorInterceptionWorksWithSuppliedLookup() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            List<String> events = new ArrayList<>();
            ConstructorInterceptor ctor = new ConstructorInterceptor() {
                @Override
                public Object[] before(Constructor<?> c, Object[] args) {
                    events.add("before");
                    return args;
                }

                @Override
                public void after(Object proxy, Constructor<?> c, Object[] a) {
                    events.add("after");
                }
            };
            Object proxy = OpenProxy.proxy(target,
                    lookupFor(child, CHILD_CLASS), new Object[0], ctor,
                    Group.otherwise(konst("intercepted")));
            assertEquals(List.of("before", "after"), events);
            assertEquals("intercepted", invoke1(proxy, "hello", "x"));
        }
    }

    // ------------------------------------------------------------------
    // 4.1 / hot deployment across loaders
    // ------------------------------------------------------------------

    @Test
    void redeployAcrossLoadersEvictsOnlyTheDiscardedLoader() throws Throwable {
        ChildFirstClassLoader oldLoader = CrossLoaderFixtures.childLoader();
        Class<?> oldTarget = oldLoader.loadClass(CHILD_CLASS);
        Object oldProxy = OpenProxy.proxy(oldTarget,
                lookupFor(oldLoader, CHILD_CLASS), konst("OLD"));

        ChildFirstClassLoader newLoader = CrossLoaderFixtures.childLoader();
        Class<?> newTarget = newLoader.loadClass(CHILD_CLASS);
        Object newProxy = OpenProxy.proxy(newTarget,
                lookupFor(newLoader, CHILD_CLASS), konst("NEW"));
        assertNotSame(oldProxy.getClass(), newProxy.getClass(),
                "distinct loaders must not share a generated class");

        OpenProxy.evictClassLoader(oldLoader);

        Object regenerated = OpenProxy.proxy(oldTarget,
                lookupFor(oldLoader, CHILD_CLASS), konst("OLD2"));
        assertNotSame(oldProxy.getClass(), regenerated.getClass(),
                "evicted loader regenerates");
        Object stillCached = OpenProxy.proxy(newTarget,
                lookupFor(newLoader, CHILD_CLASS), konst("NEW2"));
        assertSame(newProxy.getClass(), stillCached.getClass(),
                "the other loader's entry must stay cached");
        assertEquals("OLD", invoke1(oldProxy, "hello", "x"),
                "instances built before eviction keep serving");
        assertEquals("NEW", invoke1(newProxy, "hello", "x"));
    }

    @Test
    void evictDropsEveryLookupVariantOfOneTarget() throws Throwable {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> target = child.loadClass(CHILD_CLASS);
            Object a = OpenProxy.proxy(target, lookupFor(child, CHILD_CLASS),
                    konst("a"));
            Object b = OpenProxy.proxy(target, lookupFor(child, LOOKUPS),
                    konst("b"));
            assertNotSame(a.getClass(), b.getClass());

            OpenProxy.evict(target);

            Object c = OpenProxy.proxy(target, lookupFor(child, CHILD_CLASS),
                    konst("c"));
            assertNotSame(a.getClass(), c.getClass(),
                    "evict must drop the entry created by this lookup too");
        }
    }

    // ------------------------------------------------------------------
    // 4.2 / the accepted retention trade-off, observed rather than assumed
    // ------------------------------------------------------------------

    @Test
    void evictReleasesTheLookupAndItsLoader() throws Throwable {
        WeakReference<ClassLoader> ref = proxyThenEvict();
        for (int i = 0; i < 32 && ref.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(ref.get(),
                "after evictClassLoader plus GC the cache must not retain "
                + "the supplied lookup or its loader");
    }

    /**
     * Proxies a child-loaded target with a supplied lookup, evicts that loader,
     * and returns a weak reference taken after every strong handle died.
     */
    private static WeakReference<ClassLoader> proxyThenEvict() throws Exception {
        ChildFirstClassLoader child = CrossLoaderFixtures.childLoader();
        Class<?> target = child.loadClass(CHILD_CLASS);
        Object proxy = OpenProxy.proxy(target, lookupFor(child, CHILD_CLASS),
                konst("x"));
        assertEquals("x", invoke1(proxy, "hello", "q"));
        OpenProxy.evictClassLoader(child);
        return new WeakReference<>(child);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Produces a full-privilege lookup from inside {@code child}. */
    private static MethodHandles.Lookup lookupFor(ClassLoader child,
                                                  String rootedIn)
            throws Exception {
        Class<?> holder = child.loadClass(LOOKUPS);
        return (MethodHandles.Lookup) holder
                .getMethod("lookupFor", String.class).invoke(null, rootedIn);
    }

    /**
     * Asserts the call failed with the lookup diagnostic — an
     * {@code IllegalArgumentException} reachable in the cause chain, with no
     * {@code IllegalAccessException} below it.
     */
    private static IllegalArgumentException expectLookupDiagnostic(
            Executable call) {
        RuntimeException thrown = assertThrows(RuntimeException.class, call,
                "must fail with a diagnostic, not a LinkageError");
        IllegalArgumentException found = null;
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof IllegalArgumentException iae
                    && iae.getMessage() != null
                    && iae.getMessage().contains("the supplied Lookup")) {
                found = iae;
            }
            assertFalse(t instanceof IllegalAccessException,
                    "IllegalAccessException must not be reported: " + t);
        }
        assertNotNull(found, "no lookup diagnostic in: " + thrown);
        assertNull(found.getCause(),
                "the diagnostic must be terminal: " + found.getCause());
        return found;
    }

    /** Interceptor that delegates to the real implementation. */
    private static Interceptor sup() {
        return (o, m, a) -> OpenProxy.invokeSuper(o, m, a);
    }

    /** Interceptor that returns a fixed value. */
    private static Interceptor konst(String value) {
        return (o, m, a) -> value;
    }

    /** Invokes a one-String-argument method reflectively. */
    private static Object invoke1(Object target, String name, String arg)
            throws Exception {
        return target.getClass().getMethod(name, String.class)
                .invoke(target, arg);
    }
}

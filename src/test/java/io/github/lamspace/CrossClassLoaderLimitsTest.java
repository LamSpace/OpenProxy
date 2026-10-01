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

import io.github.lamspace.crossloader.Shadowable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the cross-ClassLoader boundary: which proxy shapes the library can
 * generate, and how the ones it cannot are reported.
 *
 * <p>Every failing-shape test asserts the <em>diagnostic</em>, not the raw JVM
 * exception, so the suite stays meaningful when cross-loader support lands
 * (the follow-on change flips the expectation, not the mechanism).
 */
class CrossClassLoaderLimitsTest {

    // ---- same-loader mirrors of every fixture shape (control cases) ----

    static class SamePublicClass {
        public String hello(String name) {
            return "Hello, " + name;
        }

        public static String stat(String name) {
            return "Stat " + name;
        }
    }

    static class SamePackageClass {
        public String hello(String name) {
            return "Pkg " + name;
        }
    }

    public interface SamePublicIface {
        String greet(String name);

        default String shout(String name) {
            return "SHOUT " + name;
        }
    }

    interface SamePackageIface {
        String greet(String name);

        default String pkgShout(String name) {
            return "pkg-shout " + name;
        }
    }

    @BeforeAll
    static void compileFixtures() throws Exception {
        CrossLoaderFixtures.urls();
    }

    // ------------------------------------------------------------------
    // 1.1 Fixture premise: the parent loader genuinely cannot see them.
    // ------------------------------------------------------------------

    @Test
    void parentCannotResolveFixturesWhileChildCan() throws Exception {
        ClassLoader parent = CrossClassLoaderLimitsTest.class.getClassLoader();
        ClassNotFoundException expected = assertThrows(
                ClassNotFoundException.class,
                () -> parent.loadClass(CrossLoaderFixtures.PKG
                        + ".ChildPublicClass"));

        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> loaded = child.loadClass(CrossLoaderFixtures.PKG
                    + ".ChildPublicClass");
            assertSame(child, loaded.getClassLoader(),
                    "fixture must be owned by the child loader, otherwise "
                    + "every cross-loader case below is vacuous: " + expected);
        }
    }

    // ------------------------------------------------------------------
    // 1.2 / 2.2 Controls: same-loader shapes, and ancestor-loader JDK
    // interfaces, proxy exactly as before.
    // ------------------------------------------------------------------

    @Test
    void sameLoaderShapesStillProxy() throws Throwable {
        SamePublicClass byClass = OpenProxy.proxy(SamePublicClass.class, sup());
        assertEquals("Hello, x", byClass.hello("x"));

        SamePackageClass pkgClass = OpenProxy.proxy(SamePackageClass.class,
                sup());
        assertEquals("Pkg y", pkgClass.hello("y"));

        SamePublicIface pubIface = OpenProxy.proxy(SamePublicIface.class,
                sup());
        assertEquals("SHOUT z", pubIface.shout("z"));

        Object anchorIface = OpenProxy.proxy(
                new Class<?>[]{SamePackageIface.class}, sup());
        assertEquals("pkg-shout w",
                ((SamePackageIface) anchorIface).pkgShout("w"));

        Object both = OpenProxy.proxy(
                new Class<?>[]{SamePublicIface.class, SamePackageIface.class},
                konst("mixed"));
        assertEquals("mixed", ((SamePublicIface) both).greet("v"));

        Class<?> statics = OpenProxy.proxyStatic(SamePublicClass.class,
                Group.otherwise(konst("static")));
        assertEquals("static", statics.getMethod("stat", String.class)
                .invoke(null, "u"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ancestorLoaderPublicInterfaceStillProxiesWithoutAddOpens() {
        Object proxy = OpenProxy.proxy(java.util.function.Function.class,
                konst("fixed"));
        assertInstanceOf(java.util.function.Function.class, proxy);
        assertEquals("fixed",
                ((java.util.function.Function<String, String>) proxy)
                        .apply("anything"));
    }

    // ------------------------------------------------------------------
    // 2.1 Class and anchor definition sites.
    // ------------------------------------------------------------------

    @Test
    void classProxyOfChildLoadedPublicClassIsDiagnosed() throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> type = fixture(child, "ChildPublicClass");
            assertCrossLoaderDiagnostic(child, type,
                    () -> OpenProxy.proxy(type, konst("never")));
        }
    }

    @Test
    void classProxyOfChildLoadedPackageClassIsDiagnosed() throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> type = fixture(child, "ChildPackageClass");
            assertCrossLoaderDiagnostic(child, type,
                    () -> OpenProxy.proxy(type, konst("never")));
        }
    }

    @Test
    void anchorInterfaceProxyOfChildLoadedPackageInterfaceIsDiagnosed()
            throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> type = fixture(child, "ChildPackageIface");
            assertCrossLoaderDiagnostic(child, type,
                    () -> OpenProxy.proxy(new Class<?>[]{type},
                            konst("never")));
        }
    }

    @Test
    void mixedChildLoadedInterfacesAreDiagnosed() throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> pub = fixture(child, "ChildPublicIface");
            Class<?> pkg = fixture(child, "ChildPackageIface");
            // The mixed array takes the anchor path, so the reported type is
            // the package-private anchor.
            assertCrossLoaderDiagnostic(child, pkg,
                    () -> OpenProxy.proxy(new Class<?>[]{pub, pkg},
                            konst("never")));
        }
    }

    // ------------------------------------------------------------------
    // 3.1 / 3.2 All-public interface definition site.
    // ------------------------------------------------------------------

    @Test
    void publicInterfaceInvisibleToLibraryLoaderIsDiagnosedNotLinkageError()
            throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> type = fixture(child, "ChildPublicIface");
            assertCrossLoaderDiagnostic(child, type,
                    () -> OpenProxy.proxy(new Class<?>[]{type},
                            konst("never")));
        }
    }

    @Test
    void childFirstNameCollisionIsDiagnosedNotSilentlyWrongProxy() {
        ChildFirstClassLoader shadow = CrossLoaderFixtures
                .shadowLoader(Shadowable.class.getPackageName() + ".");
        Class<?> childCopy = unchecked(() -> shadow.loadClass(
                Shadowable.class.getName()));
        assertNotSame(Shadowable.class, childCopy,
                "premise: the same name must resolve to two distinct classes");
        assertCrossLoaderDiagnostic(shadow, childCopy,
                () -> OpenProxy.proxy(new Class<?>[]{childCopy},
                        konst("never")));
    }

    // ------------------------------------------------------------------
    // 1.3 Static proxy definition site.
    // ------------------------------------------------------------------

    @Test
    void staticProxyOfChildLoadedTargetIsDiagnosed() throws Exception {
        try (ChildFirstClassLoader child = CrossLoaderFixtures.childLoader()) {
            Class<?> type = fixture(child, "ChildPublicClass");
            assertCrossLoaderDiagnostic(child, type,
                    () -> OpenProxy.proxyStatic(type,
                            Group.otherwise(konst("never"))));
        }
    }

    // ------------------------------------------------------------------
    // The one cross-loader shape that works today: a private OpenProxy copy
    // inside the plugin loader, with the interceptor from that same loader.
    // ------------------------------------------------------------------

    @Test
    void privateLibraryCopyProxiesWhenInterceptorSharesItsLoader()
            throws Exception {
        try (ChildFirstClassLoader plugin = privateCopyLoader()) {
            Class<?> library = plugin.loadClass(OpenProxy.class.getName());
            Class<?> interceptorType = plugin
                    .loadClass(Interceptor.class.getName());
            assertNotSame(OpenProxy.class, library,
                    "premise: the plugin loader must own its library copy");
            assertNotSame(Interceptor.class, interceptorType);

            Object handler = Proxy.newProxyInstance(plugin,
                    new Class<?>[]{interceptorType},
                    (p, m, args) -> "from-plugin");
            Object proxy = library.getMethod("proxy", Class.class,
                            interceptorType)
                    .invoke(null, plugin.loadClass(CrossLoaderFixtures.PKG
                                    + ".ChildPublicClass"),
                            handler);
            assertEquals("from-plugin", invoke1(proxy, "hello", "x"));

            // Same loader, public interface shape as well.
            Object iface = library.getMethod("proxy", Class[].class,
                            interceptorType)
                    .invoke(null, new Class<?>[]{plugin.loadClass(
                                    CrossLoaderFixtures.PKG
                                            + ".ChildPublicIface")},
                            handler);
            assertEquals("from-plugin", invoke1(iface, "greet", "y"));
        }
    }

    @Test
    void privateLibraryCopyRejectsInterceptorFromAnotherLoader()
            throws Exception {
        try (ChildFirstClassLoader plugin = privateCopyLoader()) {
            Class<?> library = plugin.loadClass(OpenProxy.class.getName());
            Class<?> interceptorType = plugin
                    .loadClass(Interceptor.class.getName());
            Object parentInterceptor = konst("from-parent");
            // The refusal happens at the JVM boundary: the child's proxy(Class,
            // Interceptor) does not accept the parent's Interceptor instance.
            assertThrows(IllegalArgumentException.class,
                    () -> library.getMethod("proxy", Class.class,
                                    interceptorType)
                            .invoke(null, plugin.loadClass(
                                            CrossLoaderFixtures.PKG
                                                    + ".ChildPublicClass"),
                                    parentInterceptor));
        }
    }

    /**
     * A loader holding its own copy of the library plus the fixture classes,
     * delegating to nothing but the platform loader — the OSGi-style shape
     * where the plugin cannot reach the application's OpenProxy.
     */
    private static ChildFirstClassLoader privateCopyLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(codeSourceOf(OpenProxy.class));
        urls.add(codeSourceOf(org.objectweb.asm.ClassWriter.class));
        urls.add(CrossLoaderFixtures.urls()[0]);
        return new ChildFirstClassLoader(urls.toArray(new URL[0]),
                ClassLoader.getPlatformClassLoader(),
                CrossLoaderFixtures.PKG + ".");
    }

    /** Location of the directory or jar that defined {@code type}. */
    private static URL codeSourceOf(Class<?> type) {
        java.net.URL url = type.getProtectionDomain().getCodeSource()
                .getLocation();
        assertNotNull(url, "no code source for " + type.getName());
        return url;
    }

    /** Invokes a one-String-argument method reflectively. */
    private static Object invoke1(Object target, String name, String arg)
            throws Exception {
        return target.getClass().getMethod(name, String.class)
                .invoke(target, arg);
    }

    // ------------------------------------------------------------------
    // Shared assertion: terminal cause is the actionable diagnostic.
    // ------------------------------------------------------------------

    /**
     * Asserts the call failed with the cross-loader diagnostic rather than a
     * raw JVM message or an escaping {@link LinkageError}.
     *
     * @param child the loader that owns the target type
     * @param type  the type the user asked to proxy
     * @param call  the proxy request
     */
    private static void assertCrossLoaderDiagnostic(ClassLoader child,
                                                    Class<?> type,
                                                    Executable call) {
        RuntimeException thrown = assertThrows(RuntimeException.class, call,
                "the failure must be an exception, not a LinkageError");
        Throwable root = rootCause(thrown);
        assertInstanceOf(IllegalArgumentException.class, root,
                "terminal cause must be the diagnostic, chain was: "
                        + chain(thrown));
        String message = root.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(type.getName()),
                "must name the target type: " + message);
        assertTrue(message.contains(child.getClass().getName()),
                "must name the loader owning the target: " + message);
        assertTrue(message.indexOf('@') != message.lastIndexOf('@'),
                "must name both loaders: " + message);
        assertTrue(message.contains("Options:"),
                "must state the remedies: " + message);
        assertNull(root.getCause(),
                "the diagnostic must be terminal, not a wrapper: " + root);
    }

    /** Walks to the deepest cause. */
    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Renders the cause chain for assertion messages. */
    private static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append("\n  ").append(c.getClass().getName())
              .append(": ").append(c.getMessage());
        }
        return sb.toString();
    }

    /** Loads a fixture type from the child loader. */
    private static Class<?> fixture(ChildFirstClassLoader child,
                                    String simpleName)
            throws ClassNotFoundException {
        return child.loadClass(CrossLoaderFixtures.PKG + "." + simpleName);
    }

    /** Rethrows a checked exception from a lambda. */
    @SuppressWarnings("unchecked")
    private static <T> T unchecked(ThrowingSupplier<T> body) {
        try {
            return body.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Supplier that may throw. */
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /** Interceptor that delegates to the real implementation. */
    private static Interceptor sup() {
        return (o, m, a) -> OpenProxy.invokeSuper(o, m, a);
    }

    /** Interceptor that returns a fixed value. */
    private static Interceptor konst(String value) {
        return (o, m, a) -> value;
    }
}

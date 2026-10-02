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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proxies a target in a real named module that {@code requires} the library's
 * automatic module and exports nothing. The library runs as an independent
 * copy inside the layer, so every call enters through
 * {@code com.acme.proxyable.ProxyDriver} reflectively and only JDK types
 * cross back.
 */
class NamedModuleProxyTest {

    private static final String DRIVER = "com.acme.proxyable.driver.ProxyDriver";

    private static NamedProxyModuleFixture fixture;

    @BeforeAll
    static void defineLayer() throws Exception {
        fixture = NamedProxyModuleFixture.create();
    }

    @AfterAll
    static void dropLayer() {
        if (fixture != null) {
            fixture.detach();
            fixture = null;
        }
    }

    @Test
    void premisesLayerIsolatedAndPackageUnexported() throws Exception {
        Class<?> widget = fixture.loadClass("com.acme.proxyable.Widget");
        assertTrue(widget.getModule().isNamed(), "premise: named module");
        assertEquals("com.acme.proxyable", widget.getModule().getName());
        assertFalse(widget.getModule().isExported("com.acme.proxyable"),
                "premise: the target package is exported to no one");
        assertTrue(widget.getModule().isExported("com.acme.proxyable.driver"),
                "premise: only the test entry package is open to callers");
        assertNotSame(NamedModuleProxyTest.class.getClassLoader(),
                widget.getClassLoader(),
                "premise: the layer never sees the classpath copies");
    }

    @Test
    void namedModuleTargetProxiesThroughDriver() throws Throwable {
        Class<?> driver = fixture.loadClass(DRIVER);
        assertEquals("SUPER:Hello, x",
                driver.getMethod("helloDelegating").invoke(null),
                "interception with invokeSuper must work inside the module");
    }

    @Test
    void generatedClassLandsInTargetModuleAndLayerLoader() throws Throwable {
        Class<?> driver = fixture.loadClass(DRIVER);
        String placement = (String) driver.getMethod("proxyPlacement")
                .invoke(null);
        assertEquals("com.acme.proxyable|same", placement,
                "the proxy must live in the target's module and loader");
    }

    @Test
    void sameLookupRootReusesOneCachedClassAcrossProxies() throws Throwable {
        Class<?> driver = fixture.loadClass(DRIVER);
        assertEquals("V1/V2",
                driver.getMethod("twoProxiesOneClass").invoke(null),
                "lookups rooted in the same class must share one cached "
                        + "proxy class while keeping per-instance state");
    }

    @Test
    void evictReleasesTheModuleLayersLoader() throws Throwable {
        WeakReference<ClassLoader> ref = proxyThenEvictInFreshLayer();
        for (int i = 0; i < 32 && ref.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(ref.get(),
                "after evictClassLoader plus GC the layer's loader must be "
                        + "collectable — the cache may not retain it across "
                        + "the module boundary");
    }

    /**
     * Builds a throwaway layer, proxies inside it, evicts the layer's loader
     * from inside, and returns a weak reference taken after every strong
     * handle in this frame dies.
     */
    private static WeakReference<ClassLoader> proxyThenEvictInFreshLayer()
            throws Exception {
        NamedProxyModuleFixture fresh = NamedProxyModuleFixture.create();
        try {
            Class<?> driver = fresh.loadClass(DRIVER);
            assertEquals("SUPER:Hello, x",
                    driver.getMethod("helloDelegating").invoke(null));
            @SuppressWarnings("unchecked")
            WeakReference<ClassLoader> ref = (WeakReference<ClassLoader>)
                    driver.getMethod("selfLoaderRef").invoke(null);
            driver.getMethod("evictSelf").invoke(null);
            return ref;
        } finally {
            fresh.detach();
        }
    }
}

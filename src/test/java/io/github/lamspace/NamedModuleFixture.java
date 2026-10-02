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

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compiles the fixture under {@code src/test/resources/crossmodule} into a
 * module directory and defines it as a real <em>named</em> module in its own
 * {@link ModuleLayer}, so tests can exercise the readability boundary that a
 * classpath (unnamed-module) loader never hits.
 */
final class NamedModuleFixture {

    private static final String MODULE_NAME = "com.acme.mod";

    private final ModuleLayer layer;

    private NamedModuleFixture(ModuleLayer layer) {
        this.layer = layer;
    }

    /**
     * Compiles and defines the fixture module against the test's own loader.
     *
     * @return a handle for loading classes and lookups from the module
     * @throws Exception if the sources do not compile or the module cannot be
     *                   defined
     */
    static NamedModuleFixture create() throws Exception {
        Path mods = compile();
        java.lang.module.ModuleFinder finder =
                java.lang.module.ModuleFinder.of(mods);
        java.lang.module.Configuration configuration = ModuleLayer.boot()
                .configuration().resolve(finder,
                        java.lang.module.ModuleFinder.of(),
                        Set.of(MODULE_NAME));
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(
                configuration, NamedModuleFixture.class.getClassLoader());
        return new NamedModuleFixture(layer);
    }

    /**
     * Loads a class from the fixture module.
     *
     * @param name binary name inside the module
     * @return the class, owned by the module layer's loader
     * @throws ClassNotFoundException if the module has no such class
     */
    Class<?> loadClass(String name) throws ClassNotFoundException {
        return loader().loadClass(name);
    }

    /**
     * Produces a full-privilege lookup rooted inside the fixture module.
     *
     * @param name binary name to root the lookup in
     * @return a lookup whose class lives in the named module
     * @throws Exception if the holder cannot produce the lookup
     */
    MethodHandles.Lookup lookupFor(String name) throws Exception {
        Class<?> holder = loader().loadClass(MODULE_NAME + ".LookupHolder");
        return (MethodHandles.Lookup) holder
                .getMethod("lookupFor", String.class).invoke(null, name);
    }

    /** The loader that owns every class in the fixture module. */
    private ClassLoader loader() {
        return layer.findLoader(MODULE_NAME);
    }

    /**
     * Compiles the module sources into {@code <temp>/mods}, where the module
     * directory {@code com.acme.mod} can be found by {@code ModuleFinder}.
     */
    private static Path compile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Named-module fixtures need "
                    + "jdk.compiler; run the tests on a JDK, not a JRE.");
        }
        URL resource = NamedModuleFixture.class.getResource("/crossmodule");
        if (resource == null) {
            throw new IllegalStateException("Missing test resource root "
                    + "/crossmodule");
        }
        // Scope to this module only: /crossmodule now also holds sibling
        // fixture modules (e.g. com.acme.proxyable) that compile against
        // the library on the module path, not here.
        Path root = Paths.get(resource.toURI()).resolve(MODULE_NAME);
        Path dir = Files.createTempDirectory("openproxy-namedmodule");
        Path src = dir.resolve("src");
        Path mods = dir.resolve("mods");
        Files.createDirectories(src);
        Files.createDirectories(mods);

        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(mods.resolve(MODULE_NAME).toString());
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> sources = walk.filter(p -> p.toString()
                    .endsWith(".java")).toList();
            if (sources.isEmpty()) {
                throw new IllegalStateException(
                        "No module sources under " + root);
            }
            for (Path p : sources) {
                Path dst = src.resolve(root.relativize(p).toString());
                Files.createDirectories(dst.getParent());
                Files.copy(p, dst);
                args.add(dst.toString());
            }
        }
        if (compiler.run(null, null, null, args.toArray(new String[0])) != 0) {
            throw new IllegalStateException(
                    "Module fixture failed to compile (see javac output)");
        }
        return mods;
    }
}

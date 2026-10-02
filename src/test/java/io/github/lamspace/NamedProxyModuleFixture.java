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

import org.objectweb.asm.ClassWriter;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Defines {@code com.acme.proxyable} (see
 * {@code src/test/resources/crossmodule/com.acme.proxyable}) together with a
 * synthesized module-path OpenProxy jar and the real ASM jar as a live
 * {@link ModuleLayer}, parented to the platform loader so the layer NEVER sees
 * the classpath copy of the library. This is the module-path deployment shape
 * measured in the change design: the target module only
 * {@code requires io.github.lamspace.openproxy;} — it exports nothing — and ASM
 * is a resolution root (the layer stands in for {@code --add-modules
 * org.objectweb.asm}, which an automatic module cannot pull in by itself).
 */
final class NamedProxyModuleFixture {

    private static final String MODULE_NAME = "com.acme.proxyable";
    private static final String ASM_MODULE_NAME = "org.objectweb.asm";
    private static final String MODULE_PATH_ATTR = "Automatic-Module-Name";
    private static final String LIBRARY_NAME = "io.github.lamspace.openproxy";

    private ModuleLayer layer;
    private ClassLoader loader;

    private NamedProxyModuleFixture(ModuleLayer layer) {
        this.layer = layer;
        this.loader = layer.findLoader(MODULE_NAME);
    }

    /**
     * Synthesizes the jar, compiles and defines the module layer.
     *
     * @return a handle for loading classes from the layer
     * @throws Exception if the library classes are missing, the fixture does
     *                   not compile, or the layer cannot be defined
     */
    static NamedProxyModuleFixture create() throws Exception {
        Path dir = Files.createTempDirectory("openproxy-namedproxy");
        Path openproxyJar = synthesizeLibraryJar(dir.resolve(
                "openproxy.jar"));
        Path asmJar = asmJar();
        Path mods = compile(dir.resolve("mods"), openproxyJar, asmJar);

        java.lang.module.ModuleFinder finder =
                java.lang.module.ModuleFinder.of(openproxyJar, asmJar, mods);
        java.lang.module.Configuration configuration = ModuleLayer.boot()
                .configuration().resolve(finder,
                        java.lang.module.ModuleFinder.of(),
                        Set.of(MODULE_NAME, ASM_MODULE_NAME));
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(
                configuration, ClassLoader.getPlatformClassLoader());
        return new NamedProxyModuleFixture(layer);
    }

    /**
     * Loads a class from the fixture module.
     *
     * @param name binary name inside the module
     * @return the class, owned by the layer's loader
     * @throws ClassNotFoundException if the module has no such class
     */
    Class<?> loadClass(String name) throws ClassNotFoundException {
        return loader.loadClass(name);
    }

    /** The single loader owning every class in the defined layer. */
    ClassLoader loader() {
        return loader;
    }

    /** Drops the strong handle on the layer so its loaders can be collected. */
    void detach() {
        layer = null;
        loader = null;
    }

    /**
     * Packages {@code target/classes} into a jar whose manifest carries the
     * {@code Automatic-Module-Name} the build now ships — the shipped
     * artifact shape, which an exploded directory cannot express.
     */
    private static Path synthesizeLibraryJar(Path out) throws Exception {
        URL classes = Interceptor.class.getProtectionDomain()
                .getCodeSource().getLocation();
        Path root = Paths.get(classes.toURI());
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Expected exploded classes for "
                    + "test packaging, got " + root);
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
                "1.0");
        manifest.getMainAttributes().putValue(MODULE_PATH_ATTR, LIBRARY_NAME);
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(out), manifest);
             Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)
                    ::iterator) {
                String entry = root.relativize(p).toString()
                        .replace(java.io.File.separatorChar, '/');
                jar.putNextEntry(new JarEntry(entry));
                Files.copy(p, jar);
                jar.closeEntry();
            }
        }
        return out;
    }

    /** The real ASM jar backing the test classpath. */
    private static Path asmJar() throws URISyntaxException {
        URL url = ClassWriter.class.getProtectionDomain().getCodeSource()
                .getLocation();
        Path jar = Paths.get(url.toURI());
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Expected ASM on a jar for the "
                    + "module path, got " + jar);
        }
        return jar;
    }

    /**
     * Compiles the proxyable fixture sources against the synthesized library
     * on the module path.
     */
    private static Path compile(Path modsDir, Path openproxyJar, Path asmJar)
            throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Module fixtures need "
                    + "jdk.compiler; run the tests on a JDK, not a JRE.");
        }
        URL resource = NamedProxyModuleFixture.class
                .getResource("/crossmodule/" + MODULE_NAME);
        if (resource == null) {
            throw new IllegalStateException("Missing test resource module "
                    + "/crossmodule/" + MODULE_NAME);
        }
        Path srcRoot = Paths.get(resource.toURI());
        Path out = modsDir.resolve(MODULE_NAME);
        Files.createDirectories(out);

        List<String> args = new ArrayList<>();
        args.add("--module-path");
        args.add(openproxyJar + java.io.File.pathSeparator + asmJar);
        args.add("-d");
        args.add(out.toString());
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            sources = walk.filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException(
                    "No module sources under " + srcRoot);
        }
        for (Path p : sources) {
            args.add(p.toString());
        }
        if (compiler.run(null, null, null, args.toArray(new String[0])) != 0) {
            throw new IllegalStateException(
                    "Proxyable module fixture failed to compile "
                            + "(see javac output)");
        }
        return modsDir;
    }
}

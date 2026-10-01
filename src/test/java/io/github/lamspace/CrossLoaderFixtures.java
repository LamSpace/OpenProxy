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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Compiles the cross-loader fixture sources under
 * {@code src/test/resources/crossloader} into a temporary directory that is
 * never on the test classpath, and hands out a {@link ChildFirstClassLoader}
 * over it.
 *
 * <p>The fixtures stay out of {@code target/test-classes} on purpose: a
 * fixture the parent loader can resolve would let every cross-loader assertion
 * pass while testing nothing.
 */
final class CrossLoaderFixtures {

    /** Package the fixture sources declare. */
    static final String PKG = "com.acme.child";

    /** Fixture source directory, as a classpath resource root. */
    private static final String RES_DIR = "crossloader";

    private static URL[] cachedUrls;

    private CrossLoaderFixtures() {
        // static utility
    }

    /**
     * Returns a loader owning the fixture types, with the fixture package
     * resolved child-first so the parent can never satisfy it.
     *
     * @return a child loader over the compiled fixtures
     * @throws Exception if the fixtures are missing or do not compile
     */
    /** Second fixture package, used for the lookup-package rule. */
    static final String OTHER_PKG = "com.acme.other";

    static ChildFirstClassLoader childLoader() throws Exception {
        return new ChildFirstClassLoader(urls(),
                CrossLoaderFixtures.class.getClassLoader(),
                PKG + ".", OTHER_PKG + ".");
    }

    /**
     * Returns a loader that defines its own copies of the given prefix from the
     * test's own compiled classes, so a parent-visible type can also be loaded
     * by a second loader as a distinct {@code Class}.
     *
     * @param prefix the type name prefix to shadow child-first
     * @return a loader shadowing {@code prefix}
     */
    static ChildFirstClassLoader shadowLoader(String prefix) {
        return new ChildFirstClassLoader(
                new URL[]{testClassesDir()},
                CrossLoaderFixtures.class.getClassLoader(), prefix);
    }

    /**
     * Compiles the fixture sources once per JVM into a temp directory.
     *
     * @return URLs of the compiled fixture classes
     * @throws Exception if the fixtures are missing or do not compile
     */
    static synchronized URL[] urls() throws Exception {
        if (cachedUrls == null) {
            cachedUrls = new URL[]{compile().toUri().toURL()};
        }
        return cachedUrls.clone();
    }

    /**
     * Compiles every {@code .java} under the fixture resource root into
     * {@code <temp>/classes}, returning that directory.
     */
    private static Path compile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Cross-loader fixtures need "
                    + "jdk.compiler; run the tests on a JDK, not a JRE.");
        }
        Path root = resourceRoot();
        Path dir = Files.createTempDirectory("openproxy-crossloader");
        Path src = dir.resolve("src");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(out.toString());
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> sources = walk
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            if (sources.isEmpty()) {
                throw new IllegalStateException(
                        "No fixture sources under " + root);
            }
            for (Path p : sources) {
                Path dst = src.resolve(root.relativize(p).toString());
                Files.createDirectories(dst.getParent());
                Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                args.add(dst.toString());
            }
        }
        if (compiler.run(null, null, null, args.toArray(new String[0])) != 0) {
            throw new IllegalStateException(
                    "Fixture sources failed to compile (see javac output)");
        }
        return out;
    }

    /**
     * Locates the fixture source directory as an exploded directory on disk.
     */
    private static Path resourceRoot() throws Exception {
        URL url = CrossLoaderFixtures.class.getResource("/" + RES_DIR);
        if (url == null) {
            throw new IllegalStateException("Missing test resource root /"
                    + RES_DIR);
        }
        return Paths.get(url.toURI());
    }

    /**
     * Returns the directory holding this test's own compiled classes.
     */
    private static URL testClassesDir() {
        try {
            return CrossLoaderFixtures.class
                    .getProtectionDomain().getCodeSource().getLocation();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot locate the test classes directory", e);
        }
    }
}

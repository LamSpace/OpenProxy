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

package io.github.lamspace.internal;

/**
 * Builds the actionable error reported when a proxy cannot be generated
 * because the hidden class cannot live in a loader that sees both the
 * library's contract types and the target type.
 *
 * <p>The generated exception deliberately carries <em>no</em> cause: the
 * {@code IllegalAccessException} or {@code ClassNotFoundException} that
 * triggered it is summarized in the message instead, so the terminal cause of
 * the failure is this diagnostic rather than a JVM message a caller would
 * have to interpret on its own.
 */
public final class CrossLoaderDiagnostics {

    private CrossLoaderDiagnostics() {
        // static utility
    }

    /**
     * Returns the diagnostic for a type that cannot be proxied from the loader
     * that would have to define the generated class.
     *
     * @param type           the target class or interface the user asked about
     * @param typeLoader     the loader that owns {@code type}; may be
     *                       {@code null} for the bootstrap loader
     * @param definingLoader the loader that would own the generated proxy class
     * @param jvmDetail      the underlying JVM failure summary, or {@code null}
     *                       when the check raised the condition itself
     * @return an {@code IllegalArgumentException} naming both loaders and the
     *         remedies available in this release
     */
    public static IllegalArgumentException forType(Class<?> type,
                                                   ClassLoader typeLoader,
                                                   ClassLoader definingLoader,
                                                   String jvmDetail) {
        String message = "Cannot create a proxy for " + type.getName()
                + ": the type is owned by " + describe(typeLoader)
                + " while the OpenProxy classes that define the proxy are "
                + "owned by " + describe(definingLoader)
                + ". A hidden proxy class must be defined in a loader that "
                + "can see both. Options: (1) make " + type.getName()
                + " resolvable from " + describe(definingLoader) + "; or (2) "
                + "load openproxy and ASM from " + describe(typeLoader)
                + " and create the Interceptor there too, so the interceptor "
                + "and the proxy share one loader. Cross-ClassLoader proxying "
                + "with a caller-supplied definer is tracked in ROADMAP.md "
                + "(support-cross-classloader-proxy).";
        if (jvmDetail != null) {
            message = message + " Reported by the JVM: " + jvmDetail;
        }
        return new IllegalArgumentException(message);
    }

    /**
     * Renders a loader for the message, naming the bootstrap loader and the
     * class that owns any other.
     */
    private static String describe(ClassLoader loader) {
        if (loader == null) {
            return "the bootstrap loader";
        }
        return loader.getClass().getName() + "@" + Integer.toHexString(
                System.identityHashCode(loader));
    }
}

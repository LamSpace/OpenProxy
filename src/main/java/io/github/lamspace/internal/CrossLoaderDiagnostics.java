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
                + "and the proxy share one loader. To proxy a type whose loader "
                + "the library cannot see, pass a definition Lookup: "
                + "OpenProxy.proxy(target, privateLookupIn(target, ...), "
                + "interceptor) — see docs/guide/11-jpms.md.";
        if (jvmDetail != null) {
            message = message + " Reported by the JVM: " + jvmDetail;
        }
        return new IllegalArgumentException(message);
    }

    /**
     * Returns the diagnostic for a supplied {@code Lookup} that cannot define
     * a proxy for the given type.
     *
     * @param type        the target type the user asked to proxy
     * @param lookupClass the class the supplied lookup is rooted in
     * @param reason      why that lookup cannot serve this request
     * @return an {@code IllegalArgumentException} naming the lookup class, its
     *         package and loader, and the target's loader
     */
    public static IllegalArgumentException forLookup(Class<?> type,
                                                    Class<?> lookupClass,
                                                    String reason) {
        return new IllegalArgumentException(
                "Cannot create a proxy for " + type.getName()
                + ": the supplied Lookup is rooted in " + lookupClass.getName()
                + " (package " + lookupClass.getPackageName() + ", loader "
                + describe(lookupClass.getClassLoader()) + "), while the target"
                + " is owned by " + describe(type.getClassLoader()) + ". "
                + reason);
    }

    /**
     * Returns the diagnostic for a supplied {@code Lookup} whose module cannot
     * read the module owning one of the library's contract types. A hidden
     * class inherits its lookup class's module, so without readability the
     * JVM rejects the proxy's superinterface check with an
     * {@code IllegalAccessError} — an {@code Error} no caller can catch
     * usefully.
     *
     * @param type          the target type the user asked to proxy
     * @param contractType  the library type the generated class references
     * @param lookupModule  the module the supplied lookup is rooted in
     * @return an {@code IllegalArgumentException} naming both modules
     */
    public static IllegalArgumentException forModuleRead(Class<?> type,
                                                         Class<?> contractType,
                                                         Module lookupModule) {
        return new IllegalArgumentException(
                "Cannot create a proxy for " + type.getName() + ": module "
                + lookupModule.getName() + " cannot read module "
                + describeModule(contractType.getModule()) + ", which owns "
                + contractType.getName() + " — every generated proxy "
                + "implements the library's types. OpenProxy is currently "
                + "published as a classpath artifact (unnamed module), and a "
                + "named module cannot read the unnamed module, so a target in"
                + " a named module is not proxyable until OpenProxy itself "
                + "ships a module declaration that the target module "
                + "requires.");
    }

    /**
     * Returns the diagnostic for a supplied {@code Lookup} whose loader cannot
     * resolve one of the library's contract types at all.
     *
     * @param type         the target type the user asked to proxy
     * @param contractType the library type the generated class references
     * @param typeLoader   the loader that failed to resolve it
     * @return an {@code IllegalArgumentException} naming both loaders
     */
    public static IllegalArgumentException forLibraryType(Class<?> type,
                                                          Class<?> contractType,
                                                          ClassLoader typeLoader) {
        return new IllegalArgumentException(
                "Cannot create a proxy for " + type.getName() + ": the loader "
                + describe(typeLoader) + " cannot resolve "
                + contractType.getName() + ", which every generated proxy "
                + "implements (owned by "
                + describe(contractType.getClassLoader()) + "). Delegate to "
                + "the library's loader from that loader, or load openproxy "
                + "and ASM into it and create the Interceptor there too.");
    }

    /**
     * Renders a module for the message, since the unnamed module has no name.
     */
    private static String describeModule(Module module) {
        String name = module.getName();
        return name != null ? name : "the unnamed module @" + Integer.toHexString(
                System.identityHashCode(module));
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

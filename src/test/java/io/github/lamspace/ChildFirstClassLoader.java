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

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Test loader that resolves configured package prefixes from its own URLs
 * before delegating, so a type can end up owned by the child even when the
 * parent also has it on its classpath.
 *
 * <p>Parent-first delegation is what makes cross-loader fixtures easy to get
 * wrong: if the parent can resolve the fixture type, every "child loader" case
 * silently degenerates into a same-loader case and the test passes vacuously.
 * Child-first resolution supports the opposite shape too — the parent resolving
 * the same binary name to a <em>different</em> {@code Class}.
 */
class ChildFirstClassLoader extends URLClassLoader {

    private final String[] childFirstPrefixes;

    ChildFirstClassLoader(URL[] urls, ClassLoader parent,
                          String... childFirstPrefixes) {
        super(urls, parent);
        this.childFirstPrefixes = childFirstPrefixes.clone();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null && wants(name)) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException notInSelf) {
                    loaded = null;   // fall back to normal delegation
                }
            }
            if (loaded == null) {
                loaded = super.loadClass(name, false);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    /** True if {@code name} belongs to a prefix this loader must own. */
    private boolean wants(String name) {
        for (String prefix : childFirstPrefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

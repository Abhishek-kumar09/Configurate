/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate;

import org.checkerframework.checker.initialization.qual.NotOnlyInitialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Iterator;
import java.util.Objects;

/**
 * The value in a {@link ConfigurationNode}.
 */
abstract class ConfigValue<N extends ScopedConfigurationNode<N>, T extends AbstractConfigurationNode<N, T>> {

    /**
     * The node this value "belongs" to.
     */
    protected final @NotOnlyInitialized T holder;

    protected ConfigValue(@UnknownInitialization T holder) {
        this.holder = holder;
    }

    abstract ValueType getType();

    /**
     * Gets the value encapsulated by this instance
     *
     * @return The value
     */
    abstract @Nullable Object getValue();

    /**
     * Sets the value encapsulated by this instance
     *
     * @param value The value
     */
    abstract void setValue(Object value);

    /**
     * Put a child value, or null to remove value at that key
     *
     * @param key The key
     * @param value The node to put at key
     * @return Existing node at key, if present
     */
    abstract @Nullable T putChild(Object key, @Nullable T value);

    /**
     * Put a child value, if one isn't already present at that key
     *
     * @param key The key
     * @param value The node to put at key
     * @return Existing node at key, if present
     */
    abstract @Nullable T putChildIfAbsent(Object key, @Nullable T value);

    /**
     * Gets the currently present child for the given key. Returns null if no child is present
     *
     * @param key The key to get child at
     * @return The child if any
     */
    abstract @Nullable T getChild(Object key);

    /**
     * Returns an iterable over all child nodes
     *
     * @return An iterator
     */
    abstract Iterable<T> iterateChildren();

    /**
     * Creates a copy of this node
     *
     * @return A copy
     */
    abstract ConfigValue<N, T> copy(T holder);

    /**
     * Whether this value has any content
     *
     * @return The value
     */
    abstract boolean isEmpty();

    /**
     * Clears the set value (or any attached child values) from this value
     */
    void clear() {
        for (Iterator<T> it = iterateChildren().iterator(); it.hasNext();) {
            T node = it.next();
            node.attached = false;
            it.remove();
            if (Objects.equals(node.getParentEnsureAttached(), holder)) {
                node.clear();
            }
        }
    }

}

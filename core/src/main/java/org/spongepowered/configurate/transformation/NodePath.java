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
package org.spongepowered.configurate.transformation;

import java.util.Collection;
import java.util.Iterator;

/**
 * Represents the path to a given node.
 */
public interface NodePath extends Iterable<Object>, Cloneable {

    /**
     * Gets a specific element from the path array.
     *
     * @param i The index to get
     * @return Object at the index
     */
    Object get(int i);

    /**
     * Gets the length of the path.
     *
     * @return Length of the path array
     */
    int size();

    /**
     * Returns a copy of the original path array.
     *
     * @return the copied array
     */
    Object[] getArray();

    /**
     * Create a new path with the provided element appended to the end.
     *
     * @param childKey The new key to append
     * @return A new path object reflecting the extended path
     */
    NodePath withAppendedChild(Object childKey);

    /**
     * Create a new path with the value at {@code index} replaced
     * with {@code value}.
     *
     * @param index position to change
     * @param value value to insert
     * @return new path object with changed value
     * @throws IndexOutOfBoundsException if index &lt; 0 or &ge; {@linkplain #size()}
     */
    NodePath with(int index, Object value) throws IndexOutOfBoundsException;

    /**
     * Returns an iterator over the path.
     *
     * @return An iterator of the path
     */
    @Override
    Iterator<Object> iterator();

    /**
     * Create a node path reference.
     *
     * @param path The path to reference. The provided array will be copied.
     * @return The path instance
     */
    static NodePath create(Object[] path) {
        return new NodePathImpl(path, true);
    }

    /**
     * Create a node path reference.
     *
     * @param path A collection containing elements of the path to reference
     * @return The path instance
     */
    static NodePath create(Collection<?> path) {
        return new NodePathImpl(path.toArray(), false);
    }

    /**
     * Create a node path reference.
     *
     * <p>This overload takes varargs, and is designed to be imported statically
     * to create paths.
     *
     * @param elements The path to reference. The provided array will be copied.
     * @return The path instance
     */
    static NodePath path(Object... elements) {
        return create(elements);
    }

    /**
     * Create a new node path with the same data as this path.
     *
     * @return The resulting path
     */
    NodePath clone();

}

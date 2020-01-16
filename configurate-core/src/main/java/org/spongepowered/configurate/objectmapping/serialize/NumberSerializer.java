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
package org.spongepowered.configurate.objectmapping.serialize;

import com.google.common.reflect.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ObjectMappingException;

import java.util.function.Predicate;

class NumberSerializer implements TypeSerializer<Number> {

    public static Predicate<TypeToken<Number>> getPredicate() {
        return (type) -> {
            type = type.wrap();
            Class<?> clazz = type.getRawType();
            return Integer.class.equals(clazz)
                    || Long.class.equals(clazz)
                    || Short.class.equals(clazz)
                    || Byte.class.equals(clazz)
                    || Float.class.equals(clazz)
                    || Double.class.equals(clazz);
        };
    }

    @Override
    public <Node extends ConfigurationNode<Node>> Number deserialize(@NonNull TypeToken<?> type, @NonNull Node value) throws ObjectMappingException {
        type = type.wrap();
        Class<?> clazz = type.getRawType();
        if (Integer.class.equals(clazz)) {
            return value.getInt();
        } else if (Long.class.equals(clazz)) {
            return value.getLong();
        } else if (Short.class.equals(clazz)) {
            return (short) value.getInt();
        } else if (Byte.class.equals(clazz)) {
            return (byte) value.getInt();
        } else if (Float.class.equals(clazz)) {
            return value.getFloat();
        } else if (Double.class.equals(clazz)) {
            return value.getDouble();
        }
        return null;
    }

    @Override
    public <T extends ConfigurationNode<T>> void serialize(@NonNull TypeToken<?> type, @Nullable Number obj, @NonNull T value) throws ObjectMappingException {
        value.setValue(obj);
    }
}

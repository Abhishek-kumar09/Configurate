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
import org.spongepowered.configurate.ScopedConfigurationNode;
import org.spongepowered.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.configurate.util.EnumLookup;

import java.util.Optional;

class EnumValueSerializer implements TypeSerializer<Enum<?>> {
    @Override
    public <Node extends ScopedConfigurationNode<Node>> Enum<?> deserialize(@NonNull TypeToken<?> type, @NonNull Node node) throws ObjectMappingException {
        String enumConstant = node.getString();
        if (enumConstant == null) {
            throw new ObjectMappingException("No value present in node " + node);
        }

        @SuppressWarnings("unchecked")
        Optional<? extends Enum<?>> ret = EnumLookup.lookupEnum(type.getRawType().asSubclass(Enum.class),
                enumConstant);
        if (!ret.isPresent()) {
            throw new ObjectMappingException("Invalid enum constant provided for " + node.getKey() + ": " +
                    "Expected a value of enum " + type + ", got " + enumConstant);
        }
        return ret.get();
    }

    @Override
    public <T extends ScopedConfigurationNode<T>> void serialize(@NonNull TypeToken<?> type, @Nullable Enum<?> obj, @NonNull T node) throws ObjectMappingException {
        node.setValue(obj == null ? null : obj.name());
    }
}

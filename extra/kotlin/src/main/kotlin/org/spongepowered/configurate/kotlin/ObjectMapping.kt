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
package org.spongepowered.configurate.kotlin

import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.objectmapping.ObjectMapper
import org.spongepowered.configurate.objectmapping.ObjectMapperFactory
import org.spongepowered.configurate.serialize.TypeSerializer
import org.spongepowered.configurate.serialize.TypeSerializerCollection

/**
 * Get an object mapper for the type [T] using the default object mapper factory
 */
inline fun <reified T> objectMapper(): ObjectMapper<T> {
    return ObjectMapper.forType(typeTokenOf<T>())
}

/**
 * Get an object mapper bound to the instance of [T], resolving type parameters
 */
inline fun <reified T> T.mapper(): ObjectMapper<T>.BoundInstance {
    return ObjectMapper.forObject(typeTokenOf<T>(), this)
}

/**
 * Create an object mapper with the given [ObjectMapperFactory] for objects of type [T],
 * accepting parameterized types.
 */
inline fun <reified T> ObjectMapperFactory.getMapper(): ObjectMapper<T> {
    return getMapper(typeTokenOf())
}

/**
 * Get the appropriate [TypeSerializer] for the provided type [T], or null if none is applicable.
 */
inline fun <reified T> TypeSerializerCollection.get(): TypeSerializer<T>? {
    return get(typeTokenOf())
}

@PublishedApi
internal inline fun <reified T> typeTokenOf() = object : TypeToken<T>() {}

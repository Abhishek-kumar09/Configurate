/**
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
package ninja.leaping.configurate;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.DefaultObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.ObjectMapperFactory;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import ninja.leaping.configurate.util.MapFactories;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * This object is a holder for general configuration options. This is meant to hold options
 * that are used in configuring how the configuration data structures are handled, rather than the serialization configuration that is located in {@link ConfigurationLoader}s
 */
public class ConfigurationOptions {
    private final Supplier<ConcurrentMap<Object, SimpleConfigurationNode>> mapSupplier;
    private final String header;
    private final TypeSerializerCollection serializers;
    private final ImmutableSet<Class<?>> acceptedTypes;
    private final ObjectMapperFactory objectMapperFactory;

    private ConfigurationOptions(Supplier<ConcurrentMap<Object, SimpleConfigurationNode>> mapSupplier, String header,
     TypeSerializerCollection serializers, Set<Class<?>> acceptedTypes, ObjectMapperFactory objectMapperFactory) {
        this.mapSupplier = mapSupplier;
        this.header = header;
        this.serializers = serializers;
        this.acceptedTypes = acceptedTypes == null ? null : ImmutableSet.copyOf(acceptedTypes);
        this.objectMapperFactory = objectMapperFactory;
    }

    /**
     * Create a new options object with defaults set
     *
     * @return A new default options object
     */
    public static ConfigurationOptions defaults() {
        return new ConfigurationOptions(MapFactories.<SimpleConfigurationNode>insertionOrdered(), null, TypeSerializers
                .getDefaultSerializers(), null, DefaultObjectMapperFactory.getInstance());
    }

    /**
     * Get the key comparator currently being used for this configuration
     *
     * @return The active key comparator
     */
    @SuppressWarnings("unchecked")
    public Supplier<ConcurrentMap<Object, ? extends ConfigurationNode>> getMapFactory() {
        return (Supplier) mapSupplier;
    }

    /**
     * Return a new options object with the provided option set.
     *
     * @param factory The new factory to use to create a map
     * @return The new options object
     */
    @SuppressWarnings("unchecked")
    public ConfigurationOptions setMapFactory(Supplier<ConcurrentMap<Object, ConfigurationNode>> factory) {
        Preconditions.checkNotNull(factory, "factory");
        return new ConfigurationOptions((Supplier) factory, header, serializers, acceptedTypes, objectMapperFactory);
    }

    /**
     * Get the header used for this configuration
     *
     * @return The current header. Lines are split by \n,
     */
    public String getHeader() {
        return this.header;
    }

    /**
     * Set the header that will be written to a file if
     * @param header The new header to use for the configuration
     * @return The map's header
     */
    public ConfigurationOptions setHeader(String header) {
        return new ConfigurationOptions(mapSupplier, header, serializers, acceptedTypes, objectMapperFactory);
    }

    public TypeSerializerCollection getSerializers() {
        return this.serializers;
    }

    /**
     * Set the collection of TypeSerializers to be used for lookups
     *
     * @param serializers The serializers to use
     * @return updated options object
     */
    public ConfigurationOptions setSerializers(TypeSerializerCollection serializers) {
        return new ConfigurationOptions(mapSupplier, header, serializers, acceptedTypes, objectMapperFactory);
    }

    /**
     * Get the current object mapper factory that is most appropriate to this configuration.
     *
     * @return The factory used to construct ObjectMapper instances
     */
    public ObjectMapperFactory getObjectMapperFactory() {
        return this.objectMapperFactory;
    }

    /**
     * Set the factory to use to produce object mapper instances for this configuration
     *
     * @param factory The factory to use to produce object mapper instances. Must not be null
     * @return updated options object
     */
    public ConfigurationOptions setObjectMapperFactory(ObjectMapperFactory factory) {
        Preconditions.checkNotNull(factory, "factory");
        return new ConfigurationOptions(mapSupplier, header, serializers, acceptedTypes, factory);
    }

    /**
     * Return whether objects of the provided type are accepted as values for nodes with this as their options object.
     *
     * @param type The type to check
     * @return Whether the type is accepted
     */
    public boolean acceptsType(Class<?> type) {
        if (this.acceptedTypes == null) {
            return true;
        }
        if (this.acceptedTypes.contains(type)) {
            return true;
        }

        for (Class<?> clazz : this.acceptedTypes) {
            if (clazz.isAssignableFrom(type)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Set types that will be accepted as native values for this configuration
     * @param acceptedTypes The types that will be accepted to a call to {@link ConfigurationNode#setValue(Object)}
     * @return updated options object
     */
    public ConfigurationOptions setAcceptedTypes(Set<Class<?>> acceptedTypes) {
        return new ConfigurationOptions(mapSupplier, header, serializers, acceptedTypes, objectMapperFactory);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigurationOptions that = (ConfigurationOptions) o;
        return Objects.equal(mapSupplier, that.mapSupplier) &&
                Objects.equal(header, that.header);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mapSupplier, header);
    }
}

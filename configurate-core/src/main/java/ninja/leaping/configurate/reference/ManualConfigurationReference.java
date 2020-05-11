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
package ninja.leaping.configurate.reference;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.reactive.Processor;
import ninja.leaping.configurate.reactive.Publisher;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A reference to a configuration node, that may or may not be updating
 */
class ManualConfigurationReference<N extends ConfigurationNode> implements ConfigurationReference<N> {
    protected volatile @MonotonicNonNull N node;
    private final ConfigurationLoader<? extends N> loader;
    protected final Processor.TransactionalIso<N> updateListener;
    protected final Processor.Iso<Map.Entry<ErrorPhase, Throwable>> errorListener;

    ManualConfigurationReference(ConfigurationLoader<? extends N> loader, Executor taskExecutor) {
        this.loader = loader;
        updateListener = Processor.createTransactional(taskExecutor);
        errorListener = Processor.create(taskExecutor);
        errorListener.setFallbackHandler(it -> {
            System.out.println("Unhandled error while performing a " + it.getKey() + " for a " +
                "configuration reference: " + it.getValue());
            it.getValue().printStackTrace();
        });
    }

    @Override
    public void load() throws IOException {
        synchronized (this.loader) {
            updateListener.submit(node = loader.load());
        }
    }

    @Override
    public void save() throws IOException {
        save(this.node);
    }

    @Override
    public void save(N newNode) throws IOException {
        synchronized (this.loader) {
            loader.save(this.node = requireNonNull(newNode));
        }
    }

    @Override
    public Publisher<N> saveAsync() {
        return Publisher.execute(() -> {
                save();
                return getNode();
        }, updateListener.getExecutor());
    }

    @Override
    public Publisher<N> updateAsync(Function<N, ? extends N> updater) {
        return Publisher.execute(() -> {
            final N newNode = updater.apply(getNode());
            save(newNode);
            return newNode;
        }, updateListener.getExecutor());
    }

    @Override
    public N getNode() {
        return this.node;
    }

    @Override
    public ConfigurationLoader<? extends N> getLoader() {
        return this.loader;
    }

    @Override
    @SuppressWarnings("unchecked")
    public N get(Object... path) {
        return (N) getNode().getNode(path);
    }

    @Override
    public <T> ValueReference<T> referenceTo(TypeToken<T> type, Object[] path, @Nullable T def) throws ObjectMappingException {
        return new ValueReferenceImpl<>(this, path, type, def);
    }

    @Override
    public <T> ValueReference<T> referenceTo(Class<T> type, Object[] path, @Nullable T def) throws ObjectMappingException {
        return new ValueReferenceImpl<>(this, path, type, def);
    }

    @Override
    public Publisher<N> updates() {
        return updateListener;
    }

    @Override
    public Publisher<Map.Entry<ErrorPhase, Throwable>> errors() {
        return errorListener;
    }

    @Override
    public void close() {
        updateListener.onClose();
    }

}

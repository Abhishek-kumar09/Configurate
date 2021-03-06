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
package org.spongepowered.configurate.reference;

import static java.util.Objects.requireNonNull;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ScopedConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.reactive.Disposable;
import org.spongepowered.configurate.reactive.Subscriber;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

/**
 * A wrapper around NIO's {@link WatchService} that uses the provided watch key
 * to poll for changes, and calls listeners once an event occurs.
 *
 * <p>Some deduplication is performed because Windows can be fairly spammy with
 * its events, so one callback may receive multiple events at one time.
 *
 * <p>Callback functions are {@link Subscriber Subscribers} that take the
 * {@link WatchEvent} as their parameter.
 *
 * <p>Listening to a directory provides updates on the directory's immediate
 * children, but does not listen recursively.
 */
public final class WatchServiceListener implements AutoCloseable {

    @SuppressWarnings("rawtypes") // IntelliJ says it's unnecessary, but the compiler shows warnings
    private static final WatchEvent.Kind<?>[] DEFAULT_WATCH_EVENTS = new WatchEvent.Kind[]{StandardWatchEventKinds.OVERFLOW,
        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};
    private static final int PARALLEL_THRESHOLD = 100;
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new PrefixedNameThreadFactory("Configurate-WatchService", true);

    private final WatchService watchService;
    private volatile boolean open = true;
    private final Thread executor;
    final Executor taskExecutor;
    private final ConcurrentHashMap<Path, DirectoryListenerRegistration> activeListeners = new ConcurrentHashMap<>();
    private static final ThreadLocal<IOException> exceptionHolder = new ThreadLocal<>();

    /**
     * Returns a new builder for a WatchServiceListener to create a
     * customized listener.
     *
     * @return A builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new {@link WatchServiceListener} using a new cached thread pool
     * executor and the default filesystem.
     *
     * @return A new instance with default values
     * @throws IOException If a watch service cannot be created
     * @see #builder() for customization
     */
    public static WatchServiceListener create() throws IOException {
        return new WatchServiceListener(DEFAULT_THREAD_FACTORY, FileSystems.getDefault(), ForkJoinPool.commonPool());
    }

    private WatchServiceListener(final ThreadFactory factory, final FileSystem fileSystem, final Executor taskExecutor) throws IOException {
        this.watchService = fileSystem.newWatchService();
        this.executor = factory.newThread(() -> {
            while (this.open) {
                final WatchKey key;
                try {
                    key = this.watchService.take();
                } catch (final InterruptedException e) {
                    this.open = false;
                    Thread.currentThread().interrupt();
                    break;
                } catch (final ClosedWatchServiceException e) {
                    break;
                }
                final Path watched = (Path) key.watchable();
                final DirectoryListenerRegistration registration = this.activeListeners.get(watched);
                if (registration != null) {
                    final Set<Object> seenContexts = new HashSet<>();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (!key.isValid()) {
                            break;
                        }

                        if (!seenContexts.add(event.context())) {
                            continue;
                        }

                        // Process listeners
                        registration.submit(event);
                        if (registration.closeIfEmpty()) {
                            key.cancel();
                            break;
                        }
                    }

                    // If the watch key is no longer valid, send all listeners a close event
                    if (!key.reset()) {
                        final DirectoryListenerRegistration oldListeners = this.activeListeners.remove(watched);
                        oldListeners.onClose();
                    }
                }
                try {
                    Thread.sleep(20);
                } catch (final InterruptedException e) {
                    break;
                }
            }
        });
        this.taskExecutor = taskExecutor;
        this.executor.start();
    }

    /**
     * Gets or creates a registration holder for a specific directory. This
     * handles registering with the watch service if necessary.
     *
     * @param directory The directory to listen to
     * @return A registration, created new if necessary.
     * @throws IOException If produced while registering the path with
     *          our WatchService
     */
    private DirectoryListenerRegistration getRegistration(final Path directory) throws IOException {
        final @Nullable DirectoryListenerRegistration reg = this.activeListeners.computeIfAbsent(directory, dir -> {
            try {
                return new DirectoryListenerRegistration(dir.register(this.watchService, DEFAULT_WATCH_EVENTS), this.taskExecutor);
            } catch (final IOException ex) {
                exceptionHolder.set(ex);
                return null;
            }
        });

        if (reg == null) {
            throw exceptionHolder.get();
        }
        return reg;
    }

    /**
     * Listen for changes to a specific file or directory.
     *
     * @param file The path of the file or directory to listen for changes on.
     * @param callback A subscriber that will be notified when changes occur.
     * @return A {@link Disposable} that can be used to cancel this subscription
     * @throws IOException if a filesystem error occurs.
     * @throws IllegalArgumentException if the provided path is a directory.
     */
    public Disposable listenToFile(Path file, final Subscriber<WatchEvent<?>> callback) throws IOException, IllegalArgumentException {
        file = file.toAbsolutePath();
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path " + file + " must be a file");
        }

        final Path fileName = file.getFileName();
        return getRegistration(file.getParent()).subscribe(fileName, callback);
    }

    /**
     * Listen to a directory. Callbacks will receive events both for the
     * directory and for its contents.
     *
     * @param directory The directory to listen to
     * @param callback A subscriber that will be notified when changes occur.
     * @return A {@link Disposable} that can be used to cancel this subscription
     * @throws IOException When an error occurs registering with the underlying
     *          watch service.
     * @throws IllegalArgumentException If the provided path is not a directory
     */
    public Disposable listenToDirectory(Path directory, final Subscriber<WatchEvent<?>> callback) throws IOException, IllegalArgumentException {
        directory = directory.toAbsolutePath();
        if (!(Files.isDirectory(directory) || !Files.exists(directory))) {
            throw new IllegalArgumentException("Path " + directory + " must be a directory");
        }

        return getRegistration(directory).subscribe(callback);
    }

    public <N extends ScopedConfigurationNode<N>> ConfigurationReference<N>
        listenToConfiguration(final Function<Path, ConfigurationLoader<? extends N>> loaderFunc, final Path path) throws IOException {
        return ConfigurationReference.createWatching(loaderFunc, path, this);
    }

    @Override
    public void close() throws IOException {
        this.open = false;
        this.watchService.close();
        this.activeListeners.forEachValue(PARALLEL_THRESHOLD, DirectoryListenerRegistration::onClose);
        this.activeListeners.clear();
        try {
            this.executor.interrupt();
            this.executor.join();
        } catch (final InterruptedException e) {
            throw new IOException("Failed to await termination of executor thread!");
        }
    }

    /**
     * Set the parameters needed to create a {@link WatchServiceListener}. All params are optional and defaults will be
     * used if no values are specified.
     */
    public static final class Builder {

        private @Nullable ThreadFactory threadFactory;
        private @Nullable FileSystem fileSystem;
        private @Nullable Executor taskExecutor;

        private Builder() { }

        /**
         * Set the thread factory that will be used to create the polling thread
         * for the returned watch service.
         *
         * @param factory The thread factory to create the deamon thread
         * @return this
         */
        public Builder setThreadFactory(final ThreadFactory factory) {
            this.threadFactory = requireNonNull(factory, "factory");
            return this;
        }

        /**
         * Set the executor that will be used to execute tasks queued based on
         * received events. By default, the
         * {@link ForkJoinPool#commonPool() common pool} is used.
         *
         * @param executor The executor to use
         * @return this
         */
        public Builder setTaskExecutor(final Executor executor) {
            this.taskExecutor = requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Set the filesystem expected to be used for paths. A separate
         * {@link WatchServiceListener} should be created to listen to events on
         * each different file system.
         *
         * @param system The file system to use.
         * @return this
         */
        public Builder setFileSystem(final FileSystem system) {
            this.fileSystem = system;
            return this;
        }

        /**
         * Create a new listener, using default values for any unset parameters.
         *
         * @return A newly created executor
         * @throws IOException if thrown by {@link WatchServiceListener}'s constructor
         */
        public WatchServiceListener build() throws IOException {
            if (this.threadFactory == null) {
                this.threadFactory = DEFAULT_THREAD_FACTORY;
            }

            if (this.fileSystem == null) {
                this.fileSystem = FileSystems.getDefault();
            }

            if (this.taskExecutor == null) {
                this.taskExecutor = ForkJoinPool.commonPool();
            }

            return new WatchServiceListener(this.threadFactory, this.fileSystem, this.taskExecutor);
        }

    }

}

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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Arrays;

/**
 * Implements a chain of {@link ConfigurationTransformation}s.
 */
class ChainedConfigurationTransformation<T extends ConfigurationNode> extends ConfigurationTransformation<T> {
    private final ConfigurationTransformation<? super T>[] transformations;

    ChainedConfigurationTransformation(ConfigurationTransformation<? super T>[] transformations) {
        this.transformations = Arrays.copyOf(transformations, transformations.length);
    }

    @Override
    public void apply(@NonNull T node) {
        for (ConfigurationTransformation<? super T> transformation : transformations) {
            transformation.apply(node);
        }
    }
}

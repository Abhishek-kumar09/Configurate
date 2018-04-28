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
package ninja.leaping.configurate.yaml;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.SimpleConfigurationNode;
import ninja.leaping.configurate.loader.AbstractConfigurationLoader;
import ninja.leaping.configurate.loader.CommentHandler;
import ninja.leaping.configurate.loader.CommentHandlers;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;

/**
 * A loader for YAML-formatted configurations, using the SnakeYAML library for parsing and generation.
 */
public class YAMLConfigurationLoader extends AbstractConfigurationLoader<ConfigurationNode> {

    /**
     * Creates a new {@link YAMLConfigurationLoader} builder.
     *
     * @return A new builder
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link YAMLConfigurationLoader}.
     */
    public static class Builder extends AbstractConfigurationLoader.Builder<Builder> {
        private final DumperOptions options = new DumperOptions();

        protected Builder() {
            setIndent(4);
        }

        /**
         * Sets the level of indentation the resultant loader should use.
         *
         * @param indent The indent level
         * @return This builder (for chaining)
         */
        @NonNull
        public Builder setIndent(int indent) {
            options.setIndent(indent);
            return this;
        }

        /**
         * Gets the level of indentation to be used by the resultant loader.
         *
         * @return The indent level
         */
        public int getIndent() {
            return options.getIndent();
        }

        /**
         * Sets the flow style the resultant loader should use.
         *
         * Flow: the compact, json-like representation.<br>
         * Example: <code>
         *     {value: [list, of, elements], another: value}
         * </code>
         *
         * Block: expanded, traditional YAML<br>
         * Example: <code>
         *     value:
         *     - list
         *     - of
         *     - elements
         *     another: value
         * </code>
         *
         * @param style The flow style to use
         * @return This builder (for chaining)
         */
        @NonNull
        public Builder setFlowStyle(@NonNull FlowStyle style) {
            options.setDefaultFlowStyle(style);
            return this;
        }

        /**
         * Gets the flow style to be used by the resultant loader.
         *
         * @return The flow style
         */
        @NonNull
        public FlowStyle getFlowSyle() {
            return options.getDefaultFlowStyle();
        }

        @NonNull
        @Override
        public YAMLConfigurationLoader build() {
            return new YAMLConfigurationLoader(this);
        }
    }

    private final ThreadLocal<Yaml> yaml;

    private YAMLConfigurationLoader(Builder builder) {
        super(builder, new CommentHandler[] {CommentHandlers.HASH});
        final DumperOptions opts = builder.options;
        this.yaml = ThreadLocal.withInitial(() -> new Yaml(opts));
    }

    @Override
    protected void loadInternal(ConfigurationNode node, BufferedReader reader) throws IOException {
        node.setValue(yaml.get().load(reader));
    }

    @Override
    protected void saveInternal(ConfigurationNode node, Writer writer) throws IOException {
        yaml.get().dump(node.getValue(), writer);
    }

    @NonNull
    @Override
    public ConfigurationNode createEmptyNode(@NonNull ConfigurationOptions options) {
        return SimpleConfigurationNode.root(options);
    }
}

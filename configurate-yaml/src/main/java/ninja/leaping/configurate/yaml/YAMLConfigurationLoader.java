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
package ninja.leaping.configurate.yaml;

import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.SimpleConfigurationNode;
import ninja.leaping.configurate.loader.AbstractConfigurationLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;


/**
 * A loader for YAML-formatted configurations, using the snakeyaml library for parsing
 */
public class YAMLConfigurationLoader extends AbstractConfigurationLoader {
    private final ThreadLocal<Yaml> yaml;

    public static class Builder extends AbstractConfigurationLoader.Builder {
        private final DumperOptions options = new DumperOptions();

        protected Builder() {
            setIndent(4);
        }

        public Builder setIndent(int indent) {
            options.setIndent(indent);
            return this;
        }

        /**
         * Sets the flow style for this configuration
         * Flow: the compact, json-like representation.<br>
         * Example: <code>
         *     {value: [list, of, elements], another: value}
         * </code>
         *
         * Block: expanded, traditional YAML<br>
         * Emample: <code>
         *     value:
         *     - list
         *     - of
         *     - elements
         *     another: value
         * </code>
         *
         * @param style the appropritae flow style to use
         * @return this
         */
        public Builder setFlowStyle(DumperOptions.FlowStyle style) {
            options.setDefaultFlowStyle(style);
            return this;
        }

        @Override
        public Builder setFile(File file) {
            super.setFile(file);
            return this;
        }

        @Override
        public Builder setURL(URL url) {
            super.setURL(url);
            return this;
        }

        public Builder setSource(CharSource source) {
            super.setSource(source);
            return this;
        }

        public Builder setSink(CharSink sink) {
            super.setSink(sink);
            return this;
        }

        @Override
        public YAMLConfigurationLoader build() {
            return new YAMLConfigurationLoader(source, sink, options);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public YAMLConfigurationLoader(CharSource source, CharSink sink, final DumperOptions options) {
        super(source, sink, true, "#");
        this.yaml = new ThreadLocal<Yaml>() {
            @Override
            protected Yaml initialValue() {
                return new Yaml(options);
            }
        };
    }

    @Override
    protected void loadInternal(ConfigurationNode node, BufferedReader reader) throws IOException {
        node.setValue(yaml.get().load(reader));
    }

    @Override
    protected void saveInternal(ConfigurationNode node, Writer writer) throws IOException {
        yaml.get().dump(node.getValue(), writer);
    }

    @Override
    public ConfigurationNode createEmptyNode() {
        return SimpleConfigurationNode.root();
    }
}

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
package ninja.leaping.configurate.hocon;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.typesafe.config.*;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode;
import ninja.leaping.configurate.loader.AbstractConfigurationLoader;
import ninja.leaping.configurate.loader.CommentHandler;
import ninja.leaping.configurate.loader.CommentHandlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;


/**
 * A loader for HOCON (Hodor)-formatted configurations, using the typesafe config library for parsing
 */
public class HoconConfigurationLoader extends AbstractConfigurationLoader<CommentedConfigurationNode> {
    public static final Pattern CRLF_MATCH = Pattern.compile("\r\n?");
    private final ConfigRenderOptions render;
    private final ConfigParseOptions parse;

    public static class Builder extends AbstractConfigurationLoader.Builder<Builder> {
        private ConfigRenderOptions render = ConfigRenderOptions.defaults()
                .setOriginComments(false)
                .setJson(false);
        private ConfigParseOptions parse = ConfigParseOptions.defaults();

        protected Builder() {
        }

        public ConfigRenderOptions getRenderOptions() {
            return render;
        }

        public ConfigParseOptions getParseOptions() {
            return parse;
        }

        public Builder setRenderOptions(ConfigRenderOptions options) {
            this.render = options;
            return this;
        }

        public Builder setParseOptions(ConfigParseOptions options) {
            this.parse = options;
            return this;
        }

        @Override
        public HoconConfigurationLoader build() {
            return new HoconConfigurationLoader(source, sink, render, parse, preserveHeader);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private HoconConfigurationLoader(CharSource source, CharSink sink, ConfigRenderOptions render,
                                       ConfigParseOptions parse, boolean preservesHeader) {
        super(source, sink, new CommentHandler[] {CommentHandlers.HASH, CommentHandlers.DOUBLE_SLASH}, preservesHeader);
        this.render = render;
        this.parse = parse;
    }

    @Override
    public void loadInternal(CommentedConfigurationNode node, BufferedReader reader) throws IOException {
        Config hoconConfig = ConfigFactory.parseReader(reader, parse);
        for (Map.Entry<String, ConfigValue> ent : hoconConfig.root().entrySet()) {
            readConfigValue(ent.getValue(), node.getNode(ent.getKey()));
        }
    }

    private void readConfigValue(ConfigValue value, CommentedConfigurationNode node) {
        if (!value.origin().comments().isEmpty()) {
            node.setComment(CRLF_MATCH.matcher(Joiner.on('\n').join(value.origin().comments())).replaceAll("\n"));
        }
        switch (value.valueType()) {
            case OBJECT:
                if (((ConfigObject) value).isEmpty()) {
                    node.setValue(ImmutableMap.of());
                } else {
                    for (Map.Entry<String, ConfigValue> ent : ((ConfigObject) value).entrySet()) {
                        readConfigValue(ent.getValue(), node.getNode(ent.getKey()));
                    }
                }
                break;
            case LIST:
                List<ConfigValue> values = (ConfigList) value;
                for (int i = 0; i < values.size(); ++i) {
                    readConfigValue(values.get(i), node.getNode(i));
                }
                break;
            case NULL:
                return;
            default:
                node.setValue(value.unwrapped());
        }
    }

    @Override
    protected void saveInternal(ConfigurationNode node, Writer writer) throws IOException {
        if (!node.hasMapChildren()) {
            if (node.getValue() == null) {
                writer.write(LINE_SEPARATOR);
                return;
            } else {
                throw new IOException("HOCON cannot write nodes not in map format!");
            }
        }
        final ConfigValue value = ConfigValueFactory.fromAnyRef(node.getValue(), "configurate-hocon");
        traverseForComments(value, node);
        final String renderedValue = value.render(render);
        writer.write(renderedValue);
    }


    @Override
    public CommentedConfigurationNode createEmptyNode(ConfigurationOptions options) {
        options = options.setAcceptedTypes(ImmutableSet.of(Map.class, List.class, Double.class,
                Long.class, Integer.class, Boolean.class, String.class, Number.class));
        return SimpleCommentedConfigurationNode.root(options);
    }

    private void traverseForComments(ConfigValue value, ConfigurationNode node) throws IOException {
        potentialComment(value, node);
        switch (value.valueType()) {
            case OBJECT:
                for (Map.Entry<Object, ? extends ConfigurationNode> ent : node.getChildrenMap().entrySet()) {
                    ConfigValue child = ((ConfigObject) value).get(ent.getKey().toString());
                    if (child != null) { // Accept the fact that nodes may disappear after the initial config value
                    // is generated
                        traverseForComments(child, ent.getValue());
                    }
                }
                break;
            case LIST:
                List<? extends ConfigurationNode> nodes = node.getChildrenList();
                for (int i = 0; i < nodes.size(); ++i) {
                    traverseForComments(((ConfigList) value).get(i), nodes.get(i));
                }
                break;
        }
    }

    // -- Comment handling -- this might have to be updated as the hocon dep changes (But tests should detect this
    // breakage
    private static final Class<? extends ConfigValue> VALUE_CLASS;
    private static final Field VALUE_ORIGIN;
    static {
        try {
            VALUE_CLASS = Class.forName("com.typesafe.config.impl.AbstractConfigValue").asSubclass(ConfigValue.class);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }

        try {
            VALUE_ORIGIN = VALUE_CLASS.getDeclaredField("origin");
            VALUE_ORIGIN.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }

    }
    private ConfigValue potentialComment(ConfigValue value, ConfigurationNode node) throws IOException {
        if (!(node instanceof CommentedConfigurationNode)) {
            return value;
        }
        CommentedConfigurationNode commentedNode = (CommentedConfigurationNode) node;
        Optional<String> comment = commentedNode.getComment();
        if (!comment.isPresent()) {
            return value;
        }
        try {

            VALUE_ORIGIN.set(value, value.origin().withComments(ImmutableList.copyOf(LINE_SPLITTER.split(comment.get()))));
        } catch (IllegalAccessException e) {
            throw new IOException("Unable to set comments for config value" + value);
        }
        return value;
    }
}

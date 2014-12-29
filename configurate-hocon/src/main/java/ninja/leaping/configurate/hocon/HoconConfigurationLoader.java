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
import com.google.common.base.Optional;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.typesafe.config.*;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode;
import ninja.leaping.configurate.loader.FileConfigurationLoader;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;


/**
 * A loader for HOCON (Hodor)-formatted configurations, using the typesafe config library for parsing
 */
public class HoconConfigurationLoader extends FileConfigurationLoader {
    public static final Pattern CRLF_MATCH = Pattern.compile("\r\n?");
    private final ConfigRenderOptions render = ConfigRenderOptions.defaults().setOriginComments(false);
    private final ConfigParseOptions parse = ConfigParseOptions.defaults();

    public HoconConfigurationLoader(File file) {
        super(file);
    }

    public HoconConfigurationLoader(URL url) {
        super(url);
    }

    public HoconConfigurationLoader(CharSource source, CharSink sink) {
        super(source, sink);
    }

    @Override
    public CommentedConfigurationNode load() throws IOException {
        if (!canLoad()) {
            throw new IOException("No source present to read from!");
        }
        final SimpleCommentedConfigurationNode node = SimpleCommentedConfigurationNode.root(); // TODO: autoattach
        try (Reader reader = source.openStream()) {
            Config hoconConfig = ConfigFactory.parseReader(reader, parse);
            for (Map.Entry<String, ConfigValue> ent : hoconConfig.root().entrySet()) {
                readConfigValue(ent.getValue(), node.getChild(ent.getKey()));
            }
        }
        return node;
    }

    private void readConfigValue(ConfigValue value, CommentedConfigurationNode node) {
        if (!value.origin().comments().isEmpty()) {
            node.setComment(CRLF_MATCH.matcher(Joiner.on('\n').join(value.origin().comments())).replaceAll("\n"));
        }
        switch (value.valueType()) {
            case OBJECT:
                for (Map.Entry<String, ConfigValue> ent : ((ConfigObject) value).entrySet()) {
                    readConfigValue(ent.getValue(), node.getChild(ent.getKey()));
                }
                break;
            case LIST:
                List<ConfigValue> values = (ConfigList) value;
                for (int i = 0; i < values.size(); ++i) {
                    readConfigValue(values.get(i), node.getChild(i));
                }
                break;
            case NULL:
                return;
            default:
                node.setValue(value.unwrapped());
        }
    }

    @Override
    public void save(ConfigurationNode node) throws IOException {
        if (!canSave()) {
            throw new IOException("No sink present to write to!");
        }
        if (!node.hasMapChildren()) {
            throw new IOException("HOCON cannot write nodes not in map format!");
        }
        final ConfigValue value = ConfigValueFactory.fromAnyRef(node.getValue());
        traverseForComments(value, node);
        final String renderedValue = value.render(render);
        sink.write(renderedValue);
    }

    public String renderToString(ConfigurationNode node) throws IOException {
        final ConfigValue value = ConfigValueFactory.fromAnyRef(node.getValue());
        traverseForComments(value, node);
        return value.render(render);
    }

    private void traverseForComments(ConfigValue value, ConfigurationNode node) throws IOException {
        potentialComment(value, node);
        switch (value.valueType()) {
            case OBJECT:
                for (Map.Entry<Object, ? extends ConfigurationNode> ent : node.getChildrenMap().entrySet()) {
                    traverseForComments(((ConfigObject) value).get(ent.getKey().toString()), ent.getValue());
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
    private static final Class<? extends ConfigOrigin> ORIGIN_CLASS;
    private static final Field VALUE_ORIGIN;
    private static final Method ORIGIN_SET_COMMENTS;
    static {
        try {
            VALUE_CLASS = Class.forName("com.typesafe.config.impl.AbstractConfigValue").asSubclass(ConfigValue.class);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }

        ORIGIN_CLASS = ConfigValueFactory.fromAnyRef("a").origin().getClass();
        try {
            VALUE_ORIGIN = VALUE_CLASS.getDeclaredField("origin");
            ORIGIN_SET_COMMENTS = ORIGIN_CLASS.getDeclaredMethod("setComments", List.class);
            VALUE_ORIGIN.setAccessible(true);
            ORIGIN_SET_COMMENTS.setAccessible(true);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
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
            Object o = ORIGIN_SET_COMMENTS.invoke(value.origin(), Collections.singletonList(comment.get()));
            VALUE_ORIGIN.set(value, o);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IOException("Unable to set comments for config value" + value);
        }
        return value;
    }
}

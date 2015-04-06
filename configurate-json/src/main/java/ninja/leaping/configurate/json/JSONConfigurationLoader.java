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
package ninja.leaping.configurate.json;

import com.fasterxml.jackson.core.*;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode;
import ninja.leaping.configurate.loader.AbstractConfigurationLoader;
import ninja.leaping.configurate.loader.CommentHandler;
import ninja.leaping.configurate.loader.CommentHandlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.Map;


/**
 * A loader for JSON-formatted configurations, using the jackson library for parsing and generation
 */
public class JSONConfigurationLoader extends AbstractConfigurationLoader<ConfigurationNode> {
    private final JsonFactory factory;
    private final int indent;
    private final FieldValueSeparatorStyle fieldValueSeparatorStyle;

    public static class Builder extends AbstractConfigurationLoader.Builder {
        private final JsonFactory factory = new JsonFactory();
        private int indent = 2;
        private FieldValueSeparatorStyle fieldValueSeparatorStyle = FieldValueSeparatorStyle.SPACE_AFTER;

        protected Builder() {
            factory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            factory.enable(JsonParser.Feature.ALLOW_COMMENTS);
            factory.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS);
            factory.enable(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER);
            factory.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
            factory.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            factory.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
            factory.enable(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
        }

        public JsonFactory getFactory() {
            return this.factory;
        }

        /**
         * Sets pretty printing using default options.
         * As of Configurate 1.1, it is recommended to use the {@link #setIndent(int)} and
         * {@link #setFieldValueSeparatorStyle(FieldValueSeparatorStyle)} methods
         * instead as they provide more control.
         * @param prettyPrint Whether to pretty print
         * @return this
         */
        @Deprecated
        public Builder setPrettyPrint(boolean prettyPrint) {
            if (prettyPrint) {
                this.indent = 2;
                this.fieldValueSeparatorStyle = FieldValueSeparatorStyle.SPACE_AFTER;
            } else {
                this.indent = 0;
                this.fieldValueSeparatorStyle = FieldValueSeparatorStyle.NO_SPACE;
            }
            return this;
        }

        public Builder setIndent(int indent) {
            this.indent = indent;
            return this;
        }

        public Builder setFieldValueSeparatorStyle(FieldValueSeparatorStyle style) {
            this.fieldValueSeparatorStyle = style;
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
        public Builder setPreservesHeader(boolean preservesHeader) {
            super.setPreservesHeader(preservesHeader);
            return this;
        }

        @Override
        public JSONConfigurationLoader build() {
            return new JSONConfigurationLoader(source, sink, factory, indent, fieldValueSeparatorStyle, preserveHeader);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    protected JSONConfigurationLoader(CharSource source, CharSink sink, JsonFactory factory,
                                      int indent, FieldValueSeparatorStyle fieldValueSeparatorStyle,
                                      boolean preservesHeader) {
        super(source, sink, new CommentHandler[] {CommentHandlers.DOUBLE_SLASH, CommentHandlers.SLASH_BLOCK,
                        CommentHandlers.HASH},
        preservesHeader);
        this.factory = factory;
        this.indent = indent;
        this.fieldValueSeparatorStyle = fieldValueSeparatorStyle;
    }

    @Override
    protected void loadInternal(ConfigurationNode node, BufferedReader reader) throws IOException {
        try (JsonParser parser = factory.createParser(reader)) {
            parser.nextToken();
            parseValue(parser, node);
        }
    }

    private void parseValue(JsonParser parser, ConfigurationNode node) throws IOException {
        JsonToken token = parser.getCurrentToken();
        switch (token) {
            case START_OBJECT:
                parseObject(parser, node);
                break;
            case START_ARRAY:
                parseArray(parser, node);
                break;
            case VALUE_NUMBER_FLOAT:
                node.setValue(parser.getFloatValue());
                break;
            case VALUE_NUMBER_INT:
                node.setValue(parser.getIntValue());
                break;
            case VALUE_STRING:
                node.setValue(parser.getText());
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                node.setValue(parser.getBooleanValue());
                break;
            case VALUE_NULL: // Ignored values
            case FIELD_NAME:
                break;
            default:
                throw new IOException("Unsupported token type: " + token + " (at " + parser.getTokenLocation()
                        + ")");
        }
    }

    private void parseArray(JsonParser parser, ConfigurationNode node) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != null) {
            switch (token) {
                case END_ARRAY:
                    return;
                default:
                    parseValue(parser, node.getAppendedNode());
            }
        }
        throw new JsonParseException("Reached end of stream with unclosed array!", parser.getCurrentLocation());

    }

    private void parseObject(JsonParser parser, ConfigurationNode node) throws IOException {
        JsonToken token;
        while ((token = parser.nextToken()) != null) {
            switch (token) {
                case END_OBJECT:
                    return;
                default:
                    parseValue(parser, node.getNode(parser.getCurrentName()));
            }
        }
            throw new JsonParseException("Reached end of stream with unclosed array!", parser.getCurrentLocation());
    }

    @Override
    public void saveInternal(ConfigurationNode node, Writer writer) throws IOException {
        try (JsonGenerator generator = factory.createGenerator(writer)) {
            generator.setPrettyPrinter(new ConfiguratePrettyPrinter(indent, fieldValueSeparatorStyle));
            generateValue(generator, node);
            generator.flush();
            writer.write(LINE_SEPARATOR); // Jackson doesn't add a newline at the end of files by default
        }
    }

    @Override
    public CommentedConfigurationNode createEmptyNode(ConfigurationOptions options) {
        return SimpleCommentedConfigurationNode.root(options);
    }

    private void generateValue(JsonGenerator generator, ConfigurationNode node) throws IOException {
        if (node.hasMapChildren()) {
            generateObject(generator, node);
        } else if (node.hasListChildren()) {
            generateArray(generator, node);
        } else {
            Object value = node.getValue();
            if (value instanceof Double) {
                generator.writeNumber((Double) value);
            } else if (value instanceof Float) {
                generator.writeNumber((Float) value);
            } else if (value instanceof Long) {
                generator.writeNumber((Long) value);
            } else if (value instanceof Integer) {
                generator.writeNumber((Integer) value);
            } else if (value instanceof Boolean) {
                generator.writeBoolean((Boolean) value);
            } else if (value instanceof byte[]) {
                generator.writeBinary((byte[]) value);
            } else {
                generator.writeString(value.toString());
            }
        }
    }

    /*private void generateComment(JsonGenerator generator, ConfigurationNode node, boolean inArray) throws IOException {
        if (node instanceof CommentedConfigurationNode) {
            final Optional<String> comment = ((CommentedConfigurationNode) node).getComment();
            if (comment.isPresent()) {
                if (indent == 0) {
                    generator.writeRaw("/*");
                    generator.writeRaw(comment.get().replaceAll("\\* /", ""));
                    generator.writeRaw("* /");
                } else {
                    for (Iterator<String> it = LINE_SPLITTER.split(comment.get()).iterator(); it.hasNext();) {
                        generator.writeRaw("// ");
                        generator.writeRaw(it.next());
                        generator.getPrettyPrinter().beforeObjectEntries(generator);
                        if (it.hasNext()) {
                            generator.writeRaw(LINE_SEPARATOR);
                        }
                    }
                    if (inArray) {
                        generator.writeRaw(LINE_SEPARATOR);
                    }
                }
            }
        }

    }*/

    private void generateObject(JsonGenerator generator, ConfigurationNode node) throws IOException {
        if (!node.hasMapChildren()) {
            throw new IOException("Node passed to generateObject does not have map children!");
        }
        generator.writeStartObject();
        for (Map.Entry<Object, ? extends ConfigurationNode> ent : node.getChildrenMap().entrySet()) {
            //generateComment(generator, ent.getValue(), false);
            generator.writeFieldName(ent.getKey().toString());
            generateValue(generator, ent.getValue());
        }
        generator.writeEndObject();

    }

    private void generateArray(JsonGenerator generator, ConfigurationNode node) throws IOException {
        if (!node.hasListChildren()) {
            throw new IOException("Node passed to generateArray does not have list children!");
        }
        List<? extends ConfigurationNode> children = node.getChildrenList();
        generator.writeStartArray(children.size());
        for (ConfigurationNode child : children) {
            //generateComment(generator, child, true);
            generateValue(generator, child);
        }
        generator.writeEndArray();
    }
}

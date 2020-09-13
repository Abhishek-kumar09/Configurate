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
package org.spongepowered.configurate.yaml;

import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationNodeFactory;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.scanner.Scanner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class YamlParser extends ParserImpl {

    private final Map<String, ConfigurationNode> aliases = new HashMap<>();

    YamlParser(final Scanner reader) {
        super(reader);
    }

    Event requireEvent(final Event.ID type) throws IOException {
        final Event next = peekEvent();
        if (!next.is(type)) {
            throw new IOException("Expected next event of type" + type + " but was " + next.getEventId());
        }
        return this.getEvent();
    }

    @SuppressWarnings("unchecked")
    <T extends Event> T requireEvent(final Event.ID type, final Class<T> clazz) throws IOException {
        final Event next = peekEvent();
        if (!next.is(type)) {
            throw new IOException("Expected next event of type" + type + " but was " + next.getEventId());
        }
        if (!clazz.isInstance(next)) {
            throw new IOException("Expected event of type " + clazz + " but got a " + next.getClass());
        }

        return (T) this.getEvent();
    }

    public <N extends ConfigurationNode> Stream<N> stream(final ConfigurationNodeFactory<N> factory) throws IOException {
        requireEvent(Event.ID.StreamStart);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<N>() {
            @Override
            public boolean hasNext() {
                return !checkEvent(Event.ID.StreamEnd);
            }

            @Override
            public N next() {
                if (!hasNext()) {
                    throw new IndexOutOfBoundsException();
                }
                try {
                    final N node = factory.createNode();
                    document(node);
                    return node;
                } catch (final IOException e) {
                    throw new RuntimeException(e); // TODO
                }
            }
        }, Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    public void singleDocumentStream(final ConfigurationNode node) throws IOException {
        requireEvent(Event.ID.StreamStart);
        document(node);
        requireEvent(Event.ID.StreamEnd);
    }

    public void document(final ConfigurationNode node) throws IOException {
        requireEvent(Event.ID.DocumentStart);
        try {
            value(node);
        } finally {
            this.aliases.clear();
        }
        requireEvent(Event.ID.DocumentEnd);
    }

    void value(final ConfigurationNode node) throws IOException {
        final Event peeked = peekEvent();
        // extract event metadata
        if (peeked instanceof NodeEvent && !(peeked instanceof AliasEvent)) {
            final String anchor = ((NodeEvent) peeked).getAnchor();
            if (anchor != null) {
                node.setHint(YamlConfigurationLoader.ANCHOR_ID, anchor);
                this.aliases.put(anchor, node);
            }
            if (peeked instanceof CollectionStartEvent) {
                node.setHint(YamlConfigurationLoader.NODE_STYLE, NodeStyle.fromSnakeYaml(((CollectionStartEvent) peeked).getFlowStyle()));
            }
        }

        // then handle the value
        switch (peeked.getEventId()) {
            case Scalar:
                scalar(node);
                break;
            case MappingStart:
                mapping(node);
                break;
            case SequenceStart:
                sequence(node);
                break;
            case Alias:
                alias(node);
                break;
            default:
                throw new IOException("Unexpected event type " + peekEvent().getEventId());
        }
    }

    void scalar(final ConfigurationNode node) throws IOException {
        final ScalarEvent scalar = requireEvent(Event.ID.Scalar, ScalarEvent.class);
        node.setHint(YamlConfigurationLoader.SCALAR_STYLE, ScalarStyle.fromSnakeYaml(scalar.getScalarStyle()));
        node.setValue(scalar.getValue()); // TODO:  tags and value types
    }

    void mapping(final ConfigurationNode node) throws IOException {
        requireEvent(Event.ID.MappingStart);

        node.setValue(Collections.emptyMap());
        final ConfigurationNode keyHolder = BasicConfigurationNode.root(node.getOptions());
        while (!checkEvent(Event.ID.MappingEnd)) {
            value(keyHolder);
            final ConfigurationNode child = node.getNode(keyHolder.getValue());
            if (!child.isVirtual()) { // duplicate keys are forbidden (3.2.1.3)
                throw new IOException("Duplicate key '" + keyHolder.getValue() + "' encountered!");
            }
            value(node.getNode(keyHolder.getValue()));
        }

        requireEvent(Event.ID.MappingEnd);
    }

    void sequence(final ConfigurationNode node) throws IOException {
        requireEvent(Event.ID.SequenceStart);
        node.setValue(Collections.emptyList());

        while (!checkEvent(Event.ID.SequenceEnd)) {
            value(node.appendListNode());
        }

        requireEvent(Event.ID.SequenceEnd);
    }

    void alias(final ConfigurationNode node) throws IOException {
        final AliasEvent event = requireEvent(Event.ID.Alias, AliasEvent.class);
        final ConfigurationNode target = this.aliases.get(event.getAnchor());
        if (target == null) {
            throw new IOException("Unknown anchor '" + event.getAnchor() + "'");
        }
        node.setValue(target); // TODO: Reference node types
        node.setHint(YamlConfigurationLoader.ANCHOR_ID, null); // don't duplicate alias
    }

}

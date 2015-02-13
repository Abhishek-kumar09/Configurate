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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class SimpleConfigurationNodeTest {
    @Test
    public void testUnattachedNodesTemporary() {
        ConfigurationNode config = SimpleConfigurationNode.root();
        ConfigurationNode node = config.getNode("some", "node");
        assertTrue(node.isVirtual());
        assertEquals(null, node.getValue());
        assertFalse(node.hasListChildren());
        assertFalse(node.hasMapChildren());
        ConfigurationNode node2 = config.getNode("some", "node");
        assertNotSame(node, node2);


        ConfigurationNode node3 = config.getNode("some").getNode("node");
        assertNotSame(node, node3);
    }

    @Test
    public void testNodeCreation() {
        ConfigurationNode config = SimpleConfigurationNode.root();
        ConfigurationNode uncreatedNode = config.getNode("uncreated", "node");
        assertTrue(uncreatedNode.isVirtual()); // Just in case
        uncreatedNode.setValue("test string for cool people");
        assertFalse(uncreatedNode.isVirtual());
        assertEquals("test string for cool people", uncreatedNode.getValue());

        ConfigurationNode fetchedAfterCreation = config.getNode("uncreated", "node");
        assertEquals(uncreatedNode, fetchedAfterCreation);
        assertEquals(uncreatedNode, config.getNode("uncreated").getNode("node"));
    }

    @Test
    public void testTraversingNodeCreation() {
        SimpleConfigurationNode config = SimpleConfigurationNode.root();
        SimpleConfigurationNode nodeOne = config.getNode("uncreated", "step", "node");
        SimpleConfigurationNode nodeTwo = config.getNode("uncreated", "step", "color");
        nodeOne.setValue("one");
        nodeTwo.setValue("lilac");
        ConfigurationNode attachedParent = config.getNode("uncreated", "step");
        assertEquals(attachedParent, nodeOne.getParentInternal());
        assertEquals(attachedParent, nodeTwo.getParentInternal());
    }

    @Test
    public void testGetDefaultValue() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        final Object testObj = new Object();
        assertEquals(testObj, root.getNode("nonexistent").getValue(testObj));
    }

    @Test
    public void testGetChildrenMap() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        ConfigurationNode a = root.getNode("a").setValue("one");
        ConfigurationNode b = root.getNode("b").setValue("two");
        assertEquals(ImmutableMap.of("a", a, "b", b), root.getChildrenMap());
    }

    @Test
    public void testGetChildrenList() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        ConfigurationNode a = root.getAppendedNode().setValue("one");
        ConfigurationNode b = root.getAppendedNode().setValue("two");
        assertEquals(ImmutableList.of(a, b), root.getChildrenList());
    }

    private static final Map<Object, Object> TEST_MAP = new HashMap<Object, Object>();
    private static final List<Object> TEST_LIST = new ArrayList<Object>();
    static {
        TEST_LIST.add("test1");
        TEST_LIST.add("test2");

        TEST_MAP.put("key", "value");
        TEST_MAP.put("fabulous", true);
    }
    @Test
    public void testMapUnpacking() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        root.setValue(TEST_MAP);
        assertEquals("value", root.getNode("key").getValue());
        assertEquals(true, root.getNode("fabulous").getValue());
    }

    @Test
    public void testMapPacking() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        root.getNode("key").setValue("value");
        root.getNode("fabulous").setValue(true);

        assertEquals(TEST_MAP, root.getValue());
    }

    @Test
    public void testListUnpacking() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        root.setValue(TEST_LIST);
        assertEquals("test1", root.getNode(0).getValue());
        assertEquals("test2", root.getNode(1).getValue());
    }

    @Test
    public void testListPacking() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        root.getAppendedNode().setValue("test1");
        root.getAppendedNode().setValue("test2");
        assertEquals(TEST_LIST, root.getValue());
    }

    @Test
    public void testSingleListConversion() {
        ConfigurationNode config = SimpleConfigurationNode.root();
        ConfigurationNode node = config.getNode("test", "value");
        node.setValue("test");
        ConfigurationNode secondChild = node.getAppendedNode();
        secondChild.setValue("test2");
        assertEquals(Arrays.asList("test", "test2"), node.getValue());
    }

    @Test
    public void testSettingNullRemoves() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        ConfigurationNode child = root.getNode("child").setValue("a");
        assertFalse(child.isVirtual());
        assertEquals(child, root.getNode("child"));
        child.setValue(null);
        assertTrue(child.isVirtual());
        assertNotEquals(child, root.getNode("child"));
    }

    @Test
    public void testGetPath() {
        ConfigurationNode root = SimpleConfigurationNode.root();
        assertArrayEquals(new Object[] {"a", "b", "c"}, root.getNode("a", "b", "c").getPath());
    }

}

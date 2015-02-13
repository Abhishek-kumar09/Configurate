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
package ninja.leaping.configurate.commented;

import com.google.common.base.Optional;
import ninja.leaping.configurate.ConfigurationNode;

import java.util.List;
import java.util.Map;

/**
 * A configuration node that is capable of having an attached comment
 */
public interface CommentedConfigurationNode extends ConfigurationNode {
    /**
     * Gets the current value for the comment. If the comment contains multiple lines, the lines will be split by \n
     *
     * @return the configuration's current comment
     */
    public Optional<String> getComment();

    /**
     * Sets the comment for this configuration.
     * @param comment The comment to set. Line breaks should be represented as LFs (\n)
     */
    public CommentedConfigurationNode setComment(String comment);

    // Methods from superclass overridden to have correct return types

    @Override
    public List<? extends CommentedConfigurationNode> getChildrenList();
    @Override
    public Map<Object, ? extends CommentedConfigurationNode> getChildrenMap();
    @Override
    public CommentedConfigurationNode setValue(Object value);
    @Override
    public CommentedConfigurationNode getAppendedNode();
    @Override
    public CommentedConfigurationNode getNode(Object... path);
}

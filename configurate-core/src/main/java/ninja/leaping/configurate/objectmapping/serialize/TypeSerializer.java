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
package ninja.leaping.configurate.objectmapping.serialize;

import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

public interface TypeSerializer {
    /**
     * Check if this serializer can handle values of the given type.
     *
     * @param type The type to check
     * @return Whether this serializer is applicable to the given type
     */
    boolean isApplicable(TypeToken<?> type);

    /**
     * Deserialize an object required to be of a given type from the given configuration node
     * @param type The type of return value required
     * @param value The node containing serialized data
     * @return An object
     * @throws ObjectMappingException If the presented data is somehow invalid
     */
    public Object deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException;

    public void serialize(TypeToken<?> type, Object obj, ConfigurationNode value) throws ObjectMappingException;
}

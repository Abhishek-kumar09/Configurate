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
package org.spongepowered.configurate.objectmapping.serialize;

import com.google.common.reflect.TypeToken;
import org.spongepowered.configurate.objectmapping.ObjectMappingException;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class PatternSerializer extends ScalarSerializer<Pattern> {
    PatternSerializer() {
        super(TypeToken.of(Pattern.class));
    }

    @Override
    public Pattern deserialize(TypeToken<?> type, Object obj) throws ObjectMappingException {
        try {
            return Pattern.compile(obj.toString());
        } catch (PatternSyntaxException ex) {
            throw new ObjectMappingException(ex);
        }
    }

    @Override
    public Object serialize(Pattern item, Predicate<Class<?>> typeSupported) {
        return item.pattern();
    }
}

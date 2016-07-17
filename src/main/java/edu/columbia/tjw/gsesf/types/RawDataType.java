/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.gsesf.types;

import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.EnumMember;
import java.time.LocalDate;

/**
 *
 * @author tyler
 */
public enum RawDataType implements EnumMember<RawDataType>
{
    DOUBLE(Double.class),
    INT(Integer.class),
    STRING(String.class),
    BOOLEAN(Boolean.class),
    DATE(LocalDate.class);

    public static final EnumFamily<RawDataType> FAMILY = new EnumFamily<>(values());

    private final Class<?> _underlyingClass;

    private RawDataType(final Class<?> underlyingClass_)
    {
        _underlyingClass = underlyingClass_;
    }

    public Class<?> getUnderlyingClass()
    {
        return _underlyingClass;
    }

    @Override
    public EnumFamily<RawDataType> getFamily()
    {
        return FAMILY;
    }

}

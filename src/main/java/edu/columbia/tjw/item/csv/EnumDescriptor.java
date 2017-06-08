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
package edu.columbia.tjw.item.csv;

import edu.columbia.tjw.item.base.SimpleStringEnum;
import edu.columbia.tjw.item.util.EnumFamily;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author tyler
 */
public final class EnumDescriptor implements Serializable
{
    private static final long serialVersionUID = 0x41b84b7565c38636L;

    private final String _columnName;
    private final SortedSet<String> _values;
    private final String _defaultValue;

    private final EnumFamily<SimpleStringEnum> _enumFamily;

    public EnumDescriptor(final String columnName_, final Set<String> values_, final String defaultValue_)
    {
        if (null == columnName_)
        {
            throw new NullPointerException("Column name cannot be null");
        }

        _columnName = columnName_;
        _values = Collections.unmodifiableSortedSet(new TreeSet<>(values_));
        _defaultValue = defaultValue_;

        if (!_values.contains(_defaultValue))
        {
            throw new IllegalArgumentException("Default is not a possible value: " + _defaultValue);
        }

        _enumFamily = SimpleStringEnum.generateFamily(_values);
    }

    public String getColumnName()
    {
        return _columnName;
    }

    public SortedSet<String> getValues()
    {
        return _values;
    }

    public String getDefaultValue()
    {
        return _defaultValue;
    }

    public EnumFamily<SimpleStringEnum> getFamily()
    {
        return _enumFamily;
    }

}

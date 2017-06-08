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
import java.util.TreeSet;

/**
 *
 * @author tyler
 */
public final class ColumnDescriptorSet implements Serializable
{
    private static final long serialVersionUID = 0xa79b1b7514209a52L;

    private final String _startStatusColumn;
    private final String _endStatusColumn;
    private final Set<String> _numericColumns;
    private final Set<String> _booleanColumns;
    private final Set<String> _enumColumns;

    private final EnumFamily<SimpleStringEnum> _allColumns;

    public ColumnDescriptorSet(final String endStatusColumn_, final Set<String> numericColumns_, final Set<String> booleanColumns_, final Set<String> enumColumns_)
    {
        this(null, endStatusColumn_, numericColumns_, booleanColumns_, enumColumns_);
    }

    public ColumnDescriptorSet(final String startStatusColumn_, final String endStatusColumn_, final Set<String> numericColumns_, final Set<String> booleanColumns_, final Set<String> enumColumns_)
    {
        if (null == endStatusColumn_)
        {
            throw new NullPointerException("End Status column cannot be null.");
        }

        _startStatusColumn = startStatusColumn_;
        _endStatusColumn = endStatusColumn_;

        _numericColumns = Collections.unmodifiableSortedSet(new TreeSet<>(numericColumns_));
        _booleanColumns = Collections.unmodifiableSortedSet(new TreeSet<>(booleanColumns_));
        _enumColumns = Collections.unmodifiableSortedSet(new TreeSet<>(enumColumns_));

        final Set<String> allColumns = new TreeSet<>();

        allColumns.add(_endStatusColumn);
        int count = 1;

        allColumns.addAll(_numericColumns);
        count += _numericColumns.size();

        allColumns.addAll(_booleanColumns);
        count += _booleanColumns.size();

        allColumns.addAll(_enumColumns);
        count += _enumColumns.size();

        if (null != _startStatusColumn)
        {
            allColumns.add(_startStatusColumn);
            count++;
        }

        if (count != allColumns.size())
        {
            throw new IllegalArgumentException("All columns must be unique");
        }

        _allColumns = SimpleStringEnum.generateFamily(allColumns);
    }

    public String getStartStatusColumn()
    {
        return _startStatusColumn;
    }

    public String getEndStatusColumn()
    {
        return _endStatusColumn;
    }

    public Set<String> getNumericColumns()
    {
        return _numericColumns;
    }

    public Set<String> getBooleanColumns()
    {
        return _booleanColumns;
    }

    public Set<String> getEnumColumns()
    {
        return _enumColumns;
    }

    public EnumFamily<SimpleStringEnum> getAllColumns()
    {
        return _allColumns;
    }

}

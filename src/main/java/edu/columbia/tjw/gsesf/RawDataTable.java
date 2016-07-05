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
package edu.columbia.tjw.gsesf;

import edu.columbia.tjw.gsesf.types.RawDataType;
import edu.columbia.tjw.gsesf.types.TypedField;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.InstancePool;
import java.time.LocalDate;

/**
 * A container to hold raw loan data.
 *
 * This container is pretty efficient about the packing of data. It also
 * deduplicates objects.
 *
 *
 *
 * @author tyler
 * @param <T>
 */
public final class RawDataTable<T extends TypedField<T>>
{
    private final double[][] _doubleFields;
    private final int[][] _intFields;
    private final String[][] _stringFields;
    private final boolean[][] _booleanFields;
    private final LocalDate[][] _dateFields;
    private final EnumFamily<T> _family;
    private final InstancePool<String> _stringDeduplicator;
    private final InstancePool<LocalDate> _dateDeduplicator;

    private final int _maxSize;
    private int _currentSize;
    private int _appendRow;
    private boolean _isReadonly;

    public RawDataTable(final EnumFamily<T> family_, final int maxSize_)
    {
        _family = family_;
        final int familySize = family_.size();

        _maxSize = maxSize_;
        _currentSize = 0;
        _appendRow = -1;

        _doubleFields = new double[familySize][];
        _intFields = new int[familySize][];
        _stringFields = new String[familySize][];
        _booleanFields = new boolean[familySize][];
        _dateFields = new LocalDate[familySize][];

        _stringDeduplicator = new InstancePool();
        _dateDeduplicator = new InstancePool();

        for (final T next : family_.getMembers())
        {
            final RawDataType type = next.getType();
            final int ordinal = type.ordinal();

            switch (type)
            {
                case DOUBLE:
                    _doubleFields[ordinal] = new double[maxSize_];
                    break;
                case INT:
                    _intFields[ordinal] = new int[maxSize_];
                    break;
                case STRING:
                    _stringFields[ordinal] = new String[maxSize_];
                    break;
                case BOOLEAN:
                    _booleanFields[ordinal] = new boolean[maxSize_];
                    break;
                case DATE:
                    _dateFields[ordinal] = new LocalDate[maxSize_];
                    break;
                default:
                    throw new IllegalArgumentException("Unknown data type: " + type);
            }
        }
    }

    public int getMaxSize()
    {
        return _maxSize;
    }

    public int size()
    {
        return _currentSize;
    }

    public EnumFamily<T> getFamily()
    {
        return _family;
    }

    public boolean isReadonly()
    {
        return _isReadonly;
    }

    public boolean setReadOnly()
    {
        if (_isReadonly)
        {
            return false;
        }

        _isReadonly = true;
        _appendRow = -1;
        _stringDeduplicator.clear();
        _dateDeduplicator.clear();
        return true;
    }

    public boolean appendRow()
    {
        if (_currentSize == _maxSize)
        {
            return false;
        }
        if (_isReadonly)
        {
            return false;
        }

        _appendRow = _currentSize;
        _currentSize++;
        return true;
    }

    public double getDouble(final int row_, final T field_)
    {
        checkRow(row_);

        final int ordinal = field_.ordinal();
        return _doubleFields[ordinal][row_];
    }

    public int getInt(final int row_, final T field_)
    {
        checkRow(row_);

        final int ordinal = field_.ordinal();
        return _intFields[ordinal][row_];
    }

    public boolean getBoolean(final int row_, final T field_)
    {
        checkRow(row_);

        final int ordinal = field_.ordinal();
        return _booleanFields[ordinal][row_];
    }

    public String getString(final int row_, final T field_)
    {
        checkRow(row_);

        final int ordinal = field_.ordinal();
        return _stringFields[ordinal][row_];
    }

    public LocalDate getDate(final int row_, final T field_)
    {
        checkRow(row_);

        final int ordinal = field_.ordinal();
        return _dateFields[ordinal][row_];
    }

    private void checkRow(final int row_)
    {
        if (row_ >= _currentSize)
        {
            throw new IllegalArgumentException("Row out of range: " + row_);
        }
    }

    public void setDouble(final T field_, final double input_)
    {
        if (_appendRow == -1)
        {
            throw new IllegalStateException("Not in append mode.");
        }

        final int ordinal = field_.ordinal();
        _doubleFields[ordinal][_appendRow] = input_;
    }

    public void setInt(final T field_, final int input_)
    {
        if (_appendRow == -1)
        {
            throw new IllegalStateException("Not in append mode.");
        }

        final int ordinal = field_.ordinal();
        _intFields[ordinal][_appendRow] = input_;
    }

    public void setBoolean(final T field_, final boolean input_)
    {
        if (_appendRow == -1)
        {
            throw new IllegalStateException("Not in append mode.");
        }

        final int ordinal = field_.ordinal();
        _booleanFields[ordinal][_appendRow] = input_;
    }

    public void setString(final T field_, final String input_)
    {
        if (_appendRow == -1)
        {
            throw new IllegalStateException("Not in append mode.");
        }

        final String deduplicated = _stringDeduplicator.makeCanonical(input_);
        final int ordinal = field_.ordinal();
        _stringFields[ordinal][_appendRow] = deduplicated;
    }

    public void setDate(final T field_, final LocalDate input_)
    {
        if (_appendRow == -1)
        {
            throw new IllegalStateException("Not in append mode.");
        }

        final LocalDate deduplicated = _dateDeduplicator.makeCanonical(input_);
        final int ordinal = field_.ordinal();
        _dateFields[ordinal][_appendRow] = deduplicated;
    }

}

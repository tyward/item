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
package edu.columbia.tjw.gsesf.record;

import edu.columbia.tjw.gsesf.types.RawDataType;
import edu.columbia.tjw.gsesf.types.TypedField;
import edu.columbia.tjw.item.util.EnumFamily;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;

/**
 *
 * @author tyler
 * @param <T>
 */
public final class RecordHeader<T extends TypedField<T>> implements Serializable
{
    //private static final Logger LOG = LogUtil.getLogger(RecordHeader.class);
    private static final long serialVersionUID = 5319914791373859285L;

    private final EnumFamily<T> _family;
    private final int[] _allFieldsIndex;
    private final int[] _subFieldsIndex;
    private final T[] _allFields;
    private final T[] _stringFields;
    private final T[] _booleanFields;
    private final T[] _intFields;
    private final T[] _doubleFields;
    private final T[] _dateFields;

    public RecordHeader(final SortedSet<T> fields_, EnumFamily<T> family_)
    {
        _family = family_;
        final int familySize = _family.size();

        _allFieldsIndex = new int[familySize];
        _subFieldsIndex = new int[familySize];

        Arrays.fill(_allFieldsIndex, -1);
        Arrays.fill(_subFieldsIndex, -1);

        _stringFields = extractByType(fields_, family_, RawDataType.STRING);
        _booleanFields = extractByType(fields_, family_, RawDataType.BOOLEAN);
        _intFields = extractByType(fields_, family_, RawDataType.INT);
        _doubleFields = extractByType(fields_, family_, RawDataType.DOUBLE);
        _dateFields = extractByType(fields_, family_, RawDataType.DATE);

        final int typedSize = _stringFields.length + _booleanFields.length + _intFields.length + _doubleFields.length + _dateFields.length;

        if (typedSize != fields_.size())
        {
            throw new IllegalArgumentException("Unknown field type!");
        }

        _allFields = fields_.toArray(family_.generateTypedArray(fields_.size()));

        for (int i = 0; i < _allFields.length; i++)
        {
            final T next = _allFields[i];
            final int ordinal = next.ordinal();
            _allFieldsIndex[ordinal] = i;
        }
    }

    public EnumFamily<T> getFamily()
    {
        return _family;
    }

    /**
     * Computes the index within the all fields array (i.e. index such that
     * getEntry(index_) == field)
     *
     * returns -1 if there is no such index. Throws an exception if field_ is
     * null.
     *
     * @param field_ The field to look up.
     * @return The index such that getEntry(index_) == field_, or -1 if no such
     * index exists.
     */
    public int computeAllFieldsIndex(final T field_)
    {
        final int ordinal = field_.ordinal();
        final int index = _allFieldsIndex[ordinal];
        return index;
    }

    /**
     * Computes the index within the subfields array for the given fields.
     *
     * i.e. the index_ such that getEntry(field_.getType(), index_) ==
     * computeSubFieldsIndex(field_)
     *
     * Returns -1 if no such index, throws NullPointerException if field_ is
     * null.
     *
     *
     * @param field_ The field to look up.
     * @return index such that getEntry(field_.getType(), index_) ==
     * computeSubFieldsIndex(field_), or -1 if no such index.
     */
    public int computeSubFieldsIndex(final T field_)
    {
        final int ordinal = field_.ordinal();
        final int index = _subFieldsIndex[ordinal];
        return index;
    }

    public int size()
    {
        return _allFields.length;
    }

    public T getEntry(final int index_)
    {
        return _allFields[index_];
    }

    public int size(final RawDataType type_)
    {
        final T[] array = getArray(type_);
        return array.length;
    }

    public T getEntry(final RawDataType type_, final int index_)
    {
        final T[] array = getArray(type_);
        final T output = array[index_];
        return output;
    }

    private T[] getArray(final RawDataType type_)
    {
        switch (type_)
        {
            case STRING:
                return _stringFields;
            case BOOLEAN:
                return _booleanFields;
            case DOUBLE:
                return _doubleFields;
            case INT:
                return _intFields;
            case DATE:
                return _dateFields;
            default:
                throw new IllegalArgumentException("Unknown field type!");
        }
    }

    private T[] extractByType(final SortedSet<T> fields_, final EnumFamily<T> family_, final RawDataType type_)
    {
        final List<T> typeList = new ArrayList<>(fields_.size());

        for (final T next : fields_)
        {
            if (next.getType() == type_)
            {
                final int index = typeList.size();
                final int ordinal = next.ordinal();

                //Allow us to quickly look up the index within the sub fields arrays.
                _subFieldsIndex[ordinal] = index;

                typeList.add(next);
            }
        }

        final T[] output = typeList.toArray(family_.generateTypedArray(typeList.size()));
        return output;
    }

    @SuppressWarnings("unchecked")
    public <W extends TypedField<W>> RecordHeader<W> castAsType(final Class<? extends W> clazz_)
    {
        if (clazz_.equals(_family.getComponentType()))
        {
            return (RecordHeader<W>) this;
        }

        throw new ClassCastException("Invalid cast.");
    }
}

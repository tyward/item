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
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * A compact record for recording arbitrary typed fields.
 *
 * This record has a strong distinction between null and not null, even for
 * primitive types. It also packs efficiently for serialization purposes, but
 * still allows fast lookups. The relevant indexing logic is in the record
 * header, so it won't need to be duplicated excessively.
 *
 * @author tyler
 * @param <T>
 */
public final class DataRecord<T extends TypedField<T>> implements Serializable
{
    //private static final Logger LOG = LogUtil.getLogger(DataRecord.class);
    private static final long serialVersionUID = 4265058948289559289L;

    public static final int INT_NULL = Integer.MIN_VALUE;
    public static final double DOUBLE_NULL = Double.NaN;
    public static final boolean BOOLEAN_NULL = false;

    private final RecordHeader<T> _header;
    private final boolean[] _isNull;
    private final String[] _strings;
    private final boolean[] _booleans;
    private final int[] _ints;
    private final double[] _doubles;
    private final LocalDate[] _dates;

    private DataRecord(final RecordHeader<T> header_, final boolean[] isNull_, final String[] strings_,
            final int[] ints_, final boolean[] booleans_, final double[] doubles_, final LocalDate[] dates_)
    {
        _header = header_;
        _isNull = isNull_.clone();
        _strings = strings_.clone();
        _booleans = booleans_.clone();
        _ints = ints_.clone();
        _doubles = doubles_.clone();
        _dates = dates_.clone();
    }

    public boolean isNull(final T field_)
    {
        final int nullIndex = _header.computeAllFieldsIndex(field_);
        final boolean output = _isNull[nullIndex];
        return output;
    }

    public String getString(final T field_)
    {
        checkType(field_, RawDataType.STRING);
        final int index = _header.computeSubFieldsIndex(field_);
        final String output = _strings[index];
        return output;
    }

    public LocalDate getDate(final T field_)
    {
        checkType(field_, RawDataType.DATE);
        final int index = _header.computeSubFieldsIndex(field_);
        final LocalDate output = _dates[index];
        return output;
    }

    public int getInt(final T field_)
    {
        checkType(field_, RawDataType.INT);
        final int index = _header.computeSubFieldsIndex(field_);
        final int output = _ints[index];
        return output;
    }

    public double getDouble(final T field_)
    {
        checkType(field_, RawDataType.DOUBLE);
        final int index = _header.computeSubFieldsIndex(field_);
        final double output = _doubles[index];
        return output;
    }

    public boolean getBoolean(final T field_)
    {
        checkType(field_, RawDataType.BOOLEAN);
        final int index = _header.computeSubFieldsIndex(field_);
        final boolean output = _booleans[index];
        return output;
    }

    public int size()
    {
        return _header.size();
    }

    public RecordHeader<T> getHeader()
    {
        return _header;
    }

    @SuppressWarnings("unchecked")
    public <W extends TypedField<W>> DataRecord<W> castAsType(final Class<? extends W> clazz_)
    {
        if (clazz_.equals(_header.getFamily().getComponentType()))
        {
            return (DataRecord<W>) this;
        }

        throw new ClassCastException("Invalid cast.");
    }

    public static final class RecordBuilder<T extends TypedField<T>>
    {
        private final RecordHeader<T> _header;

        private final boolean[] _isSet;

        private final boolean[] _isNull;
        private final String[] _strings;
        private final boolean[] _booleans;
        private final int[] _ints;
        private final double[] _doubles;
        private final LocalDate[] _dates;

//        private int _stringCounter;
//        private int _booleanCounter;
//        private int _intCounter;
//        private int _doubleCounter;
//        private int _dateCounter;
        private int _totalCounter;

        public RecordBuilder(final RecordHeader<T> header_)
        {
            _header = header_;

            final int familySize = _header.getFamily().size();
            final int dataSize = _header.size();

            _isSet = new boolean[familySize];
            _isNull = new boolean[dataSize];
            _strings = new String[_header.size(RawDataType.STRING)];
            _booleans = new boolean[_header.size(RawDataType.BOOLEAN)];
            _ints = new int[_header.size(RawDataType.INT)];
            _doubles = new double[_header.size(RawDataType.DOUBLE)];
            _dates = new LocalDate[_header.size(RawDataType.DATE)];
        }

        public void clear()
        {
            Arrays.fill(_isSet, false);
//            _stringCounter = 0;
//            _booleanCounter = 0;
//            _intCounter = 0;
//            _doubleCounter = 0;
//            _dateCounter = 0;
            _totalCounter = 0;
        }

        public boolean isSet(final T field_)
        {
            final int ordinal = field_.ordinal();
            final boolean output = _isSet[ordinal];
            return output;
        }

        public void setNull(final T field_)
        {
            markSet(field_);

            final int index = _header.computeAllFieldsIndex(field_);
            _isNull[index] = true;

            final RawDataType type = field_.getType();
            final int subIndex = _header.computeSubFieldsIndex(field_);

            switch (type)
            {
                case STRING:
                    _strings[subIndex] = null;
                    break;
                case BOOLEAN:
                    _booleans[subIndex] = BOOLEAN_NULL;
                    break;
                case INT:
                    _ints[subIndex] = INT_NULL;
                    break;
                case DOUBLE:
                    _doubles[subIndex] = DOUBLE_NULL;
                    break;
                case DATE:
                    _dates[subIndex] = null;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type.");
            }
        }

        public void setString(final T field_, final String data_)
        {
            checkType(field_, RawDataType.STRING);

            if (null == data_)
            {
                setNull(field_);
            }
            else
            {
                markSet(field_);
                final int index = _header.computeSubFieldsIndex(field_);
                _strings[index] = data_;
            }
        }

        public void setDate(final T field_, final LocalDate data_)
        {
            checkType(field_, RawDataType.DATE);

            if (null == data_)
            {
                setNull(field_);
            }
            else
            {
                markSet(field_);
                final int index = _header.computeSubFieldsIndex(field_);
                _dates[index] = data_;
            }
        }

        public void setBoolean(final T field_, final boolean data_)
        {
            checkType(field_, RawDataType.BOOLEAN);
            markSet(field_);
            final int index = _header.computeSubFieldsIndex(field_);
            _booleans[index] = data_;
        }

        public void setInt(final T field_, final int data_)
        {
            checkType(field_, RawDataType.INT);
            markSet(field_);
            final int index = _header.computeSubFieldsIndex(field_);
            _ints[index] = data_;
        }

        public void setDouble(final T field_, final double data_)
        {
            checkType(field_, RawDataType.DOUBLE);
            markSet(field_);
            final int index = _header.computeSubFieldsIndex(field_);
            _doubles[index] = data_;
        }

        public DataRecord<T> generateRecord()
        {
            if (_totalCounter != _header.size())
            {
                throw new IllegalArgumentException("Not enough fields filled in.");
            }

            final DataRecord<T> output = new DataRecord<>(_header, _isNull, _strings, _ints, _booleans, _doubles, _dates);
            this.clear();
            return output;
        }

        private void markSet(final T field_)
        {
            checkNotSet(field_);
            final int ordinal = field_.ordinal();
            _isSet[ordinal] = true;

//            final RawDataType type = field_.getType();
            _totalCounter++;
//
//            switch (type)
//            {
//                case STRING:
//                    _stringCounter++;
//                    break;
//                case BOOLEAN:
//                    _booleanCounter++;
//                    break;
//                case INT:
//                    _intCounter++;
//                    break;
//                case DOUBLE:
//                    _doubleCounter++;
//                    break;
//                case DATE:
//                    _dateCounter++;
//                    break;
//                default:
//                    throw new IllegalArgumentException("Unknown type.");
//            }

        }

        private void checkNotSet(final T field_)
        {
            if (isSet(field_))
            {
                throw new IllegalArgumentException("Field already set: " + field_);
            }
        }
    }

    private static <T extends TypedField<T>> void checkType(final T field_, final RawDataType type_)
    {
        final RawDataType fieldType = field_.getType();

        if (fieldType != type_)
        {
            throw new IllegalArgumentException("Types don't match: " + fieldType + " != " + type_);
        }
    }

}

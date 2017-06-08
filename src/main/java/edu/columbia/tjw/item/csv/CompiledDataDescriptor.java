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

import edu.columbia.tjw.item.base.SimpleRegressor;
import edu.columbia.tjw.item.base.SimpleStatus;
import edu.columbia.tjw.item.base.SimpleStringEnum;
import edu.columbia.tjw.item.util.EnumFamily;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author tyler
 */
public final class CompiledDataDescriptor implements Serializable
{
    private static final long serialVersionUID = 0x9672368604f072b7L;
    private static final RawConverter DOUBLE_CONVERTER = new DoubleConverter();
    private static final RawConverter BOOLEAN_CONVERTER = new BooleanConverter();
    private static final RawConverter ISNAN_CONVERTER = new IsNaNConverter();

    private final ColumnDescriptorSet _colDescriptor;
    private final Map<String, EnumDescriptor> _enumData;
    private final Map<String, NumericDescriptor> _numericData;
    private final Set<String> _endStatusLabels;

    private final EnumFamily<SimpleRegressor> _regFamily;
    private final EnumFamily<SimpleStatus> _statusFamily;
    private final RawConverter[] _converters;
    private final int[] _offsets;

    public CompiledDataDescriptor(final ColumnDescriptorSet colDescriptor_, final Set<String> endStatusLabels_, final Collection<EnumDescriptor> enumData_, final Collection<NumericDescriptor> numericData_)
    {
        _colDescriptor = colDescriptor_;

        final SortedMap<String, EnumDescriptor> descriptorMap = new TreeMap<>();

        for (final EnumDescriptor next : enumData_)
        {
            final String colName = next.getColumnName();
            descriptorMap.put(colName, next);

            if (!_colDescriptor.getEnumColumns().contains(colName))
            {
                throw new IllegalArgumentException("Enum descriptor not for valid enum column: " + colName);
            }
        }

        _enumData = Collections.unmodifiableSortedMap(descriptorMap);

        final SortedMap<String, NumericDescriptor> numericMap = new TreeMap<>();

        for (final NumericDescriptor next : numericData_)
        {
            final String colName = next.getColumnName();
            numericMap.put(colName, next);

            if (!_colDescriptor.getNumericColumns().contains(colName))
            {
                throw new IllegalArgumentException("Numeric descriptor not for valid numeric column: " + colName);
            }
        }

        _numericData = Collections.unmodifiableSortedMap(numericMap);

        _endStatusLabels = Collections.unmodifiableSortedSet(new TreeSet<>(endStatusLabels_));

        if (_endStatusLabels.size() <= 1)
        {
            throw new IllegalArgumentException("Need at least two possible end statuses.");
        }

        final List<String> names = new ArrayList<>();
        final List<RawConverter> converters = new ArrayList<>();
        final List<SimpleStringEnum> targetRegressors = new ArrayList<>();

        final EnumFamily<SimpleStringEnum> rawFamily = _colDescriptor.getAllColumns();

        //Start with the really easy ones...
        for (final String next : _colDescriptor.getBooleanColumns())
        {
            final SimpleStringEnum underlying = rawFamily.getFromName(next);

            names.add(next);
            converters.add(BOOLEAN_CONVERTER);
            targetRegressors.add(underlying);
        }

        //Doubles are also pretty easy.
        for (final String next : _colDescriptor.getNumericColumns())
        {
            final SimpleStringEnum underlying = rawFamily.getFromName(next);
            final NumericDescriptor descriptor = this.getNumericDescriptor(next);

            names.add(next);
            converters.add(DOUBLE_CONVERTER);
            targetRegressors.add(underlying);

            if (descriptor.getCanBeNaN())
            {
                final String isNaNName = next + "[IsNaN]";
                names.add(isNaNName);
                converters.add(ISNAN_CONVERTER);
                targetRegressors.add(underlying);
            }
        }

        //Enums are messier.
        for (final String next : _colDescriptor.getEnumColumns())
        {
            final SimpleStringEnum underlying = rawFamily.getFromName(next);
            final EnumDescriptor descriptor = this.getEnumDescriptor(next);

            for (final String nextVal : descriptor.getValues())
            {
                if (descriptor.getDefaultValue().equals(nextVal))
                {
                    //We are skipping this one.
                    continue;
                }

                final String targetName = next + "[" + nextVal + "]";
                names.add(targetName);
                converters.add(new StringMatchConverter(nextVal));
                targetRegressors.add(underlying);
            }
        }

        //All three are the same size.
        _regFamily = SimpleRegressor.generateFamily(names);
        _converters = converters.toArray(new RawConverter[converters.size()]);
        _offsets = new int[targetRegressors.size()];

        for (int i = 0; i < targetRegressors.size(); i++)
        {
            _offsets[i] = targetRegressors.get(i).ordinal();
        }

        _statusFamily = SimpleStatus.generateFamily(endStatusLabels_);
    }

    public ColumnDescriptorSet getColDescriptorSet()
    {
        return _colDescriptor;
    }

    public EnumDescriptor getEnumDescriptor(final String enumColumn_)
    {
        if (!_enumData.containsKey(enumColumn_))
        {
            throw new IllegalArgumentException("Not a valid enum column: " + enumColumn_);
        }

        return _enumData.get(enumColumn_);
    }

    public NumericDescriptor getNumericDescriptor(final String numericColumn_)
    {
        if (!_numericData.containsKey(numericColumn_))
        {
            throw new IllegalArgumentException("Not a valid enum column: " + numericColumn_);
        }

        return _numericData.get(numericColumn_);
    }

    /**
     * Converts the given string values to the double values appropriate for
     * this regressor family.
     *
     * @param stringValues_
     * @param output_
     */
    public void convertRow(final String[] stringValues_, final double[] output_)
    {
        final int colCount = _colDescriptor.getAllColumns().size();

        if (stringValues_.length != colCount)
        {
            throw new IllegalArgumentException("String array size mismatch: " + stringValues_.length + " != " + colCount);
        }
        if (output_.length != _regFamily.size())
        {
            throw new IllegalArgumentException("Output array size mismatch: " + output_.length + " != " + _regFamily.size());
        }

        for (int i = 0; i < output_.length; i++)
        {
            final String raw = stringValues_[_offsets[i]];
            final double converted = _converters[i].convert(raw);
            output_[i] = converted;
        }
    }

    public SimpleStatus convertStatus(final String statusName_)
    {
        return _statusFamily.getFromName(statusName_);
    }

    private interface RawConverter
    {
        public double convert(final String input_);
    }

    private static final class IsNaNConverter implements RawConverter
    {
        @Override
        public double convert(String input_)
        {
            try
            {
                final double converted = Double.parseDouble(input_);

                if (Double.isNaN(converted))
                {
                    return 1.0;
                }
                else
                {
                    return 0.0;
                }
            }
            catch (final NumberFormatException e)
            {
                return 1.0;
            }
        }

    }

    private static final class DoubleConverter implements RawConverter
    {
        @Override
        public double convert(String input_)
        {
            try
            {
                final double converted = Double.parseDouble(input_);
                return converted;
            }
            catch (final NumberFormatException e)
            {
                return 0.0;
            }
        }
    }

    private static final class BooleanConverter implements RawConverter
    {
        @Override
        public double convert(String input_)
        {
            final boolean converted = Boolean.parseBoolean(input_);

            if (converted)
            {
                return 1.0;
            }

            return 0.0;
        }
    }

    private static final class StringMatchConverter implements RawConverter
    {
        private final String _target;

        private StringMatchConverter(final String target_)
        {
            _target = target_;
        }

        @Override
        public double convert(String input_)
        {
            if (_target.equals(input_))
            {
                return 1.0;
            }

            return 0.0;
        }
    }

}

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
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.ListTool;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 *
 * @author tyler
 */
public final class CsvLoader
{
    private final File _inputFile;
    private final ColumnDescriptorSet _descriptor;
    private final CSVFormat _format;

    public CsvLoader(final File inputFile_, final ColumnDescriptorSet descriptor_)
    {
        this(inputFile_, descriptor_, CSVFormat.DEFAULT.withRecordSeparator("\n"));
    }

    public CsvLoader(final File inputFile_, final ColumnDescriptorSet descriptor_, final CSVFormat format_)
    {
        _inputFile = inputFile_;
        _descriptor = descriptor_;
        _format = format_;
    }

    public static void main(final String[] args_)
    {
        try
        {
            final File testFile = new File("/Users/tyler/Documents/code/data/sampleData.csv");
            final ColumnDescriptorSet descriptor = new ColumnDescriptorSet("adjstatus", ListTool.toSet("fico", "ltv", "dti"), ListTool.toSet("investor", "investor"), ListTool.toSet("state"));
            final CsvLoader loader = new CsvLoader(testFile, descriptor);

            final CompiledDataDescriptor compiled = loader.getCompiledDescriptor();

            final ItemStatusGrid<SimpleStatus, SimpleRegressor> grid = loader.generateGrid(compiled, null);

        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }
    }

    public ItemStatusGrid<SimpleStatus, SimpleRegressor> generateGrid(final CompiledDataDescriptor descriptor_, final SimpleStatus startStatus_) throws IOException
    {
        final ColumnDescriptorSet colDescriptor = descriptor_.getColDescriptorSet();

        try (final InputStream fin = new FileInputStream(_inputFile); final BufferedReader buff = new BufferedReader(new InputStreamReader(fin)))
        {
            final CSVParser parser = _format.parse(buff);

            final Map<String, Integer> header = parser.getHeaderMap();

            final EnumFamily<SimpleStringEnum> rawFamily = colDescriptor.getAllColumns();
            final int rawSize = rawFamily.size();

            final String[] strings = new String[rawSize];
            final int[] offsets = new int[rawSize];

            for (int i = 0; i < rawSize; i++)
            {
                final String colName = rawFamily.getFromOrdinal(i).name();
                offsets[i] = header.get(colName);
            }

            //Now, iterate over the rows and collect the required information.
            final Iterator<CSVRecord> iter = parser.iterator();
            final EnumFamily<SimpleRegressor> regFamily = descriptor_.getRegressorFamily();
            final EnumFamily<SimpleStatus> statFamily = descriptor_.getStatusFamily();
            final int regSize = regFamily.size();
            final List<double[]> valList = new ArrayList<>();
            final List<SimpleStatus> statList = new ArrayList<>();

            final String endStatusColumn = colDescriptor.getEndStatusColumn();
            final String startStatusColumn = colDescriptor.getStartStatusColumn();
            final int toStatusOffset = header.get(endStatusColumn);
            final int startStatusOffset;

            if (null != startStatus_ && null != startStatusColumn)
            {
                startStatusOffset = header.get(startStatusColumn);
            }
            else
            {
                startStatusOffset = -1;
            }

            while (iter.hasNext())
            {
                final CSVRecord next = iter.next();

                for (int i = 0; i < rawSize; i++)
                {
                    final String nextString = next.get(offsets[i]);
                    strings[i] = nextString;
                }

                final double[] regressors = new double[regSize];

                descriptor_.convertRow(strings, regressors);

                final String endStatus = next.get(toStatusOffset);
                final SimpleStatus endConverted = descriptor_.convertStatus(endStatus);

                if (startStatusOffset >= 0)
                {
                    final String startStatus = next.get(startStatusOffset);
                    final SimpleStatus startConverted = descriptor_.convertStatus(startStatus);

                    if (startConverted != startStatus_)
                    {
                        continue;
                    }
                }

                valList.add(regressors);
                statList.add(endConverted);
            }

            final RawStatusGrid output = new RawStatusGrid(startStatus_, valList, statList, statFamily, regFamily);
            return output;
        }
    }

    public CompiledDataDescriptor getCompiledDescriptor() throws IOException
    {
        try (final InputStream fin = new FileInputStream(_inputFile); final BufferedReader buff = new BufferedReader(new InputStreamReader(fin)))
        {
            final CSVParser parser = _format.withHeader().parse(buff);

            //Now, iterate over the rows and collect the required information.
            final Iterator<CSVRecord> iter = parser.iterator();
            final Map<String, Integer> header = parser.getHeaderMap();

            final int[] numericOffsets = extractOffsets(header, _descriptor.getNumericColumns());
            final int[] booleanOffsets = extractOffsets(header, _descriptor.getBooleanColumns());
            final int[] enumOffsets = extractOffsets(header, _descriptor.getEnumColumns());

            final boolean[] numericValid = new boolean[numericOffsets.length];
            final boolean[] numericNaN = new boolean[numericOffsets.length];
            final boolean[] bTrue = new boolean[booleanOffsets.length];
            final boolean[] bFalse = new boolean[booleanOffsets.length];

            final int endStatusOffset = header.get(_descriptor.getEndStatusColumn());
            final Set<String> endStatusLabels = new TreeSet<>();

            final List<Map<String, Long>> enumValues = new ArrayList<>(enumOffsets.length);

            for (int i = 0; i < enumOffsets.length; i++)
            {
                enumValues.add(new TreeMap<>());
            }

            while (iter.hasNext())
            {
                final CSVRecord next = iter.next();

                final String endStatus = next.get(endStatusOffset);
                endStatusLabels.add(endStatus);

                for (int i = 0; i < numericOffsets.length; i++)
                {
                    if (numericValid[i] && numericNaN[i])
                    {
                        //We already know this could be both, skip.
                        continue;
                    }

                    final String numberString = next.get(numericOffsets[i]);
                    final double dVal = StringConvert.convertDouble(numberString);

                    if (Double.isNaN(dVal))
                    {
                        numericNaN[i] = true;
                    }
                    else
                    {
                        numericValid[i] = true;
                    }
                }

                for (int i = 0; i < booleanOffsets.length; i++)
                {
                    if (bTrue[i] && bFalse[i])
                    {
                        //We already know this could be both, skip.
                        continue;
                    }

                    final String booleanString = next.get(booleanOffsets[i]);
                    final boolean bValue = StringConvert.convertBoolean(booleanString);

                    if (bValue)
                    {
                        bTrue[i] = true;
                    }
                    else
                    {
                        bFalse[i] = true;
                    }
                }

                for (int i = 0; i < enumOffsets.length; i++)
                {
                    final String enumString = next.get(enumOffsets[i]);

                    final Map<String, Long> instances = enumValues.get(i);

                    if (!instances.containsKey(enumString))
                    {
                        instances.put(enumString, 1L);
                    }
                    else
                    {
                        final long prevCount = instances.get(enumString);
                        instances.put(enumString, prevCount + 1);
                    }
                }
            } // end while.

            //First, verify that all our boolean columns take on both possible values.
            for (int i = 0; i < booleanOffsets.length; i++)
            {
                if (bTrue[i] && bFalse[i])
                {
                    continue;
                }

                throw new IllegalArgumentException("Boolean column is always true or always false: " + i);
            }

            final List<NumericDescriptor> numericDescriptors = new ArrayList<>();

            int pointer = 0;

            for (final String numericColumn : _descriptor.getNumericColumns())
            {
                if (!numericValid[pointer])
                {
                    throw new IllegalArgumentException("Numeric column is always NaN (or misformatted): " + numericColumn);
                }

                final boolean canBeNull = numericNaN[pointer];

                final NumericDescriptor des = new NumericDescriptor(numericColumn, canBeNull);
                numericDescriptors.add(des);
                pointer++;
            }

            pointer = 0;
            final List<EnumDescriptor> enumDescriptors = new ArrayList<>();

            for (final String enumColumn : _descriptor.getEnumColumns())
            {
                final Map<String, Long> values = enumValues.get(pointer);

                if (values.size() < 2)
                {
                    throw new IllegalArgumentException("Enum must have at least two possible values: " + values);
                }

                long maxCount = 0;
                String mostPopular = null;

                for (final Map.Entry<String, Long> entry : values.entrySet())
                {
                    final long thisCount = entry.getValue();
                    final String thisString = entry.getKey();

                    if (thisCount > maxCount)
                    {
                        maxCount = thisCount;
                        mostPopular = thisString;
                    }
                }

                final EnumDescriptor des = new EnumDescriptor(enumColumn, values.keySet(), mostPopular);
                enumDescriptors.add(des);
                pointer++;
            }

            final CompiledDataDescriptor compiled = new CompiledDataDescriptor(_descriptor, endStatusLabels, enumDescriptors, numericDescriptors);
            return compiled;
        }

    }

    private static int[] extractOffsets(final Map<String, Integer> header_, final Set<String> regressors_)
    {
        final int[] offsets = new int[regressors_.size()];

        int pointer = 0;

        for (final String next : regressors_)
        {
            if (!header_.containsKey(next))
            {
                throw new IllegalArgumentException("Column not found: " + next);
            }

            final int offset = header_.get(next);
            offsets[pointer++] = offset;
        }

        return offsets;
    }

}

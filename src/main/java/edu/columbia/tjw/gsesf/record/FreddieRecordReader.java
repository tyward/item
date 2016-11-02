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
import static edu.columbia.tjw.gsesf.types.RawDataType.BOOLEAN;
import static edu.columbia.tjw.gsesf.types.RawDataType.DATE;
import static edu.columbia.tjw.gsesf.types.RawDataType.DOUBLE;
import static edu.columbia.tjw.gsesf.types.RawDataType.INT;
import static edu.columbia.tjw.gsesf.types.RawDataType.STRING;
import edu.columbia.tjw.gsesf.types.TypedField;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.InstancePool;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author tyler
 * @param <T>
 */
public final class FreddieRecordReader<T extends TypedField<T>> extends StringRecordReader<T>
{
    private final T[] _recordHeaders;
    private final ZipFile _zf;
    private final ZipEntry _entry;
    private final DataRecord.RecordBuilder<T> _builder;

    //Take some basic efforts to deduplicate some of this data. Will make the files much smaller. 
    private final InstancePool<String> _stringPool;
    private final InstancePool<LocalDate> _datePool;
    private int _recordCount;

    public FreddieRecordReader(T[] recordHeaders_, EnumFamily<T> family_, final ZipFile zf_, final ZipEntry entry_)
    {
        super(extractRecordHeader(recordHeaders_, family_), entry_.getName());
        _recordHeaders = recordHeaders_.clone();

        _zf = zf_;
        _entry = entry_;
        _builder = new DataRecord.RecordBuilder<>(this.getHeader());

        _stringPool = new InstancePool<>();
        _datePool = new InstancePool<>();
    }

    @Override
    public final InputStream getInputStream() throws IOException
    {
        final InputStream output = _zf.getInputStream(_entry);
        return output;
    }

    @Override
    public synchronized DataRecord<T> generateRecord(final String line_)
    {
        _recordCount++;

        if ((_recordCount % 100 * 1000) == 0)
        {
            //Periodically, clear out the instance pools. We want to dedupe, but
            //for things that don't have much duplication, we can't just accumulate them forever.
            _stringPool.clear();
            _datePool.clear();
        }

        final String[] values = line_.split("\\|");

        //they truncate lines after the last non-null element....
        if (values.length > _recordHeaders.length)
        {
            throw new IllegalArgumentException("Malformed line.");
        }

        _builder.clear();

        for (int i = 0; i < _recordHeaders.length; i++)
        {
            final T header = _recordHeaders[i];

            if (null == header)
            {
                //This is a field we don't care about, so just skip it. 
                continue;
            }

            if (i >= values.length)
            {
                //Truncated line, the rest of it is null.
                setEntry(null, header, _builder);
                continue;
            }

            final String strVal = values[i];
            setEntry(strVal, header, _builder);
        }

        final DataRecord<T> output = _builder.generateRecord();
        return output;
    }

    private void setEntry(final String val_, final T header_, final DataRecord.RecordBuilder<T> builder_)
    {
        if (null == val_)
        {
            builder_.setNull(header_);
            return;
        }

        final String trimmed = val_.trim();

        if (trimmed.isEmpty())
        {
            builder_.setNull(header_);
            return;
        }

        final RawDataType type = header_.getType();

        try
        {
            switch (type)
            {
                case DOUBLE:
                {
                    final double val = Double.parseDouble(trimmed);
                    builder_.setDouble(header_, val);
                    break;
                }
                case INT:
                {
                    final int val = Integer.parseInt(trimmed);
                    builder_.setInt(header_, val);
                    break;
                }
                case STRING:
                {
                    final String dedupe = _stringPool.makeCanonical(trimmed);
                    builder_.setString(header_, dedupe);
                    break;
                }
                case BOOLEAN:
                {
                    final boolean val = "Y".equals(trimmed);
                    builder_.setBoolean(header_, val);
                    break;
                }
                case DATE:
                {
                    final String expanded = trimmed + "01";
                    final LocalDate date = LocalDate.from(DateTimeFormatter.BASIC_ISO_DATE.parse(expanded));
                    final LocalDate dedupe = _datePool.makeCanonical(date);
                    builder_.setDate(header_, dedupe);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown type: " + type);
            }
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static <T extends TypedField<T>> RecordHeader<T> extractRecordHeader(final T[] recordHeaders_, final EnumFamily<T> family_)
    {
        final SortedSet<T> headerSet = new TreeSet<>();
        int headerCount = 0;

        for (final T next : recordHeaders_)
        {
            if (null != next)
            {
                headerSet.add(next);
                headerCount++;
            }
        }

        if (headerCount != headerSet.size())
        {
            throw new IllegalArgumentException("Headers are not distinct!");
        }

        final RecordHeader<T> header = new RecordHeader<>(headerSet, family_);
        return header;
    }

}

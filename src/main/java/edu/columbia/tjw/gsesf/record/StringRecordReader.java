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

import edu.columbia.tjw.gsesf.types.TypedField;
import edu.columbia.tjw.item.util.LogUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 * @param <T>
 */
public abstract class StringRecordReader<T extends TypedField<T>> implements Iterable<DataRecord<T>>
{
    private static final Logger LOG = LogUtil.getLogger(StringRecordReader.class);
    private final RecordHeader<T> _header;

    protected StringRecordReader(final RecordHeader<T> header_)
    {
        _header = header_;
    }

    @Override
    public final Iterator<DataRecord<T>> iterator()
    {
        try
        {
            final InputStream stream = this.getInputStream();
            final InnerIterator output = new InnerIterator(stream);
            return output;
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private final class InnerIterator implements Iterator<DataRecord<T>>
    {
        private final BufferedReader _reader;
        private DataRecord<T> _next;
        private int _count;

        public InnerIterator(final InputStream stream_) throws IOException
        {
            _reader = new BufferedReader(new InputStreamReader(stream_));
            _next = attemptRead();
            _count = 0;
        }

        @Override
        public boolean hasNext()
        {
            final boolean hasNext = (null != _next);
            return hasNext;
        }

        @Override
        public DataRecord<T> next()
        {
            if (!hasNext())
            {
                throw new IllegalStateException("Iteration past end of iterator.");
            }

            final DataRecord<T> output = _next;

            try
            {
                _next = attemptRead();
            }
            catch (final IOException e)
            {
                throw new RuntimeException(e);
            }

            return output;
        }

        private DataRecord<T> attemptRead() throws IOException
        {
            if ((_count++ % 1000) == 0)
            {
                LOG.info("Reading record: " + _count);
            }

            final String line = _reader.readLine();

            if (null == line)
            {
                _reader.close();
                return null;
            }

            final DataRecord<T> output = generateRecord(line);
            return output;
        }
    }

    public final RecordHeader<T> geHeader()
    {
        return _header;
    }

    public abstract InputStream getInputStream() throws IOException;

    public abstract DataRecord<T> generateRecord(final String line_);
}

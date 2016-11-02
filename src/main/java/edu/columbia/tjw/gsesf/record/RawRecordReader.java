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
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.LogUtil;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author tyler
 * @param <T>
 */
public final class RawRecordReader<T extends TypedField<T>> implements RecordReader<T>
{
    private static final Logger LOG = LogUtil.getLogger(RawRecordReader.class);
    private static final int BLOCK_SIZE = 10 * 1000;

    private final File _inputFile;
    private final RecordHeader<T> _header;
    private final boolean _isZip;

    public RawRecordReader(final File inputFile_, final EnumFamily<T> family_, final boolean isZip_) throws IOException
    {
        _inputFile = inputFile_;
        _isZip = isZip_;
        final ObjectInputStream inputStream = getStream();

        final RecordHeader<?> header = (RecordHeader<?>) readObject(inputStream);
        _header = header.castAsType(family_.getComponentType());
    }

    @Override
    public final RecordHeader<T> getHeader()
    {
        return _header;
    }

    @Override
    public final Iterator<DataRecord<T>> iterator()
    {
        try
        {
            final ObjectInputStream oIn = getStream();
            final InnerIterator iter = new InnerIterator(oIn);
            return iter;
        }
        catch (final IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private final class InnerIterator implements Iterator<DataRecord<T>>
    {
        private final ObjectInputStream _reader;
        private DataRecord<T> _next;
        private long _count;

        public InnerIterator(final ObjectInputStream stream_) throws IOException
        {
            _reader = stream_;

            //First, strip off the RecordHeader, we already have that...
            final RecordHeader<?> header = (RecordHeader<?>) readObject(stream_);

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
            _count++;

            if ((_count % BLOCK_SIZE) == 0)
            {
                LOG.info("Reading record[" + _inputFile.getName() + "]: " + _count);
            }

            final DataRecord<?> rawRecord = (DataRecord<?>) readObject(_reader);

            if (null == rawRecord)
            {
                _reader.close();
                return null;
            }

            final DataRecord<T> typed = rawRecord.castAsType(_header.getFamily().getComponentType());

            return typed;
        }

    }

    public ObjectInputStream getStream() throws IOException
    {
        InputStream rawStream = new FileInputStream(_inputFile);

        if (_isZip)
        {
            rawStream = new GZIPInputStream(rawStream);
        }

        final ObjectInputStream oStream = new ObjectInputStream(new BufferedInputStream(rawStream));
        return oStream;
    }

    private static Object readObject(final ObjectInputStream oIn_) throws IOException
    {
        try
        {
            final Object output = oIn_.readObject();
            return output;
        }
        catch (final ClassNotFoundException e)
        {
            throw new IOException(e);
        }
        catch (final EOFException e)
        {
            return null;
        }
    }
}

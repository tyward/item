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
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 *
 * @author tyler
 * @param <T>
 */
public final class RecordWriter<T extends TypedField<T>>
{
    private final RecordHeader<T> _header;
    private final ObjectOutputStream _oOutput;
    private boolean _isClosed;

    public RecordWriter(final RecordHeader<T> header_, final OutputStream output_) throws IOException
    {
        _header = header_;
        _oOutput = new ObjectOutputStream(output_);
        _oOutput.writeObject(_header);
        _isClosed = false;
    }

    public void writeRecord(final DataRecord<T> record_) throws IOException
    {
        if (null == record_)
        {
            throw new NullPointerException("Cannot write a null record.");
        }

        final RecordHeader<T> recHeader = record_.getHeader();

        if (recHeader != _header)
        {
            throw new IllegalArgumentException("Header objects must match exactly (ensures that we don't write duplicate headers to the stream).");
        }

        _oOutput.writeObject(record_);
    }

    public void writeAllRecords(final Iterable<DataRecord<T>> recIterable_) throws IOException
    {
        for (final DataRecord<T> next : recIterable_)
        {
            writeRecord(next);
        }
    }

    public boolean isClosed()
    {
        return _isClosed;
    }

    public void close() throws IOException
    {
        if (isClosed())
        {
            return;
        }

        this._isClosed = true;

        try
        {
            _oOutput.flush();
        }
        finally
        {
            _oOutput.close();
        }
    }

}

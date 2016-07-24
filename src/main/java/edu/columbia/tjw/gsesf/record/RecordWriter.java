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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author tyler
 * @param <T>
 */
public final class RecordWriter<T extends TypedField<T>>
{
    private static final Logger LOG = LogUtil.getLogger(RecordWriter.class);

    private static final int FLUSH_SIZE = 100 * 1000;
    private static final int GZIP_BUFFER_SIZE = 4 * 1024;

    private final RecordHeader<T> _header;
    private final ObjectOutputStream _oOutput;
    private final File _outputFile;
    private boolean _isClosed;

    private int _recordCount;

    public RecordWriter(final RecordHeader<T> header_, final File outputFile_, final boolean zip_) throws IOException
    {
        _outputFile = outputFile_;
        _header = header_;

        final FileOutputStream fStream = new FileOutputStream(outputFile_);
        OutputStream wrapped = new BufferedOutputStream(fStream);

        if (zip_)
        {
            wrapped = new GZIPOutputStream(wrapped, GZIP_BUFFER_SIZE, true);
        }

        _oOutput = new ObjectOutputStream(wrapped);
        _oOutput.writeObject(_header);
        _isClosed = false;
        _recordCount = 0;
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

        _recordCount++;

        //_oOutput.write(record_.hashCode());
        _oOutput.writeObject(record_);

        if ((_recordCount % FLUSH_SIZE) == 0)
        {
            //Periodically, flush so we don't use too much memory...
            this.flush();
        }

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

    public void flush() throws IOException
    {
        _oOutput.flush();

        //This prevents our state from building up too much. 
        _oOutput.reset();
        LOG.info("Flushing records[" + _outputFile.getName() + "]: " + _recordCount);
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
            this.flush();
        }
        finally
        {
            _oOutput.close();
        }
    }

}

package edu.columbia.tjw.item.base.raw;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemRegressorReader;
import edu.columbia.tjw.item.data.ItemGrid;
import edu.columbia.tjw.item.util.EnumFamily;

import java.io.*;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A serializable version of the ItemGrid. Allows for data to be coveniently cached and moved around.
 * <p>
 * Converts doubles to floats, so some loss of precision occurs, but reduces needed space by 50%.
 *
 * @param <R>
 */
public class RawItemGrid<R extends ItemRegressor<R>> implements ItemGrid<R>, Serializable
{
    private final Set<R> _availableRegressors;
    private final int _size;
    private final EnumFamily<R> _regressorFamily;
    private final ItemRegressorReader[] _readers;

    public RawItemGrid(ItemGrid<R> underlying_)
    {
        _availableRegressors = underlying_.getAvailableRegressors();
        _size = underlying_.size();
        _regressorFamily = underlying_.getRegressorFamily();
        _readers = new ItemRegressorReader[_regressorFamily.size()];

        for (final R next : _availableRegressors)
        {
            _readers[next.ordinal()] = new RawRegressorReader(underlying_.getRegressorReader(next));
        }
    }

    @Override
    public Set<R> getAvailableRegressors()
    {
        return _availableRegressors;
    }

    @Override
    public ItemRegressorReader getRegressorReader(R field_)
    {
        return _readers[field_.ordinal()];
    }

    @Override
    public int size()
    {
        return _size;
    }

    @Override
    public EnumFamily<R> getRegressorFamily()
    {
        return _regressorFamily;
    }

    private final class RawRegressorReader implements ItemRegressorReader, Serializable
    {
        private final float[] _data;

        public RawRegressorReader(ItemRegressorReader underlying_)
        {
            _data = new float[this.size()];

            if (this.size() != underlying_.size())
            {
                throw new IllegalArgumentException("Size mismatch.");
            }

            for (int i = 0; i < this.size(); i++)
            {
                _data[i] = (float) underlying_.asDouble(i);
            }
        }


        @Override
        public double asDouble(int index_)
        {
            return _data[index_];
        }

        @Override
        public int size()
        {
            return _size;
        }
    }

    public void writeToStream(final OutputStream stream_) throws IOException
    {
        try (final GZIPOutputStream zipout = new GZIPOutputStream(stream_);
             final ObjectOutputStream oOut = new ObjectOutputStream(zipout))
        {
            oOut.writeObject(this);
            oOut.flush();
        }
    }

    public static <R2 extends ItemRegressor<R2>>
    RawItemGrid<R2> readFromStream(final InputStream stream_,
                                   final Class<R2> regClass_)
            throws IOException
    {
        try (final GZIPInputStream zipin = new GZIPInputStream(stream_);
             final ObjectInputStream oIn = new ObjectInputStream(zipin))
        {
            final RawItemGrid<?> raw = (RawItemGrid<?>) oIn.readObject();

            if (raw._regressorFamily.getComponentType() != regClass_)
            {
                throw new IOException("Wrong class type!");
            }

            final RawItemGrid<R2> typed = (RawItemGrid<R2>) raw;
            return typed;
        }
        catch (ClassNotFoundException e)
        {
            throw new IOException(e);
        }
    }
}

package edu.columbia.tjw.item.base.raw;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemSettings;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.data.ItemStatusGrid;
import edu.columbia.tjw.item.data.RandomizedStatusGrid;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RawFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>>
        extends RawItemGrid<R> implements ItemFittingGrid<S, R>
{
    private final S _fromStatus;
    private final int[] _nextStatus;

    public RawFittingGrid(ItemFittingGrid<S, R> underlying_)
    {
        super(underlying_);

        _fromStatus = underlying_.getFromStatus();
        final int size = underlying_.size();
        _nextStatus = new int[size];

        for (int i = 0; i < size; i++)
        {
            _nextStatus[i] = underlying_.getNextStatus(i);
        }
    }

    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>>
    RawFittingGrid<S, R> fromStatusGrid(final ItemStatusGrid<S, R> grid_, final S status_)
    {
        final ItemSettings noShuffleSettings = (new ItemSettings()).toBuilder().setRandomShuffle(false).build();
        return fromStatusGrid(grid_, noShuffleSettings, status_);
    }

    public static <S extends ItemStatus<S>, R extends ItemRegressor<R>>
    RawFittingGrid<S, R> fromStatusGrid(final ItemStatusGrid<S, R> grid_, final ItemSettings settings_, final S status_)
    {
        final ItemFittingGrid<S, R> wrapped = new RandomizedStatusGrid<>(grid_, settings_,
                grid_.getRegressorFamily(), status_);
        return new RawFittingGrid<>(wrapped);
    }

    @Override
    public S getFromStatus()
    {
        return _fromStatus;
    }

    @Override
    public int getNextStatus(int index_)
    {
        return _nextStatus[index_];
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

    public static <S2 extends ItemStatus<S2>, R2 extends ItemRegressor<R2>>
    RawFittingGrid<S2, R2> readFromStream(
            final InputStream stream_, final Class<S2> statusClass_,
            final Class<R2> regClass_)
            throws IOException
    {
        try (final GZIPInputStream zipin = new GZIPInputStream(stream_);
             final ObjectInputStream oIn = new ObjectInputStream(zipin))
        {
            final RawFittingGrid<?, ?> raw = (RawFittingGrid<?, ?>) oIn.readObject();

            if (raw.getRegressorFamily().getComponentType() != regClass_)
            {
                throw new IOException("Wrong class type!");
            }
            if (raw.getFromStatus().getClass() != statusClass_)
            {
                throw new IOException("Wrong class type!");
            }

            final RawFittingGrid<S2, R2> typed = (RawFittingGrid<S2, R2>) raw;
            return typed;
        }
        catch (ClassNotFoundException e)
        {
            throw new IOException(e);
        }
    }
}

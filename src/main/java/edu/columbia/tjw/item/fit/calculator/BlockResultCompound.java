package edu.columbia.tjw.item.fit.calculator;

import java.util.ArrayList;
import java.util.List;

public final class BlockResultCompound
{
    private BlockResult _aggregated;
    private int _nextStart;

    private final List<BlockResult> _results;

    public BlockResultCompound()
    {
        _results = new ArrayList<>();
        _nextStart = 0;
    }

    public void setResult(final int index_, final BlockResult next_)
    {
        if (null == next_)
        {
            throw new NullPointerException("Next cannot be null.");
        }

        _results.set(index_, next_);
    }

    public void appendResult(final BlockResult next_)
    {
        if (null == next_)
        {
            throw new NullPointerException("Next cannot be null.");
        }

        if (next_.getRowStart() != _nextStart)
        {
            throw new IllegalArgumentException("Blocks must be added in order.");
        }

        _nextStart = next_.getRowEnd();
        _results.add(next_);

        _aggregated = null;
    }

    public BlockResult getAggregated()
    {
        if (null == _aggregated)
        {
            _aggregated = new BlockResult(_results);
        }

        return _aggregated;
    }

    public int getNextStart()
    {
        return _nextStart;
    }

    public int getBlockCount()
    {
        return _results.size();
    }

    public BlockResult getBlock(final int index_)
    {
        return _results.get(index_);
    }


}

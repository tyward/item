package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;

public final class ReducedParameterVector<S extends ItemStatus<S>, R extends ItemRegressor<R>,
        T extends ItemCurveType<T>> implements PackedParameters<S, R, T>
{
    private final int[] _keepIndices;
    private final PackedParameters<S, R, T> _underlying;

    private ReducedParameterVector(final ReducedParameterVector<S, R, T> cloneFrom_)
    {
        _keepIndices = cloneFrom_._keepIndices;
        _underlying = cloneFrom_._underlying.clone();
    }

    public ReducedParameterVector(final boolean[] keep_, final PackedParameters<S, R, T> underlying_)
    {
        _underlying = underlying_.clone();

        if (keep_.length != underlying_.size())
        {
            throw new IllegalArgumentException("Mask is the wrong size.");
        }

        int count = 0;

        for (final boolean next : keep_)
        {
            if (next)
            {
                count++;
            }
        }

        if (count < 1)
        {
            throw new IllegalArgumentException("Invalid keep count.");
        }

        _keepIndices = new int[count];
        int pointer = 0;

        for (int i = 0; i < keep_.length; i++)
        {
            if (keep_[i])
            {
                _keepIndices[pointer++] = i;
            }
        }
    }

    @Override
    public int size()
    {
        return _keepIndices.length;
    }

    @Override
    public double[] getPacked()
    {
        final double[] output = new double[this.size()];

        for (int i = 0; i < this.size(); i++)
        {
            output[i] = getParameter(i);
        }

        return output;
    }

    @Override
    public void updatePacked(double[] newParams_)
    {
        if (newParams_.length != this.size())
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        for (int i = 0; i < newParams_.length; i++)
        {
            this.setParameter(i, newParams_[i]);
        }
    }

    @Override
    public double getParameter(int index_)
    {
        return _underlying.getParameter(translate(index_));
    }

    @Override
    public void setParameter(int index_, double value_)
    {
        _underlying.setParameter(translate(index_), value_);
    }

    @Override
    public double getEntryBeta(int index_)
    {
        return _underlying.getEntryBeta(translate(index_));
    }

    @Override
    public boolean isBeta(int index_)
    {
        return _underlying.isBeta(translate(index_));
    }

    @Override
    public boolean betaIsFrozen(int index_)
    {
        return _underlying.betaIsFrozen(translate(index_));
    }

    @Override
    public boolean isCurve(int index_)
    {
        return _underlying.isCurve(translate(index_));
    }

    @Override
    public int getTransition(int index_)
    {
        return _underlying.getTransition(translate(index_));
    }

    @Override
    public int getEntry(int index_)
    {
        return _underlying.getEntry(translate(index_));
    }

    @Override
    public int getDepth(int index_)
    {
        return _underlying.getDepth(translate(index_));
    }

    @Override
    public int getCurveIndex(int index_)
    {
        return _underlying.getCurveIndex(translate(index_));
    }

    @Override
    public ItemParameters<S, R, T> generateParams()
    {
        return _underlying.generateParams();
    }

    @Override
    public ItemParameters<S, R, T> getOriginalParams()
    {
        return _underlying.getOriginalParams();
    }

    private int translate(final int index_)
    {
        return _keepIndices[index_];
    }

    public PackedParameters<S, R, T> clone()
    {
        return new ReducedParameterVector<S, R, T>(this);
    }
}

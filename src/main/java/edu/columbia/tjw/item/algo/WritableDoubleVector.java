package edu.columbia.tjw.item.algo;

/**
 * This is basically a union between a DoubleVector and a double[], allowing for efficient updates.
 */
public final class WritableDoubleVector
{
    private final int _length;
    private double[] _data;
    private DoubleVector _vector;

    public WritableDoubleVector(final DoubleVector vector_)
    {
        if (null == vector_)
        {
            throw new NullPointerException("Vector cannot be null.");
        }

        _length = vector_.getSize();
        _vector = vector_;
        _data = null;
    }

    public WritableDoubleVector(final int size_)
    {
        _length = size_;
        _vector = null;
        _data = new double[size_];
    }

    public DoubleVector getVector()
    {
        if (null == _vector)
        {
            _vector = DoubleVector.of(_data, false);
            _data = null;
        }

        return _vector;
    }

    public void setEntries(final DoubleVector vector_)
    {
        if (vector_.getSize() != this._length)
        {
            throw new IllegalArgumentException("Length mismatch.");
        }

        _vector = vector_;
        _data = null;
    }

    public void setEntry(final int index_, final double value_)
    {
        if (null == _data)
        {
            _data = _vector.copyOfUnderlying();
            _vector = null;
        }

        _data[index_] = value_;
    }

    public double getEntry(final int index_)
    {
        if (null != _vector)
        {
            return _vector.getEntry(index_);
        }
        else
        {
            return _data[index_];
        }
    }


    public int getSize()
    {
        return _length;
    }

}

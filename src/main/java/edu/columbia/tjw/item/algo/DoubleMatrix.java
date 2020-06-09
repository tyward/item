package edu.columbia.tjw.item.algo;

import java.io.Serializable;

/**
 * A rectangular matrix of numbers.
 */
public final class DoubleMatrix implements Serializable
{
    private static final long serialVersionUID = 0x648db9a83bb78d05L;

    private final DoubleVector[] _underlying;
    private final int _columSize;

    private transient DoubleMatrix _collapsed;

    private DoubleMatrix(final DoubleVector[] underlying_)
    {
        _underlying = underlying_;

        if (_underlying.length < 1)
        {
            _columSize = 0;
        }
        else
        {
            _columSize = _underlying[0].getSize();
        }

        _collapsed = null;

        for (int i = 0; i < underlying_.length; i++)
        {
            if (null == underlying_)
            {
                throw new NullPointerException("Underlying cannot be null.");
            }

            if (underlying_[i].getSize() != _columSize)
            {
                throw new IllegalArgumentException("Size mismatch.");
            }
        }
    }

    /**
     * @param row_
     * @param column_
     * @return
     */
    public double getEntry(final int row_, final int column_)
    {
        return getRow(row_).getEntry(column_);
    }

    /**
     * @param row_
     * @return
     */
    public DoubleVector getRow(final int row_)
    {
        return _underlying[row_];
    }

    /**
     * Returns the number of elements in this Vector.
     *
     * @return The number of elements in this Vector
     */
    public int getRowSize()
    {
        return _underlying.length;
    }

    /**
     * Returns the number of elements in this Vector.
     *
     * @return The number of elements in this Vector
     */
    public int getColumnSize()
    {
        return _columSize;
    }

    /**
     * Collapse this item down to a version with no computations.
     * <p>
     * For a vector made from a double[], this just returns itself.
     *
     * @return A version of this vector with all values fully computed.
     */
    public DoubleMatrix collapse()
    {
        if (null != _collapsed)
        {
            return _collapsed;
        }

        for (int i = 0; i < getRowSize(); i++)
        {
            DoubleVector rowVector = getRow(i);
            DoubleVector collapsed = rowVector.collapse();

            if (collapsed != rowVector)
            {
                DoubleVector[] output = new DoubleVector[getRowSize()];

                for (int k = 0; k < i; k++)
                {
                    output[k] = _underlying[k];
                }

                output[i] = collapsed;

                for (int k = i + 1; k < getRowSize(); k++)
                {
                    output[k] = _underlying[k].collapse();
                }

                _collapsed = new DoubleMatrix(output);
                return _collapsed;
            }
        }

        _collapsed = this;
        return _collapsed;
    }

    /**
     * Return a copy of the underlying data (as a double[]).
     *
     * @return
     */
    public final double[][] copyOfUnderlying()
    {
        final double[][] output = new double[this.getRowSize()][this.getColumnSize()];

        for (int i = 0; i < output.length; i++)
        {
            output[i] = this.getRow(i).copyOfUnderlying();
        }

        return output;
    }


    public DoubleMatrix of(final double[][] data_)
    {
        DoubleVector[] output = new DoubleVector[data_.length];


        return new DoubleMatrix(output);


    }


}

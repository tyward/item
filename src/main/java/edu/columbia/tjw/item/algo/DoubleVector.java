package edu.columbia.tjw.item.algo;

import java.io.Serializable;

/**
 * A read-only version of a double vector. This is useful where items might need to be passed around quite a lot.
 */
public abstract class DoubleVector implements Serializable
{
    private static final long serialVersionUID = 0x5aafcfe25b062dfaL;

    private DoubleVector()
    {
    }

    /**
     * Get the regressor for observation index_
     *
     * @param index_ The index of the observation
     * @return The value of the regressor for that observation.
     */
    public abstract double getEntry(final int index_);

    /**
     * Returns the number of elements in this Vector.
     *
     * @return The number of elements in this Vector
     */
    public abstract int getSize();

    /**
     * Return a copy of the underlying data (as a double[]).
     *
     * @return
     */
    public final double[] copyOfUnderlying()
    {
        final double[] output = new double[this.getSize()];

        for (int i = 0; i < output.length; i++)
        {
            output[i] = this.getEntry(i);
        }

        return output;
    }

    public static DoubleVector of(final double[] data_)
    {
        return of(data_, false);
    }

    public static DoubleVector of(final float[] data_)
    {
        return of(data_, false);
    }

    public static DoubleVector of(final double[] data_, final boolean doCopy_)
    {
        return new DoubleArrayVector(data_, doCopy_);
    }

    public static DoubleVector of(final float[] data_, final boolean doCopy_)
    {
        return new FloatArrayVector(data_, doCopy_);
    }

    private static final class DoubleArrayVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x2165692518a8c386L;

        private final double[] _underlying;

        private DoubleArrayVector(final double[] underlying_, final boolean doCopy_)
        {
            _underlying = underlying_.clone();
        }

        @Override
        public double getEntry(int index_)
        {
            return _underlying[index_];
        }

        @Override
        public int getSize()
        {
            return _underlying.length;
        }
    }

    private static final class FloatArrayVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x2d399701ea8edac8L;

        private final float[] _underlying;

        private FloatArrayVector(final float[] underlying_, final boolean doCopy_)
        {
            if (doCopy_)
            {
                _underlying = underlying_.clone();
            }
            else
            {
                _underlying = underlying_;
            }
        }

        @Override
        public double getEntry(int index_)
        {
            return _underlying[index_];
        }

        @Override
        public int getSize()
        {
            return _underlying.length;
        }
    }
}

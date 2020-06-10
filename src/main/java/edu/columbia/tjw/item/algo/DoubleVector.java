package edu.columbia.tjw.item.algo;

import org.apache.commons.math3.analysis.BivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;

import java.io.ObjectStreamException;
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
     * Collapse this item down to a version with no computations.
     * <p>
     * For a vector made from a double[], this just returns itself.
     *
     * @return A version of this vector with all values fully computed.
     */
    public abstract DoubleVector collapse();

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

    public static DoubleVector of(final DoubleVector.Builder builder_)
    {
        if (null == builder_)
        {
            return null;
        }

        return builder_.build();
    }

    public static DoubleVector of(final double[] data_)
    {
        if (null == data_)
        {
            return null;
        }

        return new DoubleArrayVector(data_, true);
    }

    public static DoubleVector of(final double[] data_, final boolean doCopy_)
    {
        if (null == data_)
        {
            return null;
        }

        return new DoubleArrayVector(data_, true);
    }

    public static DoubleVector of(final float[] data_)
    {
        if (null == data_)
        {
            return null;
        }

        return new FloatArrayVector(data_, true);
    }

    public static DoubleVector constantVector(final double value_, final int size_)
    {
        return new ConstantDoubleVector(value_, size_);
    }

    public static DoubleVector apply(final BivariateFunction function_, final DoubleVector a_, final DoubleVector b_)
    {
        return new BivariateFunctionDoubleVector(function_, a_, b_);
    }

    public static DoubleVector apply(final UnivariateFunction function_, final DoubleVector vector_)
    {
        return new FunctionDoubleVector(function_, vector_);
    }


    public static DoubleVector.Builder newBuilder(final int size_)
    {
        return new Builder(size_);
    }

    /**
     * Used to build double vectors without risk of underlying object leakage or excessive copying.
     */
    public static final class Builder
    {
        private double[] _data;

        private Builder(final int size_)
        {
            _data = new double[size_];
        }

        /**
         * Get the regressor for observation index_
         *
         * @param index_ The index of the observation
         * @return The value of the regressor for that observation.
         */
        public double getEntry(final int index_)
        {
            return _data[index_];
        }

        /**
         * Returns the number of elements in this Vector.
         *
         * @return The number of elements in this Vector
         */
        public int getSize()
        {
            return _data.length;
        }

        public void setEntry(final int index_, final double value_)
        {
            _data[index_] = value_;
        }

        public void addToEntry(final int index_, final double value_)
        {
            _data[index_] += value_;
        }

        public void scalarMultiply(final double value_)
        {
            for (int i = 0; i < _data.length; i++)
            {
                _data[i] *= value_;
            }
        }

        public DoubleVector build()
        {
            DoubleVector output = new DoubleArrayVector(_data, false);
            _data = null; // This will prevent any further modifications to the data array.
            return output;
        }
    }


    private static final class DoubleArrayVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x2165692518a8c386L;

        private final double[] _underlying;

        private DoubleArrayVector(final double[] underlying_, final boolean doCopy_)
        {
            if (null == underlying_)
            {
                throw new NullPointerException("Underlying cannot be null.");
            }

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

        @Override
        public DoubleVector collapse()
        {
            return this;
        }
    }

    private static final class FloatArrayVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x2d399701ea8edac8L;

        private final float[] _underlying;

        private FloatArrayVector(final float[] underlying_, final boolean doCopy_)
        {
            if (null == underlying_)
            {
                throw new NullPointerException("Underlying cannot be null.");
            }

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

        @Override
        public DoubleVector collapse()
        {
            return this;
        }
    }

    private static final class FunctionDoubleVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x5786e31501ee9960L;
        private final UnivariateFunction _function;
        private final DoubleVector _underlying;
        private transient DoubleVector _collapsed;

        public FunctionDoubleVector(final UnivariateFunction function_, final DoubleVector underlying_)
        {
            if (null == underlying_)
            {
                throw new NullPointerException("Underlying cannot be null.");
            }
            if (null == function_)
            {
                throw new NullPointerException("Function cannot be null.");
            }

            _function = function_;
            _underlying = underlying_;
            _collapsed = null;
        }

        @Override
        public double getEntry(int index_)
        {
            final double underlying = _underlying.getEntry(index_);
            return _function.value(underlying);
        }

        @Override
        public int getSize()
        {
            return _underlying.getSize();
        }

        @Override
        public DoubleVector collapse()
        {
            if (null == _collapsed)
            {
                final double[] raw = new double[getSize()];

                for (int i = 0; i < raw.length; i++)
                {
                    raw[i] = this.getEntry(i);
                }

                _collapsed = new DoubleArrayVector(raw, false);
            }

            return _collapsed;
        }

        private Object writeReplace() throws ObjectStreamException
        {
            if (_function instanceof Serializable)
            {
                return this;
            }
            else
            {
                // Collapse if necessary to preserve serializability.
                return this.collapse();
            }
        }

    }


    private static final class BivariateFunctionDoubleVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x77bb1fa9bd592e23L;
        private final BivariateFunction _function;
        private final DoubleVector _a;
        private final DoubleVector _b;
        private transient DoubleVector _collapsed;

        public BivariateFunctionDoubleVector(final BivariateFunction function_, final DoubleVector a_,
                                             final DoubleVector b_)
        {
            if (null == a_)
            {
                throw new NullPointerException("Underlying (A) cannot be null.");
            }
            if (null == b_)
            {
                throw new NullPointerException("Underlying (B) cannot be null.");
            }
            if (null == function_)
            {
                throw new NullPointerException("Function cannot be null.");
            }
            if (a_.getSize() != b_.getSize())
            {
                throw new IllegalArgumentException("Incompatible size: " + a_.getSize() + " != " + b_.getSize());
            }

            _function = function_;
            _a = a_;
            _b = b_;
            _collapsed = null;
        }

        @Override
        public double getEntry(int index_)
        {
            final double a = _a.getEntry(index_);
            final double b = _b.getEntry(index_);
            return _function.value(a, b);
        }

        @Override
        public int getSize()
        {
            return _a.getSize();
        }

        @Override
        public DoubleVector collapse()
        {
            if (null == _collapsed)
            {
                final double[] raw = new double[getSize()];

                for (int i = 0; i < raw.length; i++)
                {
                    raw[i] = this.getEntry(i);
                }

                _collapsed = new DoubleArrayVector(raw, false);
            }

            return _collapsed;
        }

        private Object writeReplace() throws ObjectStreamException
        {
            // Since this requres two underlying vectors, it's safer to collapse it down to 1 before serialization.
            return this.collapse();
        }

    }

    private static final class ConstantDoubleVector extends DoubleVector
    {
        private static final long serialVersionUID = 0x33d0ef04077b6898L;
        private final double _constant;
        private final int _size;

        private ConstantDoubleVector(final double constant_, final int size_)
        {
            if (size_ < 0)
            {
                throw new IllegalArgumentException("Size must be nonnegative.");
            }

            _constant = constant_;
            _size = size_;
        }

        @Override
        public double getEntry(int index_)
        {
            if (index_ < 0 || index_ >= _size)
            {
                throw new ArrayIndexOutOfBoundsException("Index out of bounds [0, " + _size + "): " + index_);
            }

            return _constant;
        }

        @Override
        public int getSize()
        {
            return _size;
        }

        @Override
        public DoubleVector collapse()
        {
            return this;
        }
    }
}

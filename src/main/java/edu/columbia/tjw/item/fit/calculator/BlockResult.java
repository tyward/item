package edu.columbia.tjw.item.fit.calculator;

import java.util.Collection;
import java.util.List;

public final class BlockResult
{
    private final int _rowStart;
    private final int _rowEnd;
    private final double _sumEntropy;
    private final double _sumEntropy2;
    private final double[] _derivative;
    private final int _size;

    public BlockResult(final int rowStart_, final int rowEnd_, final double sumEntropy_, final double sumEntropy2_,
                       final double[] derivative_)
    {
        if (rowStart_ < 0)
        {
            throw new IllegalArgumentException("Invalid start row.");
        }
        if (rowStart_ > rowEnd_)
        {
            throw new IllegalArgumentException("Size must be nonnegative.");
        }

        final int size = rowEnd_ - rowStart_;

        if (!(sumEntropy_ >= 0.0) || Double.isInfinite(sumEntropy_))
        {
            throw new IllegalArgumentException("Illegal entropy: " + sumEntropy_);
        }
        if (!(sumEntropy2_ >= 0.0) || Double.isInfinite(sumEntropy2_))
        {
            throw new IllegalArgumentException("Illegal entropy: " + sumEntropy2_);
        }


        _rowStart = rowStart_;
        _rowEnd = rowEnd_;
        _sumEntropy = sumEntropy_;
        _sumEntropy2 = sumEntropy2_;
        _size = size;
        _derivative = derivative_;
    }

    public BlockResult(final List<BlockResult> analysisList_)
    {
        if (analysisList_.size() < 1)
        {
            throw new IllegalArgumentException("List size must be positive.");
        }

        int minStart = Integer.MAX_VALUE;
        int maxEnd = Integer.MIN_VALUE;
        double h = 0.0;
        double h2 = 0.0;
        int count = 0;

        final double[] derivative;

        if (analysisList_.get(0).hasDerivative())
        {
            derivative = new double[analysisList_.get(0)._derivative.length];
        }
        else {
            derivative = null;
        }

        for (final BlockResult next : analysisList_)
        {
            minStart = Math.min(next._rowStart, minStart);
            maxEnd = Math.max(next._rowEnd, maxEnd);
            h += next._sumEntropy;
            h2 += next._sumEntropy2;
            count += next._size;

            if(null != derivative) {
                final double weight = next._size;

                for(int i = 0; i < derivative.length; i++) {
                    derivative[i] += weight * next.getDerivativeEntry(i);
                }
            }
        }

        if (count != (maxEnd - minStart))
        {
            throw new IllegalArgumentException("Discontiguous blocks.");
        }

        if(null != derivative) {
            final double invWeight = 1.0 / count;

            for(int i = 0; i < derivative.length; i++) {
                derivative[i] += invWeight * derivative[i];
            }
        }

        _rowStart = minStart;
        _rowEnd = maxEnd;
        _sumEntropy = h;
        _sumEntropy2 = h2;
        _size = count;
        _derivative = derivative;
    }

    public int getRowStart()
    {
        return _rowStart;
    }

    public int getRowEnd()
    {
        return _rowEnd;
    }

    public double getEntropySum()
    {
        return _sumEntropy;
    }

    public double getEntropySquareSum()
    {
        return _sumEntropy2;
    }

    public double getEntropyMean()
    {
        return _sumEntropy / _size;
    }

    public double getEntropySumVariance()
    {
        final double eX = getEntropyMean();
        final double eX2 = _sumEntropy2 / _size;
        final double var = Math.max(0.0, eX2 - (eX * eX));
        return var;
    }

    public double getEntropyMeanVariance()
    {
        return getEntropySumVariance() / _size;
    }

    public double getEntropyMeanDev()
    {
        return Math.sqrt(getEntropyMeanVariance());
    }

    public int getSize()
    {
        return _size;
    }

    public boolean hasDerivative()
    {
        return _derivative != null;
    }

    public int derivativeDimension()
    {
        if (!hasDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _derivative.length;
    }

    public double getDerivativeEntry(final int index_)
    {
        if (!hasDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _derivative[index_];
    }

//    @Override
//    public int compareTo(final BlockResult that_)
//    {
//        final int compare = this._rowStart - that_._rowStart;
//        return compare;
//    }
}

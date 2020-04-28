package edu.columbia.tjw.item.fit.calculator;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.List;

public final class BlockResult
{
    private final int _rowStart;
    private final int _rowEnd;
    private final double _sumEntropy;
    private final double _sumEntropy2;
    private final double[] _derivative;
    private final double[] _derivativeSquared;
    private final double[] _jDiag;
    private final double[] _shiftGradient;
    private final double[] _scaledGradient;
    private final double[] _scaledGradient2;
    private final double _gradientMass;
    private final double[][] _secondDerivative;
    private final double[][] _fisherInformation;
    private final int _size;

    public BlockResult(final int rowStart_, final int rowEnd_, final double sumEntropy_, final double sumEntropy2_,
                       final double[] derivative_, final double[] derivativeSquared_, final double[] jDiag_,
                       final double[] shiftGradient_, final double[] scaledGradient_,
                       final double[] scaledGradient2_, final double gradientMass_,
                       final double[][] fisherInformation_, final double[][] secondDerivative_)
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
        _jDiag = jDiag_;
        _shiftGradient = shiftGradient_;
        _derivativeSquared = derivativeSquared_;
        _secondDerivative = secondDerivative_;
        _fisherInformation = fisherInformation_;

        _gradientMass = gradientMass_;
        _scaledGradient = scaledGradient_;
        _scaledGradient2 = scaledGradient2_;
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
        double gradientMass = 0.0;
        int count = 0;

        final boolean hasSecondDerivative = analysisList_.get(0).hasSecondDerivative();
        final boolean hasDerivative = hasSecondDerivative || analysisList_.get(0).hasDerivative();

        if (hasDerivative)
        {
            final int dimension = analysisList_.get(0).getDerivativeDimension();
            _derivative = new double[dimension];
            _derivativeSquared = new double[dimension];
            _jDiag = new double[dimension];

            _scaledGradient = new double[dimension];
            _scaledGradient2 = new double[dimension];

            if (hasSecondDerivative)
            {
                _fisherInformation = new double[dimension][dimension];
                _secondDerivative = new double[dimension][dimension];
                _shiftGradient = new double[dimension];
            }
            else
            {
                _fisherInformation = null;
                _secondDerivative = null;
                _shiftGradient = null;
            }
        }
        else
        {
            _derivative = null;
            _scaledGradient = null;
            _scaledGradient2 = null;
            _derivativeSquared = null;
            _jDiag = null;

            _fisherInformation = null;
            _secondDerivative = null;
            _shiftGradient = null;
        }

        for (final BlockResult next : analysisList_)
        {
            minStart = Math.min(next._rowStart, minStart);
            maxEnd = Math.max(next._rowEnd, maxEnd);
            h += next._sumEntropy;
            h2 += next._sumEntropy2;
            count += next._size;

            if (null != _derivative)
            {
                final double weight = next._size;
                final int dimension = _derivative.length;
                gradientMass += next._gradientMass;

                for (int i = 0; i < dimension; i++)
                {
                    final double entry = next.getDerivativeEntry(i);
                    _derivative[i] += weight * entry;
                    _scaledGradient[i] += weight * next._scaledGradient[i];
                    _scaledGradient2[i] += weight * next._scaledGradient2[i];
                    _derivativeSquared[i] += weight * next._derivativeSquared[i];
                    _jDiag[i] += weight * next._jDiag[i];


                    if (null != _secondDerivative)
                    {
                        _shiftGradient[i] = weight * next.getShiftGradientEntry(i);

                        for (int j = 0; j < dimension; j++)
                        {
                            _secondDerivative[i][j] += weight * next.getSecondDerivativeEntry(i, j);
                            _fisherInformation[i][j] += weight * next.getFisherInformationEntry(i, j);
                        }
                    }
                }
            }
        }

        if (count != (maxEnd - minStart))
        {
            throw new IllegalArgumentException("Discontiguous blocks.");
        }

        if (null != _derivative)
        {
            final double invWeight = 1.0 / count;
            final int dimension = _derivative.length;

            for (int i = 0; i < dimension; i++)
            {
                _derivative[i] = invWeight * _derivative[i];
                _scaledGradient[i] = invWeight * _scaledGradient[i];
                _scaledGradient2[i] = invWeight * _scaledGradient2[i];
                _derivativeSquared[i] = invWeight * _derivativeSquared[i];
                _jDiag[i] = invWeight * _jDiag[i];

                if (null != _secondDerivative)
                {
                    _shiftGradient[i] *= invWeight;

                    for (int j = 0; j < dimension; j++)
                    {
                        _secondDerivative[i][j] = invWeight * _secondDerivative[i][j];
                        _fisherInformation[i][j] *= invWeight;
                    }
                }
            }
        }

        _rowStart = minStart;
        _rowEnd = maxEnd;
        _sumEntropy = h;
        _sumEntropy2 = h2;
        _size = count;
        _gradientMass = gradientMass;
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

    public boolean hasSecondDerivative()
    {
        return _secondDerivative != null;
    }

    public int getDerivativeDimension()
    {
        if (!hasDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _derivative.length;
    }

    public double getSecondDerivativeEntry(final int row_, final int column_)
    {
        if (!hasSecondDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _secondDerivative[row_][column_];
    }

    public double[] getScaledGradient()
    {
        return _scaledGradient;
    }

    public double[] getScaledGradient2()
    {
        return _scaledGradient2;
    }

    public double getFisherInformationEntry(final int row_, final int column_)
    {
        if (!hasDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _fisherInformation[row_][column_];
    }

    public double getDerivativeEntry(final int index_)
    {
        if (!hasDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _derivative[index_];
    }

    public double getD2Entry(final int index_)
    {
        return _derivativeSquared[index_];
    }

    public double getJDiagEntry(final int index_)
    {
        return _jDiag[index_];
    }

    public double[] getJDiag()
    {
        return _jDiag.clone();
    }


    public double[] getDerivative()
    {
        return _derivative.clone();
    }

    public RealMatrix getSecondDerivative()
    {
        return new Array2DRowRealMatrix(_secondDerivative);
    }

    public RealMatrix getFisherInformation()
    {
        return new Array2DRowRealMatrix(_fisherInformation);
    }

    public double getShiftGradientEntry(final int index_)
    {
        return _shiftGradient[index_];
    }

    public double getGradientMass()
    {
        return _gradientMass;
    }

//    @Override
//    public int compareTo(final BlockResult that_)
//    {
//        final int compare = this._rowStart - that_._rowStart;
//        return compare;
//    }
}

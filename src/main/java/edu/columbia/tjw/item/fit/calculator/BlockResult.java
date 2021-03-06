package edu.columbia.tjw.item.fit.calculator;

import edu.columbia.tjw.item.algo.DoubleMatrix;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.MatrixTools;
import edu.columbia.tjw.item.algo.VectorTools;

import java.util.List;

public final class BlockResult
{
    private final int _rowStart;
    private final int _rowEnd;
    private final double _sumEntropy;
    private final double _sumEntropy2;
    private final DoubleVector _derivative;
    private final DoubleVector _derivativeSquared;
    private final DoubleVector _jDiag;
    private final DoubleVector _shiftGradient;
    private final DoubleVector _scaledGradient;
    private final DoubleVector _scaledGradient2;
    private final double _gradientMass;
    private final DoubleMatrix _secondDerivative;
    private final DoubleMatrix _fisherInformation;
    private final int _size;

    public BlockResult(final int rowStart_, final int rowEnd_, final double sumEntropy_, final double sumEntropy2_,
                       final DoubleVector derivative_, final DoubleVector derivativeSquared_, final DoubleVector jDiag_,
                       final DoubleVector shiftGradient_, final DoubleVector scaledGradient_,
                       final DoubleVector scaledGradient2_, final double gradientMass_,
                       final DoubleMatrix fisherInformation_, final DoubleMatrix secondDerivative_)
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

        //final DoubleVector.Builder derivative;
        DoubleVector derivative = null;
        DoubleVector derivativeSquared = null;
        DoubleVector jDiag = null;
        DoubleVector scaledGradient = null;
        DoubleVector scaledGradient2 = null;
        DoubleVector shiftGradient = null;
        DoubleMatrix fisherInformation = null;
        DoubleMatrix secondDerivative = null;


        if (hasDerivative)
        {
            final int dimension = analysisList_.get(0).getDerivativeDimension();
            final DoubleVector zero = DoubleVector.constantVector(0, dimension);

            derivative = zero;
            derivativeSquared = zero;
            jDiag = zero;
            scaledGradient = zero;
            scaledGradient2 = zero;

            if (hasSecondDerivative)
            {
                final DoubleMatrix zeroMatrix = DoubleMatrix.constantMatrix(0.0, dimension, dimension);

                fisherInformation = zeroMatrix;
                secondDerivative = zeroMatrix;
                shiftGradient = zero;
            }
            else
            {
                fisherInformation = null;
                secondDerivative = null;
                shiftGradient = null;
            }
        }
        else
        {
            derivative = null;
            scaledGradient = null;
            scaledGradient2 = null;
            derivativeSquared = null;
            jDiag = null;

            fisherInformation = null;
            secondDerivative = null;
            shiftGradient = null;
        }

        for (final BlockResult next : analysisList_)
        {
            minStart = Math.min(next._rowStart, minStart);
            maxEnd = Math.max(next._rowEnd, maxEnd);
            h += next._sumEntropy;
            h2 += next._sumEntropy2;
            count += next._size;

            if (null != derivative)
            {
                final double weight = next._size;
                final int dimension = derivative.getSize();
                gradientMass += next._gradientMass;

                derivative = VectorTools.multiplyAccumulate(derivative, next._derivative, weight);
                derivativeSquared = VectorTools.multiplyAccumulate(derivativeSquared, next._derivativeSquared, weight);
                scaledGradient = VectorTools.multiplyAccumulate(scaledGradient, next._scaledGradient, weight);
                scaledGradient2 = VectorTools.multiplyAccumulate(scaledGradient2, next._scaledGradient2, weight);
                jDiag = VectorTools.multiplyAccumulate(jDiag, next._jDiag, weight);

                if (null != shiftGradient)
                {
                    shiftGradient = VectorTools.multiplyAccumulate(shiftGradient, next._shiftGradient, weight);
                }

                if (null != secondDerivative)
                {
                    secondDerivative = MatrixTools.multiplyAccumulate(secondDerivative, next.getSecondDerivative(),
                            weight);
                    fisherInformation = MatrixTools
                            .multiplyAccumulate(fisherInformation, next.getFisherInformation(), weight);
                }
            }
        }

        if (count != (maxEnd - minStart))
        {
            throw new IllegalArgumentException("Discontiguous blocks.");
        }

        if (null != derivative)
        {
            final double invWeight = 1.0 / count;
            final int dimension = derivative.getSize();
            derivative = VectorTools.scalarMultiply(derivative, invWeight).collapse();
            derivativeSquared = VectorTools.scalarMultiply(derivativeSquared, invWeight).collapse();

            scaledGradient = VectorTools.scalarMultiply(scaledGradient, invWeight).collapse();
            scaledGradient2 = VectorTools.scalarMultiply(scaledGradient2, invWeight).collapse();
            jDiag = VectorTools.scalarMultiply(jDiag, invWeight).collapse();

            if (null != shiftGradient)
            {
                shiftGradient = VectorTools.scalarMultiply(shiftGradient, invWeight).collapse();

                if (null != secondDerivative)
                {
                    secondDerivative = MatrixTools.scalarMultiply(secondDerivative, invWeight).collapse();
                    fisherInformation = MatrixTools.scalarMultiply(fisherInformation, invWeight).collapse();
                }
            }
        }

        _derivative = derivative;
        _derivativeSquared = derivativeSquared;
        _scaledGradient = scaledGradient;
        _scaledGradient2 = scaledGradient2;
        _jDiag = jDiag;

        _shiftGradient = shiftGradient;

        _secondDerivative = secondDerivative;
        _fisherInformation = fisherInformation;

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

        return _derivative.getSize();
    }

    public DoubleVector getScaledGradient()
    {
        return _scaledGradient;
    }

    public DoubleVector getScaledGradient2()
    {
        return _scaledGradient2;
    }

    public double getDerivativeEntry(final int index_)
    {
        if (!hasDerivative())
        {
            throw new IllegalArgumentException("Derivative was not calculated.");
        }

        return _derivative.getEntry(index_);
    }

    public DoubleVector getDerivativeSquared()
    {
        return _derivativeSquared;
    }

    public double getD2Entry(final int index_)
    {
        return _derivativeSquared.getEntry(index_);
    }

    public double getJDiagEntry(final int index_)
    {
        return _jDiag.getEntry(index_);
    }

    public DoubleVector getJDiag()
    {
        return _jDiag;
    }


    public DoubleVector getDerivative()
    {
        return _derivative;
    }

    public DoubleMatrix getSecondDerivative()
    {
        return _secondDerivative;
    }

    public DoubleMatrix getFisherInformation()
    {
        return _fisherInformation;
    }

    public DoubleVector getShiftGradient()
    {
        return _shiftGradient;
    }

    public double getShiftGradientEntry(final int index_)
    {
        return _shiftGradient.getEntry(index_);
    }

    public double getGradientMass()
    {
        return _gradientMass;
    }
}

package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import edu.columbia.tjw.item.util.IceTools;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.io.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class FitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements Serializable
{
    private static final double EPSILON = Math.ulp(4.0); // Just a bit bigger than machine epsilon.
    private static final long serialVersionUID = 0x606e4b6c2343db26L;

    private final FitResult<S, R, T> _prev;
    private final ItemParameters<S, R, T> _params;

    private final double _entropy;
    private final double _entropyStdDev;
    private final double _aic;
    private final double _tic;
    private final double _ice;

    // These are really only here for debugging.
    private final double[] _gradient;
    private final double[] _paramStdDev;
    private final PackedParameters<S, R, T> _packed;

    private final double _ice2;
    private final double _iceSum;
    private final double _iceSum2;
    private final double _iceSum3;
    private final double _ticSum;
    private final double _invConditionNumber;

    private final double _invConditionNumberJ;
    private final double _invConditionNumberI;


    /**
     * Make a new fit result based on current_, but with a new prev_.
     * <p>
     * This is useful for squashing a bunch of garbage together into a single result.
     *
     * @param current_
     * @param prev_
     */
    public FitResult(final FitResult<S, R, T> current_, final FitResult<S, R, T> prev_)
    {
        this._prev = prev_;
        _params = current_.getParams();
        _entropy = current_.getEntropy();
        _entropyStdDev = current_.getEntropyStdDev();
        _aic = current_.getInformationCriterion();
        _tic = current_.getTic();
        _ice = current_._ice;
        _gradient = current_._gradient;
        _paramStdDev = current_._paramStdDev;
        _packed = current_._packed;

        _iceSum = current_._iceSum;
        _ticSum = current_._ticSum;

        _ice2 = current_._ice2;
        _iceSum2 = current_._iceSum2;
        _iceSum3 = current_._iceSum3;
        _invConditionNumber = current_._invConditionNumber;

        _invConditionNumberI = current_._invConditionNumberI;
        _invConditionNumberJ = current_._invConditionNumberJ;
    }

    public FitResult(final ItemFitPoint<S, R, T> fitPoint_, final FitResult<S, R, T> prev_,
                     final boolean complexFitResults_)
    {
        _params = fitPoint_.getParams();
        _packed = _params.generatePacked();
        _prev = prev_;

        final int rowCount = fitPoint_.getSize();

        if (complexFitResults_)
        {
            fitPoint_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
            final BlockResult secondDerivative = fitPoint_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
            _entropy = secondDerivative.getEntropyMean();
            _entropyStdDev = secondDerivative.getEntropyMeanDev();
            _gradient = secondDerivative.getDerivative();
            final int dimension = _gradient.length;

            final RealMatrix jMatrix = secondDerivative.getSecondDerivative();
            final SingularValueDecomposition jSvd = new SingularValueDecomposition(jMatrix);

            final RealMatrix iMatrix = secondDerivative.getFisherInformation();
            final SingularValueDecomposition iSvd = new SingularValueDecomposition(iMatrix);

            _invConditionNumberJ = jSvd.getInverseConditionNumber();
            _invConditionNumberI = iSvd.getInverseConditionNumber();

            final double minInverseCondition = Math
                    .min(_invConditionNumberJ, _invConditionNumberI);

            _invConditionNumber = minInverseCondition;

            final RealMatrix jInverse = jSvd.getSolver().getInverse();
            final RealMatrix iInverse = iSvd.getSolver().getInverse();

            final double inverseSqrtN = 1.0 / Math.sqrt(rowCount);
            _paramStdDev = new double[dimension];

            for (int i = 0; i < dimension; i++)
            {
                _paramStdDev[i] = inverseSqrtN * Math.sqrt(iInverse.getEntry(i, i));
            }

            final RealMatrix ticMatrix = jInverse.multiply(iMatrix);

            double ticSum = 0.0;

            for (int i = 0; i < dimension; i++)
            {
                final double ticTerm = ticMatrix.getEntry(i, i);
                ticSum += ticTerm;
            }

            _tic = 2.0 * ((_entropy * rowCount) + ticSum);
            _ticSum = ticSum;

            final double iceSum = IceTools.computeIceSum(secondDerivative);
            final double iceSum2 = IceTools.computeIce2Sum(secondDerivative);

            _ice = 2.0 * ((_entropy * rowCount) + iceSum);
            _iceSum = iceSum;

            _iceSum2 = iceSum2;
            _ice2 = 2.0 * ((_entropy * rowCount) + iceSum2);

            _iceSum3 = IceTools.computeIce3Sum(secondDerivative);

        }
        else
        {
            fitPoint_.computeAll(BlockCalculationType.VALUE);
            _entropy = fitPoint_.getAggregated(BlockCalculationType.VALUE).getEntropyMean();
            _entropyStdDev = fitPoint_.getAggregated(BlockCalculationType.VALUE).getEntropyMeanDev();
            _gradient = null;
            _paramStdDev = null;
            _tic = Double.NaN;
            _ice = Double.NaN;

            _ticSum = Double.NaN;
            _iceSum = Double.NaN;

            _iceSum2 = Double.NaN;
            _iceSum3 = Double.NaN;
            _ice2 = Double.NaN;

            _invConditionNumber = Double.NaN;
            _invConditionNumberJ = Double.NaN;
            _invConditionNumberI = Double.NaN;
        }

        _aic = computeAic(_entropy, rowCount, _params.getEffectiveParamCount());
    }

    public static double computeAic(final double entropy_, final int rowCount_, final int paramCount_)
    {
        return 2.0 * ((entropy_ * rowCount_) + paramCount_);
    }

    public FitResult<S, R, T> getPrev()
    {
        return _prev;
    }

    public ItemParameters<S, R, T> getParams()
    {
        return _params;
    }

    public double getEntropy()
    {
        return _entropy;
    }

    public double getEntropyStdDev()
    {
        return _entropyStdDev;
    }

    public double getAic()
    {
        return _aic;
    }

    public double getTic()
    {
        return _tic;
    }

    public double getIce()
    {
        return _ice;
    }

    public double[] getParamStdDev()
    {
        return _paramStdDev.clone();
    }

    public double[] getGradient()
    {
        return _gradient.clone();
    }

    public double getTicSum()
    {
        return _ticSum;
    }

    public double getIceSum()
    {
        return _iceSum;
    }

    public double getIce2Sum()
    {
        return _iceSum2;
    }

    public double getIce3Sum()
    {
        return _iceSum3;
    }

    public double getInvConditionNumber()
    {
        return _invConditionNumber;
    }

    public double getInvConditionNumberJ()
    {
        return _invConditionNumberJ;
    }


    public double getInvConditionNumberI()
    {
        return _invConditionNumberI;
    }

    public double getInformationCriterion()
    {
        // For now, this is AIC, but it could be TIC later on.
        return getAic();
    }

    public double getInformationCriterionDiff()
    {
        if (null == _prev)
        {
            return Double.NaN;
            //return getInformationCriterion();
        }

        return getInformationCriterion() - getPrev().getInformationCriterion();
    }

    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("FitResult[" + Integer.toHexString(System.identityHashCode(this)) + "][\n");
        builder.append("Params: ");
        builder.append(_params.toString());
        builder.append("\nPrev Hash: " + System.identityHashCode(_prev));
        builder.append("\nEntropy: " + _entropy);
        builder.append("\nAIC: " + _aic);
        builder.append("\nTIC: " + _tic);
        builder.append("\nICE: " + _ice);
        builder.append("\nICE2: " + _ice2);

        builder.append("\nTIC Adjustment: " + _ticSum);
        builder.append("\nICE Adjustment: " + _iceSum);
        builder.append("\nICE2 Adjustment: " + _iceSum2);
        builder.append("\nICE3 Adjustment: " + _iceSum3);

        builder.append("\nInv Condition Number: " + _invConditionNumber);

        builder.append("\nGradient: " + Arrays.toString(_gradient));
        builder.append("\nParamStdDev: " + Arrays.toString(_paramStdDev));
        builder.append("\n]");

        return builder.toString();
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


    public static <S2 extends ItemStatus<S2>, R2 extends ItemRegressor<R2>, T2 extends ItemCurveType<T2>>
    FitResult<S2, R2, T2> readFromStream(final InputStream stream_,
                                         final Class<S2> statusClass_, final Class<R2> regClass_,
                                         final Class<T2> typeClass_)
            throws IOException
    {
        try (final GZIPInputStream zipin = new GZIPInputStream(stream_);
             final ObjectInputStream oIn = new ObjectInputStream(zipin))
        {
            final FitResult<?, ?, ?> raw = (FitResult<?, ?, ?>) oIn.readObject();

            if (raw.getParams().getStatus().getClass() != statusClass_)
            {
                throw new IOException("Status class mismatch.");
            }
            if (raw.getParams().getRegressorFamily().getComponentType() != regClass_)
            {
                throw new IOException("Regressor class mismatch.");
            }
            if (raw.getParams().getCurveFamily().getComponentType() != typeClass_)
            {
                throw new IOException("Curve Type class mismatch.");
            }

            final FitResult<S2, R2, T2> typed = (FitResult<S2, R2, T2>) raw;
            return typed;
        }
        catch (ClassNotFoundException e)
        {
            throw new IOException(e);
        }
    }

}

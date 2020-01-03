package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.fit.calculator.BlockCalculationType;
import edu.columbia.tjw.item.fit.calculator.BlockResult;
import edu.columbia.tjw.item.fit.calculator.ItemFitPoint;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.io.Serializable;

public final class FitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements Serializable
{
    private static final boolean USE_COMPLEX_RESULTS = false;
    private static final long serialVersionUID = 0x606e4b6c2343db26L;

    private final FitResult<S, R, T> _prev;
    private final ItemParameters<S, R, T> _params;

    private final double _entropy;
    private final double _entropyStdDev;
    private final double _aic;
    private final double _tic;

    private final double[] _gradient;
    private final double[] _paramStdDev;

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
        _gradient = current_._gradient;
        _paramStdDev = current_._paramStdDev;
    }

    protected FitResult(final ItemFitPoint<S, R, T> fitPoint_, final FitResult<S, R, T> prev_)
    {
        _params = fitPoint_.getParams();
        _prev = prev_;

        final int rowCount = fitPoint_.getSize();

        if (USE_COMPLEX_RESULTS)
        {
            fitPoint_.computeAll(BlockCalculationType.SECOND_DERIVATIVE);
            final BlockResult secondDerivative = fitPoint_.getAggregated(BlockCalculationType.SECOND_DERIVATIVE);
            _entropy = secondDerivative.getEntropyMean();
            _entropyStdDev = secondDerivative.getEntropyMeanDev();
            _gradient = secondDerivative.getDerivative();

            final RealMatrix jMatrix = secondDerivative.getSecondDerivative();
            final SingularValueDecomposition jSvd = new SingularValueDecomposition(jMatrix);

            final RealMatrix iMatrix = secondDerivative.getFisherInformation();
            final SingularValueDecomposition iSvd = new SingularValueDecomposition(iMatrix);

            final double minInverseCondition = Math
                    .min(jSvd.getInverseConditionNumber(), iSvd.getInverseConditionNumber());

            if (minInverseCondition < 1.0e-16)
            {
                // TIC cannot be computed accurately for these parameters, just leave it undefined.
                _tic = Double.NaN;
                _paramStdDev = null;
            }
            else
            {
                final RealMatrix jInverse = jSvd.getSolver().getInverse();
                final RealMatrix iInverse = iSvd.getSolver().getInverse();

                final double inverseSqrtN = 1.0 / Math.sqrt(rowCount);
                _paramStdDev = new double[_gradient.length];

                for (int i = 0; i < _paramStdDev.length; i++)
                {
                    _paramStdDev[i] = inverseSqrtN * Math.sqrt(iInverse.getEntry(i, i));
                }

                final RealMatrix ticMatrix = jInverse.multiply(iMatrix);

                double ticSum = 0.0;

                for (int i = 0; i < ticMatrix.getRowDimension(); i++)
                {
                    ticSum += ticMatrix.getEntry(i, i);
                }

                _tic = 2.0 * ((_entropy * rowCount) + ticSum);
            }
        }
        else
        {
            fitPoint_.computeAll(BlockCalculationType.VALUE);
            _entropy = fitPoint_.getAggregated(BlockCalculationType.VALUE).getEntropyMean();
            _entropyStdDev = fitPoint_.getAggregated(BlockCalculationType.VALUE).getEntropyMeanDev();
            _gradient = null;
            _paramStdDev = null;
            _tic = Double.NaN;
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

    public double getInformationCriterion()
    {
        // For now, this is AIC, but it could be TIC later on.
        return getAic();
    }

    public double getInformationCriterionDiff()
    {
        if (null == _prev)
        {
            return getInformationCriterion();
        }

        return getInformationCriterion() - getPrev().getInformationCriterion();
    }

    public double getTic()
    {
        return _tic;
    }
}

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
    private static final boolean USE_COMPLEX_RESULTS = true;
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
    private final double _ticSum;

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
    }

    public FitResult(final ItemFitPoint<S, R, T> fitPoint_, final FitResult<S, R, T> prev_)
    {
        _params = fitPoint_.getParams();
        _packed = _params.generatePacked();
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

            // This is basically the worst an entropy could ever be with a uniform model. It is a reasonable level of
            // "an entropy bad enough that any realistic model should avoid it like the plague, but not so bad that
            // it causes any sort of numerical issues", telling the model that J must be pos. def.
            final double logM = Math.log(_params.getReachableSize());
            //final double iceBalance = 1.0 / (logM + _params.getEffectiveParamCount());
            final double iceBalance = 1.0 / logM;

            if (minInverseCondition < 1.0e-200)
            {
                // TIC cannot be computed accurately for these parameters, just leave it undefined.
                _tic = Double.NaN;
                _ice = Double.NaN;
                _paramStdDev = null;

                _ticSum = Double.NaN;
                _iceSum = Double.NaN;

                _iceSum2 = Double.NaN;
                _ice2 = Double.NaN;
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
                    final double ticTerm = ticMatrix.getEntry(i, i);
                    ticSum += ticTerm;
                }

                _tic = 2.0 * ((_entropy * rowCount) + ticSum);
                _ticSum = ticSum;

                double iceSum = 0.0;
                double iceSum2 = 0.0;

                for (int i = 0; i < ticMatrix.getRowDimension(); i++)
                {
                    final double iTerm = iMatrix.getEntry(i, i); // Already squared, this one is.
                    final double jTerm = jMatrix.getEntry(i, i);
                    final double iceTerm = iTerm / jTerm;

                    final double iceTerm2 = iTerm / (Math.abs(jTerm) * (1.0 - iceBalance) + iTerm * iceBalance);

                    iceSum += iceTerm;
                    iceSum2 += iceTerm2;
                }

                _ice = 2.0 * ((_entropy * rowCount) + iceSum);
                _iceSum = iceSum;

                _iceSum2 = iceSum2;
                _ice2 = 2.0 * ((_entropy * rowCount) + iceSum2);
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
            _ice = Double.NaN;

            _ticSum = Double.NaN;
            _iceSum = Double.NaN;

            _iceSum2 = Double.NaN;
            _ice2 = Double.NaN;
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

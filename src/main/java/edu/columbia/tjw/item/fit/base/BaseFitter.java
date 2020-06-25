package edu.columbia.tjw.item.fit.base;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.fit.EntropyCalculator;
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.optimize.ConvergenceException;
import edu.columbia.tjw.item.optimize.MultivariateOptimizer;
import edu.columbia.tjw.item.optimize.MultivariatePoint;
import edu.columbia.tjw.item.optimize.OptimizationResult;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.logging.Logger;

public final class BaseFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(BaseFitter.class);

    private final MultivariateOptimizer _optimizer;
    private final ItemSettings _settings;
    private final EntropyCalculator<S, R, T> _calc;

    public BaseFitter(final EntropyCalculator<S, R, T> calc_, final ItemSettings settings_)
    {
        _calc = calc_;
        _optimizer = new MultivariateOptimizer(settings_.getBlockSize(), 1000, 50, 0.1, settings_.getTarget(),
                settings_);
        _settings = settings_;
    }

    public FitResult<S, R, T> doFit(final PackedParameters<S, R, T> packed_, final FitResult<S, R, T> prev_)
    {
        return doFit(packed_, prev_, true);
    }

    public FitResult<S, R, T> doFit(final PackedParameters<S, R, T> packed_, final FitResult<S, R, T> prev_,
                                    final boolean skipWorse_)
    {
        try
        {
            // The entropy of the starting point.
            //final double entropy = prev_.getEntropy();
            final BaseModelFunction<S, R, T> function = generateFunction(packed_);
            final DoubleVector beta = function.getBeta();
            final MultivariatePoint point = new MultivariatePoint(beta);
            final OptimizationResult<MultivariatePoint> result = _optimizer.optimize(function, point);

            if (!result.converged())
            {
                LOG.info("Exhausted dataset before convergence, moving on.");
            }

            //final double newEntropy = result.minValue();
            final double prevAic = prev_.getInformationCriterion();
            final double newAic = FitResult.computeAic(result.minEntropy(), _calc.getGrid().size(),
                    packed_.getOriginalParams().getEffectiveParamCount());

            if (skipWorse_ && newAic >= (prevAic + 5.0))
            {
                // We will not even consider this result unless it was at least slightly better (or not much worse)
                // than the original.
                // N.B: Since the optimizer didn't necessarily evaluate all observations, this is not guarantee that
                // the point is actually better, we will actually recompute to be sure later on, but don't even spend
                // the time if there's no real chance here.

                // A vacuous result, not changed from the underlying.
                return new FitResult<>(prev_, prev_);
            }

            final ItemParameters<S, R, T> params = function.generateParams(result.getOptimum().getElements());
            return _calc.computeFitResult(params, prev_);
        }
        catch (final ConvergenceException e)
        {
            // A vacuous result, not changed from the underlying.
            return new FitResult<>(prev_, prev_);
        }
    }

    public EntropyCalculator<S, R, T> getCalc()
    {
        return _calc;
    }

    private BaseModelFunction<S, R, T> generateFunction(final PackedParameters<S, R, T> packed_)
    {
        final BaseModelFunction<S, R, T> function = new BaseModelFunction<>(_calc.getGrid(),
                _settings, packed_);
        return function;
    }
}

package edu.columbia.tjw.item.fit.base;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.EntropyCalculator;
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.fit.PackedParameters;
import edu.columbia.tjw.item.fit.ReducedParameterVector;
import edu.columbia.tjw.item.fit.curve.CurveFitResult;
import edu.columbia.tjw.item.fit.curve.CurveFitter;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

public final class ModelFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ModelFitter.class);

    private final ItemSettings _settings;
    private final ItemParameters<S, R, T> _starting;

    private final BaseFitter<S, R, T> _base;
    private final ParamFitter<S, R, T> _fitter;
    private final CurveFitter<S, R, T> _curveFitter;


    public ModelFitter(final ItemParameters<S, R, T> starting_,
                       final ItemFittingGrid<S, R> grid_, ItemSettings settings_)
    {
        _settings = settings_;
        _starting = starting_;

        final EntropyCalculator<S, R, T> calc = new EntropyCalculator<>(grid_);
        _base = new BaseFitter<>(calc, _settings);
        _fitter = new ParamFitter<>(_base);
        _curveFitter = new CurveFitter<>(_settings, _base);
    }

    public FitResult<S, R, T> generateInitialModel()
    {
        final FitResult<S, R, T> fitResult = getCalculator().computeFitResult(_starting, null);
        final FitResult<S, R, T> calibrated = fitAllParameters(fitResult);
        return calibrated;
    }

    public FitResult<S, R, T> fitAllParameters(final FitResult<S, R, T> prevResults_)
    {
        final FitResult<S, R, T> refit = _base.doFit(prevResults_.getParams().generatePacked(), prevResults_, false);
        return refit;
    }

    public FitResult<S, R, T> fitEntries(final FitResult<S, R, T> prevResults_, final int[] entries_)
    {
        final PackedParameters<S, R, T> packed = prevResults_.getParams().generatePacked();
        final boolean[] active = new boolean[prevResults_.getParams().getEffectiveParamCount()];

        Arrays.sort(entries_);
        int activeCount = 0;

        for (int i = 0; i < active.length; i++)
        {
            if (Arrays.binarySearch(entries_, packed.getEntry(i)) < 0)
            {
                // Not one of the target entries.
                continue;
            }

            active[i] = true;
            activeCount++;
        }

        if (activeCount <= 0)
        {
            throw new IllegalArgumentException("Invalid entry list.");
        }

        final PackedParameters<S, R, T> reduced = new ReducedParameterVector<>(active, packed);
        return _base.doFit(reduced, prevResults_);
    }


    public FitResult<S, R, T> fitBetas(final FitResult<S, R, T> fitResult_)
    {
        return _fitter.fitBetas(fitResult_.getParams(), fitResult_);
    }

    public FitResult<S, R, T> addDirectRegressors(final FitResult<S, R, T> fitResult_,
                                                  final Collection<R> coefficients_)
    {
        ItemParameters<S, R, T> params = fitResult_.getParams();
        final SortedSet<R> flagSet = params.getFlagSet();
        final int startingSize = params.getEntryCount();

        for (final R field : coefficients_)
        {
            if (flagSet.contains(field))
            {
                continue;
            }

            params = params.addBeta(field);
        }

        if (params.getEntryCount() == startingSize)
        {
            // We didn't actually add anything.
            return new FitResult<>(fitResult_, fitResult_);
        }

        final FitResult<S, R, T> output = _fitter.fitBetas(params, fitResult_);
        return output;
    }

    public FitResult<S, R, T> trim(final FitResult<S, R, T> fitResult_)
    {
        FitResult<S, R, T> current = fitResult_;

        DoubleVector gradient = null;

        for (int i = 0; i < current.getParams().getEntryCount(); i++)
        {
            final ItemParameters<S, R, T> base = current.getParams();
            final PackedParameters<S, R, T> basePacked = base.generatePacked();

            if (i == base.getInterceptIndex())
            {
                continue;
            }

            final ItemParameters<S, R, T> reduced = base.dropIndex(i);
            final S statusRestrict = base.getEntryStatusRestrict(i);
            final PackedParameters<S, R, T> packed = reduced.generatePacked();

            if (null != statusRestrict)
            {
                final int statusIndex = base.getStatus().getReachable().indexOf(statusRestrict);
                final double dropBeta = base.getBeta(statusIndex, i);

                if (Math.abs(dropBeta) > 8.0)
                {
                    if (null == gradient)
                    {
                        gradient = this.getCalculator().computeGradients(basePacked).getGradient();
                    }

                    final int betaIndex = basePacked.findBetaIndex(statusIndex, i);
                    final int interceptIndex = basePacked.findBetaIndex(statusIndex, base.getInterceptIndex());
                    final double gradBeta = gradient.getEntry(betaIndex);
                    final double gradIntercept = gradient.getEntry(interceptIndex);

                    // We think that beta * gradBeta / gradIntercept  is an adjustment to bring the intercept into
                    // alignment with the zero beta case.
                    final double interceptAdjustment = -dropBeta * gradBeta / gradIntercept;

                    final int targetIndex = packed.findBetaIndex(statusIndex, base.getInterceptIndex());
                    final double existing = packed.getParameter(targetIndex);
                    final double updated = existing - interceptAdjustment;
                    packed.setParameter(targetIndex, updated);

                    // This should start us off with something that won't overflow, and we can optimize from there.
                }


            }
            // Try to recalibrate everything, don't short-circuit if we see worse results.
            final FitResult<S, R, T> refit = _base.doFit(packed, current, false);
            final double aicDiff = refit.getInformationCriterionDiff();

            // Check to see if the reduced model is good enough that we would not be able to add this entry (i.e.
            // adding the entry would have failed the cutoff).
            if (aicDiff < -_settings.getAicCutoff())
            {
                LOG.info("Trimming an entry[" + i + "]");
                current = refit;

                //Just step back, this entry has been removed, other entries slid up.
                i--;
            }
        }

        return current;
    }

    public FitResult<S, R, T> expandModel(final FitResult<S, R, T> fitResult_, final Set<R> curveFields_,
                                          final int maxParamCount_)
    {
        final int newParams = maxParamCount_ - fitResult_.getParams().getEffectiveParamCount();
        FitResult<S, R, T> best = fitResult_;

        //As a bare minimum, each expansion will consume at least one param, we'll break out before this most likely.
        for (int i = 0; i < newParams; i++)
        {
            // N.B: Do we fit all parameters, or just coefficients?
            final FitResult<S, R, T> recalibrated = this.fitAllParameters(best);

            if (recalibrated.getInformationCriterion() < best.getInformationCriterion())
            {
                // First step, just calibrate all the parameters if we cann.
                best = recalibrated;
            }

            // Now do the actual expansion....
            // N.B: It is MUCH worse to try to do these in batches. We always need to use the greedy algorithm and
            // search for new curves each time.
            final CurveFitResult<S, R, T> curveFit = _curveFitter.findBest(curveFields_, best);

            if (null == curveFit)
            {
                break;
            }
            if (curveFit.getFitResult().getInformationCriterionDiff() >= _settings.getAicCutoff())
            {
                // Curve wasn't really better.
                break;
            }

            if (curveFit.getFitResult().getEntropy() >= best.getEntropy())
            {
                // This wasn't actually better, just misidentified.
                break;
            }

            best = curveFit.getFitResult();

            // TODO: Consider interactions here?

        }

        if (best == fitResult_)
        {
            // We couldn't improve this at all, just return a result showing that this is the cases.
            return new FitResult<>(fitResult_, fitResult_);
        }

        return best;
    }


    public EntropyCalculator<S, R, T> getCalculator()
    {
        return _base.getCalc();
    }

    public ParamFitter<S, R, T> getParamFitter()
    {
        return _fitter;
    }

    public CurveFitter<S, R, T> getCurveFitter()
    {
        return _curveFitter;
    }

}

package edu.columbia.tjw.item.fit.base;

import edu.columbia.tjw.item.*;
import edu.columbia.tjw.item.data.ItemFittingGrid;
import edu.columbia.tjw.item.fit.EntropyCalculator;
import edu.columbia.tjw.item.fit.FitResult;
import edu.columbia.tjw.item.fit.curve.CurveFitter;
import edu.columbia.tjw.item.fit.param.ParamFitter;
import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.LogUtil;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

public final class ModelFitter<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private static final Logger LOG = LogUtil.getLogger(ModelFitter.class);

    private final ItemSettings _settings;
    private final R _intercept;
    private final S _status;

    private final BaseFitter<S, R, T> _base;
    private final ParamFitter<S, R, T> _fitter;
    private final CurveFitter<S, R, T> _curveFitter;

    private final EnumFamily<T> _curveFamily;

    public ModelFitter(final EnumFamily<T> curveFamily_, final R intercept_, final S status_,
                       final ItemFittingGrid<S, R> grid_, ItemSettings settings_)
    {
        _settings = settings_;
        _intercept = intercept_;
        _status = status_;
        _curveFamily = curveFamily_;

        final EntropyCalculator<S, R, T> calc = new EntropyCalculator<>(grid_);
        _base = new BaseFitter<>(calc, _settings);
        _fitter = new ParamFitter<>(_base);
        _curveFitter = new CurveFitter<>(_settings, _base);
    }

    public FitResult<S, R, T> generateInitialModel()
    {
        final ItemParameters<S, R, T> starting = new ItemParameters<>(_status, _intercept,
                _curveFamily);

        final FitResult<S, R, T> fitResult = getCalculator().computeFitResult(starting, null);
        final FitResult<S, R, T> calibrated = fitAllParameters(fitResult);
        return calibrated;
    }

    public FitResult<S, R, T> fitAllParameters(final FitResult<S, R, T> prevResults_)
    {
        final FitResult<S, R, T> refit = _base.doFit(prevResults_.getParams().generatePacked(), prevResults_);
        return refit;
    }

    public FitResult<S, R, T> fitBetas(final FitResult<S, R, T> fitResult_)
    {
        return _fitter.fitBetas(fitResult_.getParams(), fitResult_);
    }

    public FitResult<S, R, T> addDirectRegressors(final FitResult<S, R, T> fitResult_,
                                                  final Collection<R> coefficients_)
    {
        ItemParameters<S, R, T> params = fitResult_.getParams();
        final SortedSet<R> flagSet = new TreeSet<>();

        for (int i = 0; i < params.getEntryCount(); i++)
        {
            if (params.getEntryDepth(i) != 1)
            {
                continue;
            }
            if (params.getEntryCurve(i, 0) != null)
            {
                continue;
            }

            flagSet.add(params.getEntryRegressor(i, 0));
        }

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

        for (int i = 0; i < current.getParams().getEntryCount(); i++)
        {
            final ItemParameters<S, R, T> base = current.getParams();

            if (i == base.getInterceptIndex())
            {
                continue;
            }

            final ItemParameters<S, R, T> reduced = base.dropIndex(i);

            // Try to recalibrate everything, don't short-circuit if we see worse results.
            final FitResult<S, R, T> refit = _base.doFit(reduced.generatePacked(), current, false);
            final double aicDiff = refit.getAicDiff();

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


    public EntropyCalculator<S, R, T> getCalculator()
    {
        return _base.getCalc();
    }

}
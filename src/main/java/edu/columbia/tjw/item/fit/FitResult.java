package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;

import java.io.Serializable;

public final class FitResult<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
        implements Serializable
{
    private static final long serialVersionUID = 0x606e4b6c2343db26L;

    private final FitResult<S, R, T> _prev;
    private final ItemParameters<S, R, T> _params;

    private final double _entropy;
    private final double _aic;
    private final double _tic;

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
        _aic = current_.getAic();
        _tic = current_.getTic();
    }

    public FitResult(final ItemParameters<S, R, T> params_,
                     final double entropy_, final long rowCount_, final FitResult<S, R, T> prev_)
    {
        if (params_ == null)
        {
            throw new NullPointerException("Params cannot be null.");
        }
        if (Double.isNaN(entropy_) || Double.isInfinite(entropy_) || entropy_ < 0.0)
        {
            throw new IllegalArgumentException("Log likelihood must be well defined.");
        }
        if (rowCount_ < 1)
        {
            throw new IllegalArgumentException("Row count must be positive: " + rowCount_);
        }

        _prev = prev_;
        _params = params_;
        _entropy = entropy_;

        _aic = 2.0 * ((entropy_ * rowCount_) + _params.getEffectiveParamCount());

        _tic = Double.NaN;
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

    public double getAic()
    {
        return _aic;
    }

    public double getAicDiff()
    {
        if (null == _prev)
        {
            return getAic();
        }

        return getAic() - getPrev().getAic();
    }

    public double getTic()
    {
        return _tic;
    }
}

package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.*;

import java.util.List;

public final class ItemParametersVector<S extends ItemStatus<S>, R extends ItemRegressor<R>, T extends ItemCurveType<T>>
{
    private final ItemParameters<S, R, T> _base;
    private final double[] _paramValues;
    private final int[] _toStatus;
    private final int[] _entryIndex;
    private final int[] _curveDepth;
    private final int[] _curveIndex;

    public ItemParametersVector(final ItemParameters<S, R, T> params_)
    {
        _base = params_;
        final int paramCount = params_.getEffectiveParamCount();

        _paramValues = new double[paramCount];
        _toStatus = new int[paramCount];
        _entryIndex = new int[paramCount];
        _curveDepth = new int[paramCount];
        _curveIndex = new int[paramCount];

        int pointer = 0;

        final S fromStatus = _base.getStatus();
        final List<S> reachable = fromStatus.getReachable();

        for (int i = 0; i < _base.getEntryCount(); i++)
        {
            final S statusRestrict = _base.getEntryStatusRestrict(i);

            if (statusRestrict == null)
            {
                for (final S next : reachable)
                {
                    if (next == fromStatus)
                    {
                        continue;
                    }

                    pointer = fillBeta(next, i, pointer);
                }
            }
            else
            {
                pointer = fillBeta(statusRestrict, i, pointer);
            }

            for (int z = 0; z < _base.getEntryDepth(i); z++)
            {
                final ItemCurve<T> curve = _base.getEntryCurve(i, z);

                if (null == curve)
                {
                    continue;
                }

                final int curveParamCount = curve.getCurveType().getParamCount();

                for(int w = 0; w < curveParamCount; w++) {
                    final double curveParam = curve.getParam(w);
                    pointer = fillOne(curveParam, -1, i, z, w, pointer);
                }
            }
        }

        if(pointer != paramCount) {
            throw new IllegalArgumentException("Impossible: " + pointer + " != " + paramCount);
        }
    }

    private int fillBeta(final S toStatus_, final int entryIndex_, final int pointer_)
    {
        final int toIndex = _base.getToIndex(toStatus_);
        return fillOne(_base.getBeta(toIndex, entryIndex_), toIndex, entryIndex_, -1, -1, pointer_);
    }

    private int fillOne(final double val_, final int toStatus_, final int entryIndex_, final int curveDepth_, final int curveIndex_, final int pointer_)
    {
        _paramValues[pointer_] = val_;
        _toStatus[pointer_] = toStatus_;
        _entryIndex[pointer_] = entryIndex_;
        _curveDepth[pointer_] = curveDepth_;
        _curveIndex[pointer_] = curveIndex_;
        return pointer_ + 1;
    }

}

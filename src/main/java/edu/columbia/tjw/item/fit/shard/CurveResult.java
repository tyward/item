package edu.columbia.tjw.item.fit.shard;

import edu.columbia.tjw.item.ItemCurve;
import edu.columbia.tjw.item.ItemCurveParams;
import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemRegressor;

import java.io.Serializable;

public final class CurveResult<R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements Serializable
{
    private static final long serialVersionUID = 0x4ad8f1e94b389ef5L;

    private final ItemCurveParams<R, T> _params;
    private final double _entropy;



    public CurveResult(final ItemCurveParams<R, T> params_, final double entropy_) {
        _params = params_;
        _entropy = entropy_;
    }



}

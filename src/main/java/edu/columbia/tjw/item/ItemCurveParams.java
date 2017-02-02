/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.item;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author tyler
 * @param <R>
 * @param <T> The curve type defined by these params
 */
public final class ItemCurveParams<R extends ItemRegressor<R>, T extends ItemCurveType<T>> implements Serializable
{
    private static final long serialVersionUID = 0x7b981aa6c028bfa7L;

    private final int _size;
    private final double _intercept;
    private final double _beta;
    private final List<T> _types;
    private final List<R> _regressors;
    private final List<ItemCurve<T>> _curves;

    public ItemCurveParams(final T type_, final R field_, ItemCurveFactory<R, T> factory_, final double[] curvePoint_)
    {
        this(Collections.singletonList(type_), Collections.singletonList(field_), factory_, curvePoint_[0], curvePoint_[1], 2, curvePoint_);
    }

    public ItemCurveParams(final List<T> types_, final List<R> fields_, ItemCurveFactory<R, T> factory_, final double intercept_, final double beta_, final int arrayOffset_, final double[] curvePoint_)
    {
        if (Double.isNaN(intercept_))
        {
            throw new IllegalArgumentException("Intercept must be well defined.");
        }
        if (Double.isNaN(beta_))
        {
            throw new IllegalArgumentException("Beta must be well defined.");
        }
        if (types_.size() != fields_.size())
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        _types = Collections.unmodifiableList(new ArrayList<>(types_));
        _regressors = Collections.unmodifiableList(new ArrayList<>(fields_));
        _size = calculateSize(_types);

        _intercept = intercept_;
        _beta = beta_;

        int pointer = arrayOffset_;

        List<ItemCurve<T>> curveList = new ArrayList<>(_types.size());

        for (final T next : _types)
        {
            if (null == next)
            {
                curveList.add(null);
                continue;
            }

            ItemCurve<T> curve = factory_.generateCurve(next, pointer, curvePoint_);
            pointer += next.getParamCount();
            curveList.add(curve);
        }

        _curves = Collections.unmodifiableList(curveList);
    }

    public ItemCurveParams(final double intercept_, final double beta_, final T type_, final R field_, final ItemCurve<T> curve_)
    {
        this(intercept_, beta_, Collections.singletonList(type_), Collections.singletonList(field_), Collections.singletonList(curve_));
    }

    public ItemCurveParams(final double intercept_, final double beta_, final List<T> types_, final List<R> fields_, final List<ItemCurve<T>> curves_)
    {
        if (Double.isNaN(intercept_))
        {
            throw new IllegalArgumentException("Intercept must be well defined.");
        }
        if (Double.isNaN(beta_))
        {
            throw new IllegalArgumentException("Beta must be well defined.");
        }

        if (types_.size() != curves_.size())
        {
            throw new IllegalArgumentException("Invalid size.");
        }

        _types = Collections.unmodifiableList(new ArrayList<>(types_));
        _regressors = Collections.unmodifiableList(new ArrayList<>(fields_));
        _curves = Collections.unmodifiableList(new ArrayList<>(curves_));

        _intercept = intercept_;
        _beta = beta_;
        _size = calculateSize(_types);
    }

    public ItemCurveParams(final T type_, final R field_, ItemCurveFactory<R, T> factory_, final double intercept_, final double beta_, final double[] curveParams_)
    {
        this(Collections.singletonList(type_), Collections.singletonList(field_), factory_, intercept_, beta_, 0, curveParams_);
    }

    private static <T extends ItemCurveType<T>> int calculateSize(final List<T> types_)
    {
        int size = 2;

        for (final T next : types_)
        {
            if (null == next)
            {
                continue;
            }

            size += next.getParamCount();
        }

        return size;
    }

    public T getType(final int depth_)
    {
        return _types.get(depth_);
    }

    public R getRegressor(final int depth_)
    {
        return _regressors.get(depth_);
    }

    public ItemCurve<T> getCurve(final int depth_)
    {
        return _curves.get(depth_);
    }

    public double getIntercept()
    {
        return _intercept;
    }

    public double getBeta()
    {
        return _beta;
    }

    /**
     * Similar to the notion of entry depth in ItemParameters.
     *
     * @return
     */
    public int getEntryDepth()
    {
        return _types.size();
    }

    /**
     * How many parameters are we dealing with here.
     *
     * Including all curves, bet and intercept.
     *
     * @return
     */
    public int size()
    {
        return _size;
    }

    public int getInterceptIndex()
    {
        return 0;
    }

    public int getBetaIndex()
    {
        return 1;
    }

    public void extractPoint(final double[] point_)
    {
        if (point_.length != _size)
        {
            throw new IllegalArgumentException("Invalid point array size.");
        }

        point_[0] = _intercept;
        point_[1] = _beta;

        int pointer = 2;

        for (final ItemCurve<T> next : _curves)
        {
            if (null == next)
            {
                continue;
            }

            for (int i = 0; i < next.getCurveType().getParamCount(); i++)
            {
                point_[pointer++] = next.getParam(i);
            }
        }
    }

    public double[] generatePoint()
    {
        final double[] output = new double[this.size()];
        extractPoint(output);
        return output;
    }

}

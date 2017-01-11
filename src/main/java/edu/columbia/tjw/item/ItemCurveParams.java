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

/**
 *
 * @author tyler
 * @param <T> The curve type defined by these params
 */
public final class ItemCurveParams<T extends ItemCurveType<T>>
{
    private final T _type;
    private final double _intercept;
    private final double _beta;
    private final double[] _curveParams;

    public ItemCurveParams(final T type_, final double[] curvePoint_)
    {
        if (curvePoint_.length != (2 + type_.getParamCount()))
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        _type = type_;
        _intercept = extractIntercept(_type, curvePoint_);
        _beta = extractBeta(_type, curvePoint_);
        _curveParams = new double[_type.getParamCount()];

        for (int i = 0; i < _curveParams.length; i++)
        {
            _curveParams[i] = curvePoint_[i];
        }
    }

    public ItemCurveParams(final T type_, final double intercept_, final double beta_, final ItemCurve<T> curve_)
    {
        _type = type_;
        _intercept = intercept_;
        _beta = beta_;
        _curveParams = new double[type_.getParamCount()];

        for (int i = 0; i < _curveParams.length; i++)
        {
            _curveParams[i] = curve_.getParam(i);
        }
    }

    public ItemCurveParams(final T type_, final double intercept_, final double beta_, final double[] curveParams_)
    {
        if (curveParams_.length != type_.getParamCount())
        {
            throw new IllegalArgumentException("Size mismatch.");
        }

        _type = type_;
        _intercept = intercept_;
        _beta = beta_;
        _curveParams = curveParams_;
    }

    public T getType()
    {
        return _type;
    }

    public double getIntercept()
    {
        return _intercept;
    }

    public double getBeta()
    {
        return _beta;
    }

    public double getCurveParams(final int index_)
    {
        if (index_ >= _type.getParamCount())
        {
            throw new ArrayIndexOutOfBoundsException("Out of bounds: " + index_);
        }

        return _curveParams[index_];
    }

    public double[] generatePoint()
    {
        final int curveParamCount = _type.getParamCount();
        final double[] output = new double[curveParamCount + 2];

        for (int i = 0; i < curveParamCount; i++)
        {
            output[i] = _curveParams[i];
        }

        output[curveParamCount] = _intercept;
        output[curveParamCount + 1] = _beta;
        return output;
    }

    public static <T extends ItemCurveType<T>> double extractIntercept(final T type_, final double[] paramPoint_)
    {
        return paramPoint_[type_.getParamCount()];
    }

    public static <T extends ItemCurveType<T>> double extractBeta(final T type_, final double[] paramPoint_)
    {
        return paramPoint_[type_.getParamCount() + 1];
    }

    public static <T extends ItemCurveType<T>> int getInterceptParamNumber(final T type_)
    {
        return type_.getParamCount();
    }

    public static <T extends ItemCurveType<T>> int getBetaParamNumber(final T type_)
    {
        return type_.getParamCount() + 1;
    }

    public static <T extends ItemCurveType<T>> int translateCurveParamNumber(final T type_, final int input_)
    {
        if (input_ >= type_.getParamCount())
        {
            throw new ArrayIndexOutOfBoundsException("Out of range: " + input_);
        }

        return input_;
    }

}

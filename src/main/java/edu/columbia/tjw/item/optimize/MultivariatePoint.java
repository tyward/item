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
package edu.columbia.tjw.item.optimize;

import edu.columbia.tjw.item.algo.DoubleVector;
import edu.columbia.tjw.item.algo.VectorTools;
import edu.columbia.tjw.item.algo.WritableDoubleVector;
import edu.columbia.tjw.item.util.HashUtil;

/**
 * @author tyler
 */
public final class MultivariatePoint implements EvaluationPoint<MultivariatePoint>
{
    private final WritableDoubleVector _data;


    public MultivariatePoint(final DoubleVector raw_)
    {
        _data = new WritableDoubleVector(raw_.collapse());

        if (!VectorTools.isWellDefined(_data.getVector()))
        {
            throw new IllegalArgumentException("Points must be well defined: " + this.toString());
        }
    }

    public MultivariatePoint(final MultivariatePoint copyFrom_)
    {
        this._data = new WritableDoubleVector(copyFrom_.getElements().collapse());
    }

    public DoubleVector getElements()
    {
        return _data.getVector();
    }

    public void setElements(final DoubleVector value_)
    {
        _data.setEntries(value_.collapse());
    }

    public void setElement(final int index_, final double value_)
    {
        if (Double.isNaN(value_) || Double.isInfinite(value_))
        {
            throw new IllegalArgumentException("Points must be well defined.");
        }

        _data.setEntry(index_, value_);
    }

    public double getElement(final int index_)
    {
        return _data.getEntry(index_);
    }

    @Override
    public double project(MultivariatePoint input_)
    {
        if (this.getDimension() != input_.getDimension())
        {
            throw new IllegalArgumentException("Dimensionality must match.");
        }

        final double thatMagnitude = input_.getMagnitude();

        final int dimension = this.getDimension();
        double sum = 0.0;

        for (int i = 0; i < dimension; i++)
        {
            final double a = this.getElement(i);
            final double b = input_.getElement(i);
            sum += (a * b);
        }

        final double output = sum / (thatMagnitude);
        return output;
    }

    @Override
    public double getMagnitude()
    {
        return VectorTools.magnitude(this.getElements());
    }

    @Override
    public double distance(MultivariatePoint point_)
    {
        return VectorTools.distance(this.getElements(), point_.getElements());
    }

    @Override
    public void scale(double input_)
    {
        if (Double.isNaN(input_) || Double.isInfinite(input_))
        {
            throw new IllegalArgumentException("Points must be well defined: " + input_);
        }

        _data.setEntries(VectorTools.scalarMultiply(this.getElements(), input_));
    }

    @Override
    public void copy(MultivariatePoint point_)
    {
        checkLength(point_);
        _data.setEntries(point_.getElements().collapse());
    }

    @Override
    public void add(MultivariatePoint point_)
    {
        _data.setEntries(VectorTools.add(this.getElements(), point_.getElements()).collapse());
    }

    @Override
    public void normalize()
    {
        final double mag = this.getMagnitude();

        if (Double.isNaN(mag) || Double.isInfinite(mag))
        {
            throw new IllegalArgumentException("Points must be well defined: " + this.toString());
        }

        if (mag == 0.0)
        {
            throw new IllegalStateException("Cannot normalize the zero point.");
        }

        final double invMag = 1.0 / mag;

        _data.setEntries(VectorTools.scalarMultiply(this.getElements(), invMag).collapse());
    }

    @Override
    public MultivariatePoint clone()
    {
        return new MultivariatePoint(this);
    }

    public int getDimension()
    {
        return getElements().getSize();
    }

    public int getSize()
    {
        return this.getElements().getSize();
    }

    private void checkLength(final MultivariatePoint point_)
    {
        if (this.getSize() != point_.getSize())
        {
            throw new IllegalArgumentException("Length mismatch.");
        }
    }

    @Override
    public int hashCode()
    {
        int hash = HashUtil.startHash(MultivariatePoint.class);
        return VectorTools.hashCode(hash, this.getElements());
    }

    @Override
    public boolean equals(final Object that_)
    {
        if (null == that_)
        {
            return false;
        }
        if (this == that_)
        {
            return true;
        }
        if (that_.getClass() != this.getClass())
        {
            return false;
        }

        final MultivariatePoint other = (MultivariatePoint) that_;
        return VectorTools.equals(this.getElements(), other.getElements());
    }

    public boolean equals(final MultivariatePoint that_)
    {
        if (null == that_)
        {
            return false;
        }
        if (this == that_)
        {
            return true;
        }

        final boolean equal = VectorTools.equals(this.getElements(), that_.getElements());
        return equal;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getName() + ": (" + VectorTools.toString(this.getElements()) + ")");
        return builder.toString();
    }

}

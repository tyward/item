/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.optimize;

/**
 *
 * @author tyler
 */
public final class EvaluationResult
{
    private final double[] _results;
    private double _sum;
    private double _sqSum;
    private int _highWater;
    private int _resetCount;
    private int _highRow;

    public EvaluationResult(final int size_)
    {
        _results = new double[size_];
        _resetCount = 0;
        this.clear();
    }

    public double getSum()
    {
        return _sum;
    }

    public double getMean()
    {
        if (0 == _highWater)
        {
            return 0.0;
        }

        return _sum / _highWater;
    }

    /**
     * Return the variance of the mean.
     *
     * @return
     */
    public double getVariance()
    {
        if (0 == _highWater)
        {
            return 0.0;
        }

        final double invN = 1.0 / _highWater;
        final double eX = _sum * invN;
        final double eX2 = _sqSum * invN;
        final double variance = eX2 - (eX * eX);
        final double meanVariance = invN * variance;

        if (meanVariance < 0.0)
        {
            //Protect against rounding issues here. 
            return 0.0;
        }

        return meanVariance;
    }

    public double getStdDev()
    {
        final double variance = getVariance();
        final double stdDev = Math.sqrt(variance);
        return stdDev;
    }

    public int getHighWater()
    {
        return _highWater;
    }

    public int getHighRow()
    {
        return _highRow;
    }

    public void setHighRow(final int endRow_)
    {
        if (endRow_ < _highRow)
        {
            throw new IllegalArgumentException("High water must only increase.");
        }

        _highRow = endRow_;
    }

    public void add(final double observation_, final int startValue_, final int endRow_)
    {
        setHighRow(endRow_);
        _sum += observation_;
        _sqSum += (observation_ * observation_);

        if (_highWater != startValue_)
        {
            throw new IllegalArgumentException("Must add data in order!");
        }

        _results[startValue_] = observation_;
        _highWater++;

    }

    public int getResetCount()
    {
        return _resetCount;
    }

    public double get(final int row_)
    {
        if (row_ >= _highWater)
        {
            throw new IllegalArgumentException("Data not yet set.");
        }

        return _results[row_];
    }

    public void add(final EvaluationResult result_, final int startValue_, final int endRow_)
    {
        setHighRow(endRow_);

        if (_highWater != startValue_)
        {
            throw new IllegalArgumentException("Must add data in order!");
        }

        final int addCount = result_.getHighWater();

        if (addCount + _highWater > this._results.length)
        {
            System.out.println("Boing.");
        }

        if (addCount > 0)
        {
            System.arraycopy(result_._results, 0, this._results, this._highWater, addCount);
            _sum += result_.getSum();
            _sqSum += result_._sqSum;
        }

        _highWater += addCount;
    }

    public void clear()
    {
        _sum = 0.0;
        _sqSum = 0.0;
        _highWater = 0;
        _highRow = 0;
        _resetCount++;
    }
}

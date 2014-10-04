/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util;

/**
 *
 * @author tyler
 */
public class RectangularDoubleArray
{
    private final double[] _data;
    private final int _rows;
    private final int _columns;

    public RectangularDoubleArray(final int rows_, final int columns_)
    {
        final int size = rows_ * columns_;

        _rows = rows_;
        _columns = columns_;
        _data = new double[size];
    }

    public double get(final int index_)
    {
        final double output = _data[index_];
        return output;
    }

    public void set(final int index_, final double value_)
    {
        _data[index_] = value_;
    }

    public double get(final int row_, final int column_)
    {
        final int index = computeIndex(row_, column_);
        return get(index);
    }

    public void set(final int row_, final int column_, final double value_)
    {
        final int index = computeIndex(row_, column_);
        set(index, value_);
    }

    public int getRows()
    {
        return _rows;
    }

    public int getColumns()
    {
        return _columns;
    }

    public int size()
    {
        return _data.length;
    }

    /**
     * Check for errors that will not cause an array index out of bounds
     * exception.
     *
     * @param row_
     * @param column_
     */
    private int computeIndex(final int row_, final int column_)
    {
        if (column_ < 0)
        {
            throw new ArrayIndexOutOfBoundsException("Column must be non-negative: " + column_);
        }
        if (column_ >= _columns)
        {
            throw new ArrayIndexOutOfBoundsException("Column too large: " + column_);
        }

        final int index = (row_ * _columns) + column_;
        return index;
    }

}

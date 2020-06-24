package edu.columbia.tjw.item.fit;

import edu.columbia.tjw.item.ItemCurveType;
import edu.columbia.tjw.item.ItemParameters;
import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.algo.DoubleVector;

import java.io.Serializable;

public interface PackedParameters<S extends ItemStatus<S>, R extends ItemRegressor<R>,
        T extends ItemCurveType<T>> extends Cloneable, Serializable
{

    public int size();

    public DoubleVector getPacked();

    public void updatePacked(final double[] newParams_);

    public double getParameter(final int index_);

    public void setParameter(final int index_, final double value_);

    public double getEntryBeta(int index_);

    public int findBetaIndex(final int toStatus_, final int entryIndex_);

    public boolean isBeta(final int index_);

    public boolean betaIsFrozen(final int index_);

    public boolean isCurve(final int index_);

    /**
     * Gets the toStatus of this index.
     *
     * @param index_
     * @return
     */
    public int getTransition(final int index_);

    public int getEntry(final int index_);

    public int getDepth(final int index_);

    public int getCurveIndex(final int index_);

    public ItemParameters<S, R, T> generateParams();

    public ItemParameters<S, R, T> getOriginalParams();

    public PackedParameters<S, R, T> clone();
}

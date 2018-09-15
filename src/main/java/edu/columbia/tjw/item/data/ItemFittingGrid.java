package edu.columbia.tjw.item.data;

import edu.columbia.tjw.item.ItemRegressor;
import edu.columbia.tjw.item.ItemStatus;
import edu.columbia.tjw.item.util.EnumFamily;

public interface ItemFittingGrid<S extends ItemStatus<S>, R extends ItemRegressor<R>> extends ItemGrid<R>
{
    public S getFromStatus();

    public int getNextStatus(final int index_);
}

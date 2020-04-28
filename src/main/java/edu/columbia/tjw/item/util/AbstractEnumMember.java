package edu.columbia.tjw.item.util;

import java.io.ObjectStreamException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEnumMember<V extends AbstractEnumMember<V>> implements EnumMember<V>
{
    private static final long serialVersionUID = 0x42ce58ca93014783L;

    private final int _ordinal;
    private final String _name;
    private final int _hashCode;

    public AbstractEnumMember(final int ordinal_, final String name_, final int hashBase_)
    {
        _ordinal = ordinal_;
        _name = name_;

        int hash = HashUtil.startHash(this.getClass());
        hash = HashUtil.mix(hash, hashBase_);
        hash = HashUtil.mix(hash, name_.hashCode());
        _hashCode = HashUtil.mix(hash, ordinal_);
    }

    @Override
    public final String name()
    {
        return _name;
    }

    @Override
    public final int ordinal()
    {
        return _ordinal;
    }

    @Override
    public final int hashCode()
    {
        return _hashCode;
    }

    @Override
    public final boolean equals(final Object other_)
    {
        if (this == other_)
        {
            return true;
        }
        if (null == other_)
        {
            return false;
        }
        if (this.getClass() != other_.getClass())
        {
            return false;
        }

        final V that = (V) other_;

        if (this.getFamily() != that.getFamily())
        {
            return false;
        }

        return (0 == this.compareTo(that));
    }

    @Override
    public final int compareTo(final V that_)
    {
        if (this == that_)
        {
            return 0;
        }
        if (null == that_)
        {
            return 1;
        }

        if (!this.getFamily().equals(that_.getFamily()))
        {
            throw new IllegalArgumentException(
                    "Incomparable families: " + this.getFamily() + " != " + that_.getFamily());
        }

        //Within a family, compare based on ordinal.
        return Integer.compare(this.ordinal(), that_.ordinal());
    }



}

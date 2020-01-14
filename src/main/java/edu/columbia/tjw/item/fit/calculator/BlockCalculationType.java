package edu.columbia.tjw.item.fit.calculator;

public enum BlockCalculationType
{
    VALUE,
    FIRST_DERIVATIVE,
    SECOND_DERIVATIVE;

    private static final int VALUE_COUNT = values().length;

    public static int getValueCount()
    {
        return VALUE_COUNT;
    }
}

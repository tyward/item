package edu.columbia.tjw.item.optimize;

public enum OptimizationTarget
{
    // Standard MLE
    ENTROPY,
    // Direct usage of ICE correction term as-is, but with derivatives as per ICE2.
    ICE_RAW,
    // Direct usage of diagonalized ICE correction, but with ICE2 derivatives.
    ICE_SIMPLE,
    // Numerically stabilized ICE correction.
    ICE_STABLE_A,
    // Alternate stabilization formula for ICE.
    ICE_STABLE_B,
    // Objective is same as ICE3, but with optimized derivative computation.
    ICE,
    // Objective is same as ICE3, but with alternate derivative computation.
    ICE_B,
    // L2 regularization.
    L2;
}

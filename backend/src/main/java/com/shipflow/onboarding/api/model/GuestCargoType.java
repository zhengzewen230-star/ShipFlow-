package com.shipflow.onboarding.api.model;

/** Categories available only to the public estimate lead. They do not affect formal quote pricing. */
public enum GuestCargoType {
    GENERAL, BATTERY, SENSITIVE, LIQUID_POWDER, FRAGILE, OVERSIZED, OTHER
}

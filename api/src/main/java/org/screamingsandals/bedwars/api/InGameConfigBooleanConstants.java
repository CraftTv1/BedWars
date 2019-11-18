package org.screamingsandals.bedwars.api;

/**
 * @author Bedwars Team
 */

@Deprecated
public enum InGameConfigBooleanConstants {
    INHERIT(false, false),
    TRUE(true, true),
    FALSE(true, false);

    private final boolean original;
    private final boolean value;

    InGameConfigBooleanConstants(boolean original, boolean value) {
        this.original = original;
        this.value = value;
    }

    /**
     * @return
     */
    public boolean isInherited() {
        return !original;
    }

    /**
     * @return
     */
    public boolean isOriginal() {
        return original;
    }

    /**
     * @return
     */
    public boolean getValue() {
        return value;
    }
}

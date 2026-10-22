package com.pool8.rules;

/** 球组。 */
public enum Group {
    OPEN("未定"),
    SOLIDS("全色 1-7"),
    STRIPES("花色 9-15");

    public final String label;
    Group(String label) { this.label = label; }

    public Group other() {
        return this == SOLIDS ? STRIPES : this == STRIPES ? SOLIDS : OPEN;
    }

    public static Group of(int ballId) {
        if (ballId >= 1 && ballId <= 7) return SOLIDS;
        if (ballId >= 9 && ballId <= 15) return STRIPES;
        return OPEN;
    }
}

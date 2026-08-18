package org.lowern1ght.burg.people;

/**
 * Man or woman.
 *
 * <p>The mod has had a sex since its first generated name — it is the coin flip that chooses
 * between the masculine and feminine name endings — and for a long time nothing in the game could
 * tell you about it. Here it is a field on the record rather than a derivation, because the town
 * balances its intake: a settlement that took only men would never have a second generation, and
 * the balancing decision has to be recorded or it is re-rolled on every load.
 */
public enum Sex {
    MAN,
    WOMAN;

    public boolean isWoman() { return this == WOMAN; }

    /** For the wire and for NBT, so neither carries an enum name that a rename would break. */
    public int index() { return ordinal(); }

    public static Sex byIndex(int index) {
        return index == 1 ? WOMAN : MAN;
    }
}

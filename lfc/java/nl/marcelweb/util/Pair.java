package nl.marcelweb.util;

// Version 2011-10-22/19:11CEST

/**
 * An immutable pair objects.
 */
public class Pair<A, B> {
    private final A a;
    private final B b;

    private final int hashCode;

    /**
     * Constructs a new <code>Pair</code> containing the supplied objects.
     */
    public Pair(final A a, final B b) {
        this.a = a;
        this.b = b;

        if (a == null && b == null) {
            throw new NullPointerException();
        } else if (a == null) {
            this.hashCode = b.hashCode();
        } else if (b == null) {
            this.hashCode = a.hashCode();
        } else {
            this.hashCode = a.hashCode() ^ b.hashCode();
        }
    }

    /**
     * Returns the first object of this <code>Pair</code>.
     */
    public A getA() {
        return this.a;
    }

    /**
     * Returns a new <code>Pair</code> instance with the supplied object
     * paired with the second object of the current <code>Pair</code>.
     * <p>
     * This method does not modify the current instance!
     */
    public Pair<A, B> setA(final A a) {
        return new Pair<A, B>(a, this.b);
    }

    /**
     * Returns the second object of this <code>Pair</code>.
     */
    public B getB() {
        return this.b;
    }

    /**
     * Returns a new <code>Pair</code> instance with the supplied object
     * paired with the first object of the current <code>Pair</code>.
     * <p>
     * This method does not modify the current instance!
     */
    public Pair<A, B> setB(final B b) {
        return new Pair<A, B>(this.a, b);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null || !(object instanceof Pair)) {
            return false;
        }

        final Pair pair = (Pair)object;

        return this.a.equals(pair.a) && this.b.equals(pair.b);
    }
}


package pl.jakubtworek.chatsystem.lifecycle;

import java.util.*;

/** Stan procesu usuwania pozwala reconciliation wykryć pominięty cache, indeks lub blob. */
public final class DeletionPropagationTracker {
    public enum Destination { PRIMARY, SEARCH, CACHE, OBJECT_STORAGE, ANALYTICS }
    private final EnumSet<Destination> pending = EnumSet.allOf(Destination.class);
    public void confirmed(Destination destination) { pending.remove(destination); }
    public boolean complete() { return pending.isEmpty(); }
    public Set<Destination> pending() { return Set.copyOf(pending); }
}

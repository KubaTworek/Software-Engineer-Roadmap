package pl.jakubtworek.backend_engineering.stage_2.block_f;

/** Model kolejności cutoveru, nie implementacja rozproszonego fencing protokołu. */
public final class CutoverSimulation {
    public enum Owner { LEGACY, NEW }
    private Owner owner = Owner.LEGACY;
    private boolean writesPaused;
    private long sourceVersion;
    private long verifiedVersion = -1;
    private boolean newWrite;

    public synchronized void write(Owner caller) {
        if (writesPaused || caller != owner) throw new IllegalStateException("Zapis poza ownershipem");
        sourceVersion++;
        if (owner == Owner.NEW) newWrite = true;
    }

    public synchronized long pauseAndGetWatermark() {
        writesPaused = true;
        return sourceVersion;
    }

    public synchronized void verify(long copiedVersion, boolean equalContents) {
        if (!writesPaused || copiedVersion != sourceVersion || !equalContents) {
            throw new IllegalStateException("Backfill nie pokrywa zamrożonego źródła");
        }
        verifiedVersion = copiedVersion;
    }

    public synchronized void promote() {
        if (!writesPaused || owner != Owner.LEGACY || verifiedVersion != sourceVersion) {
            throw new IllegalStateException("Brak dowodu kompletności danych");
        }
        owner = Owner.NEW;
        writesPaused = false;
    }

    public synchronized void rollback() {
        // Po nowym zapisie cofnięcie routingu bez reverse sync zgubiłoby zmianę.
        if (newWrite) throw new IllegalStateException("Wymagana migracja zwrotna i nowa weryfikacja");
        owner = Owner.LEGACY;
        writesPaused = false;
    }

    public synchronized Owner owner() { return owner; }
}

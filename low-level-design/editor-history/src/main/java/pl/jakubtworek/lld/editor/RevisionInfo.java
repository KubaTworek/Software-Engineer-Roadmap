package pl.jakubtworek.lld.editor;

import java.util.OptionalLong;

/**
 * Mały opis wersji: jej ID, rodzic i rozmiar. Lista gałęzi nie spłaszcza tekstów.
 */
public record RevisionInfo(long id, OptionalLong parent, int length) {
}

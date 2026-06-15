package com.example.filestorage.search;

import com.example.filestorage.file.FileMetadataRepository;
import com.example.filestorage.folder.FolderRepository;
import com.example.filestorage.sharing.ResourceType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za aktualizowanie indeksu wyszukiwania.
 *
 * Właściwe dane plików i folderów znajdują się w tabelach files/folders.
 * SearchIndex jest pomocniczą tabelą zoptymalizowaną pod wyszukiwanie.
 *
 * Dzięki temu endpoint /search nie musi przeszukiwać osobno plików i folderów,
 * tylko korzysta z jednego uproszczonego indeksu.
 *
 * Indeksowanie jest asynchroniczne, więc operacje typu upload, rename albo delete
 * nie muszą czekać na aktualizację wyszukiwarki.
 */
@Service
public class SearchIndexService {

    /**
     * Repozytorium indeksu wyszukiwania.
     * Tu zapisujemy uproszczone rekordy reprezentujące pliki i foldery.
     */
    private final SearchIndexRepository searchIndexRepository;

    /**
     * Repozytorium metadanych plików.
     * Służy do pobrania aktualnego stanu pliku przed zaktualizowaniem indeksu.
     */
    private final FileMetadataRepository fileRepository;

    /**
     * Repozytorium folderów.
     * Służy do pobrania aktualnego stanu folderu przed zaktualizowaniem indeksu.
     */
    private final FolderRepository folderRepository;

    public SearchIndexService(SearchIndexRepository searchIndexRepository,
                              FileMetadataRepository fileRepository,
                              FolderRepository folderRepository) {
        this.searchIndexRepository = searchIndexRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
    }

    /**
     * Asynchronicznie odświeża wpis indeksu dla pliku.
     *
     * Ta metoda powinna być wywoływana po operacjach zmieniających plik:
     * - upload nowego pliku,
     * - rename pliku,
     * - move pliku, jeśli wyszukiwarka pokazuje ścieżkę lub kontekst folderu,
     * - restore pliku,
     * - zmiana typu/metadanych,
     * - utworzenie nowej wersji, jeśli wpływa na metadane.
     *
     * @Async oznacza, że metoda wykonuje się w tle,
     * poza głównym requestem użytkownika.
     */
    @Async
    @Transactional
    public void reindexFileAsync(UUID fileId) {
        /*
         * Pobieramy aktualny stan pliku z bazy.
         * Nie ufamy danym przekazanym z zewnątrz, bo indeks ma odzwierciedlać
         * aktualny stan zapisany w DB.
         */
        fileRepository.findById(fileId).ifPresent(file -> {

            /*
             * Jeśli plik jest w koszu albo został usunięty logicznie,
             * nie powinien pojawiać się w wynikach wyszukiwania.
             */
            if (file.getDeletedAt() != null) {
                searchIndexRepository.deleteByResourceId(fileId);
                return;
            }

            /*
             * Jeśli wpis indeksu już istnieje, aktualizujemy go.
             * Jeśli nie istnieje, tworzymy nowy.
             *
             * ID indeksu jest takie samo jak resourceId, więc łatwo robić upsert
             * dla pliku/folderu bez osobnego technicznego identyfikatora.
             */
            SearchIndex item = searchIndexRepository.findById(fileId)
                    .orElse(new SearchIndex(
                            ResourceType.FILE,
                            file.getId(),
                            file.getOwnerId(),
                            file.getName(),
                            file.getContentType(),
                            file.getSizeBytes()
                    ));

            /*
             * Odświeżamy pola, po których użytkownik będzie wyszukiwał
             * albo które będą pokazywane w wynikach.
             */
            item.refresh(
                    file.getName(),
                    file.getContentType(),
                    file.getSizeBytes()
            );

            searchIndexRepository.save(item);
        });
    }

    /**
     * Asynchronicznie odświeża wpis indeksu dla folderu.
     *
     * Ta metoda powinna być wywoływana po operacjach:
     * - utworzenie folderu,
     * - rename folderu,
     * - move folderu,
     * - restore folderu,
     * - delete folderu.
     *
     * Folder nie ma rozmiaru ani klasycznego MIME type,
     * dlatego zapisujemy typ jako "folder", a size jako null.
     */
    @Async
    @Transactional
    public void reindexFolderAsync(UUID folderId) {
        /*
         * Pobieramy aktualny stan folderu z bazy.
         */
        folderRepository.findById(folderId).ifPresent(folder -> {

            /*
             * Usunięty folder nie powinien być widoczny w wyszukiwarce.
             */
            if (folder.getDeletedAt() != null) {
                searchIndexRepository.deleteByResourceId(folderId);
                return;
            }

            /*
             * Upsert wpisu indeksu dla folderu.
             */
            SearchIndex item = searchIndexRepository.findById(folderId)
                    .orElse(new SearchIndex(
                            ResourceType.FOLDER,
                            folder.getId(),
                            folder.getOwnerId(),
                            folder.getName(),
                            "folder",
                            null
                    ));

            /*
             * Aktualizujemy nazwę i typ zasobu.
             * Size zostaje null, bo folder sam w sobie nie ma rozmiaru pliku.
             */
            item.refresh(
                    folder.getName(),
                    "folder",
                    null
            );

            searchIndexRepository.save(item);
        });
    }

    /**
     * Asynchronicznie usuwa zasób z indeksu wyszukiwania.
     *
     * Używane przy:
     * - soft delete,
     * - permanent delete,
     * - usuwaniu folderów,
     * - cleanupie zasobów.
     *
     * Usunięcie z indeksu nie usuwa właściwego pliku ani folderu.
     * Dotyczy tylko widoczności w wynikach wyszukiwania.
     */
    @Async
    @Transactional
    public void deleteAsync(UUID resourceId) {
        searchIndexRepository.deleteByResourceId(resourceId);
    }
}
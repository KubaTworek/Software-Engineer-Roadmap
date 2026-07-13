package com.ridesharing.mleta;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Kontroler HTTP dla ML ETA Service.
 *
 * W Etapie 4 ten endpoint reprezentuje usługę predykcji ETA,
 * czyli szacowania czasu dojazdu / czasu przejazdu na podstawie cech wejściowych.
 *
 * W aplikacji ride-sharing ETA jest krytyczne dla:
 * - matchingu kierowcy z pasażerem,
 * - sortowania kandydatów po czasie dojazdu,
 * - wyświetlania czasu oczekiwania pasażerowi,
 * - kalkulacji ceny,
 * - wykrywania anomalii,
 * - oceny jakości przejazdu.
 *
 * Controller jest cienki: waliduje request i deleguje predykcję do EtaModelService.
 */
@RestController
@RequestMapping("/api/v1/ml/eta")
public class EtaController {

    /**
     * Serwis modelu ETA.
     *
     * To tutaj powinna znajdować się właściwa logika predykcji:
     * - baseline liniowy,
     * - model ML,
     * - feature engineering,
     * - fallback,
     * - wersjonowanie modelu.
     */
    private final EtaModelService modelService;

    /**
     * Konstruktor wstrzykujący EtaModelService.
     *
     * Controller nie tworzy modelu ręcznie.
     */
    public EtaController(EtaModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * Zwraca predykcję ETA dla przekazanych cech.
     *
     * Endpoint:
     * POST /api/v1/ml/eta/predict
     *
     * Request może zawierać np.:
     * - dystans,
     * - porę dnia,
     * - dzień tygodnia,
     * - typ obszaru,
     * - aktualną prędkość,
     * - cityId,
     * - weather/traffic features,
     * - pickup/dropoff H3 cell.
     *
     * @Valid uruchamia walidację pól EtaPredictionRequest.
     *
     * Response powinien zawierać:
     * - predictedMinutes,
     * - confidence,
     * - modelVersion,
     * - ewentualny breakdown cech albo fallbackReason.
     */
    @PostMapping("/predict")
    public EtaPredictionResponse predict(@Valid @RequestBody EtaPredictionRequest request) {
        return modelService.predict(request);
    }

    /**
     * Zwraca wersję modelu ETA.
     *
     * Endpoint:
     * GET /api/v1/ml/eta/model
     *
     * To prosty endpoint diagnostyczny.
     * Pozwala sprawdzić, jaka wersja modelu jest aktualnie wystawiona przez usługę.
     *
     * W produkcji lepiej zwrócić DTO, np.:
     * - modelName,
     * - modelVersion,
     * - trainedAt,
     * - featureSchemaVersion,
     * - status.
     */
    @GetMapping("/model")
    public String model() {
        return "eta-linear-baseline-v4";
    }
}
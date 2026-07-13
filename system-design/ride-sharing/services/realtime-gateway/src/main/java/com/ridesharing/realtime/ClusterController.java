package com.ridesharing.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kontroler diagnostyczny dla Realtime Gateway / WebSocket Gateway.
 *
 * W Etapie 3/4 aplikacja ride-sharing może mieć wiele instancji realtime gateway,
 * które obsługują połączenia WebSocket/STOMP od pasażerów i kierowców.
 *
 * Ten endpoint pozwala sprawdzić, która instancja/node obsłużyła request.
 * Jest to przydatne przy:
 * - debugowaniu klastra,
 * - sprawdzaniu load balancera,
 * - diagnozowaniu sticky sessions,
 * - weryfikacji konfiguracji node-id,
 * - testach wdrożenia w Kubernetes/Docker Compose.
 *
 * To nie jest właściwy mechanizm routingu wiadomości realtime.
 * To tylko prosty endpoint informacyjny.
 */
@RestController
@RequestMapping("/api/v1/realtime")
public class ClusterController {

    /**
     * Identyfikator aktualnej instancji realtime node.
     *
     * Wartość pochodzi z konfiguracji:
     * app.realtime.node-id
     *
     * W środowisku produkcyjnym może to być np.:
     * - hostname poda,
     * - nazwa instancji,
     * - numer node'a,
     * - identyfikator regionu + instancji.
     */
    private final String nodeId;

    /**
     * Konstruktor wstrzykujący nodeId z konfiguracji.
     *
     * Jeśli property app.realtime.node-id nie będzie ustawione,
     * aplikacja nie wystartuje, bo nie ma tu wartości domyślnej.
     */
    public ClusterController(
            @Value("${app.realtime.node-id}") String nodeId
    ) {
        this.nodeId = nodeId;
    }

    /**
     * Zwraca informację o aktualnym node realtime.
     *
     * Endpoint:
     * GET /api/v1/realtime/node
     *
     * Przykładowy response:
     * {
     *   "nodeId": "realtime-gateway-1",
     *   "mode": "cluster-ready"
     * }
     *
     * "cluster-ready" oznacza tutaj tylko deklaratywny tryb działania.
     * Endpoint nie sprawdza realnego stanu klastra, brokera wiadomości,
     * liczby połączeń WebSocket ani synchronizacji między node'ami.
     */
    @GetMapping("/node")
    Map<String, String> node() {
        return Map.of(
                "nodeId", nodeId,
                "mode", "cluster-ready"
        );
    }
}
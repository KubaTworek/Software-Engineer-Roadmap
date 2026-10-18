package pl.jakubtworek.marketplace.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;

/**
 * Fail-closed policy dla webhooków chroniąca przed SSRF.
 *
 * Sama walidacja tekstu URL nie wystarcza: host może rozwiązać się do loopback,
 * adresu prywatnego lub zmienić odpowiedź DNS między walidacją a połączeniem.
 * Produkcyjny adapter powinien używać allowlisty, rozwiązać host raz, przypiąć
 * połączenie do zweryfikowanego IP, wyłączyć przekierowania i ponowić kontrolę po
 * każdej zmianie celu. Ta klasa pokazuje decyzję polityki przed granicą sieciową.
 */
public final class OutboundRequestPolicy {
    private final Set<String> allowedHosts;
    public OutboundRequestPolicy(Set<String> allowedHosts) { this.allowedHosts = Set.copyOf(allowedHosts); }

    public void verify(URI target, InetAddress resolvedAddress) {
        if (!"https".equalsIgnoreCase(target.getScheme()) || target.getUserInfo() != null
                || target.getHost() == null || !allowedHosts.contains(target.getHost())) {
            throw new SecurityException("outbound target is not allowlisted");
        }
        if (resolvedAddress.isAnyLocalAddress() || resolvedAddress.isLoopbackAddress()
                || resolvedAddress.isLinkLocalAddress() || resolvedAddress.isSiteLocalAddress()) {
            throw new SecurityException("outbound target resolved to a non-public address");
        }
    }
}

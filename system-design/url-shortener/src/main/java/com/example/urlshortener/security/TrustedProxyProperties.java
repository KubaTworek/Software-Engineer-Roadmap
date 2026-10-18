package com.example.urlshortener.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.trusted-proxies")
public class TrustedProxyProperties {
    /** Only these directly connected proxies may supply client-address headers. */
    private List<String> cidrs = new ArrayList<>();

    public List<String> getCidrs() { return cidrs; }
    public void setCidrs(List<String> cidrs) { this.cidrs = cidrs; }
}

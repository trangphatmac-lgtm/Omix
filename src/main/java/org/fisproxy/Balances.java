package org.fisproxy;

import java.util.Map;

/** Decimal-string balances. Do not convert these to floating point. */
public final class Balances {
    private final String servicePoint;
    private final String nfaCoin;
    private final String subscriptionPass;

    public Balances(String servicePoint, String nfaCoin, String subscriptionPass) {
        this.servicePoint = servicePoint;
        this.nfaCoin = nfaCoin;
        this.subscriptionPass = subscriptionPass;
    }

    public static Balances fromMapping(Object value) {
        Map<String, Object> data = value instanceof Map<?, ?> map
                ? org.fisproxy.internal.Json.asObject(map)
                : Map.of();
        return new Balances(
                DecimalStrings.decimalStr(data.getOrDefault("service_point", "0")),
                DecimalStrings.decimalStr(data.getOrDefault("nfa_coin", "0")),
                DecimalStrings.decimalStr(data.getOrDefault("subscription_pass", "0")));
    }

    public String servicePoint() {
        return servicePoint;
    }

    public String nfaCoin() {
        return nfaCoin;
    }

    public String subscriptionPass() {
        return subscriptionPass;
    }
}


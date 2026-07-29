package me.ksyz.accountmanager.auth;

public class Account {
    private String refreshToken;
    private String accessToken;
    private String username;
    private long unban;
    private String clientId;
    private String scope;
    private boolean offline;

    public Account(String refreshToken, String accessToken, String username, long unban, String clientId, String scope, boolean offline) {
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.username = username;
        this.unban = unban;
        this.clientId = clientId;
        this.scope = scope;
        this.offline = offline;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getUsername() {
        return username;
    }

    public long getUnban() {
        return unban;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setUnban(long unban) {
        this.unban = unban;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }
}

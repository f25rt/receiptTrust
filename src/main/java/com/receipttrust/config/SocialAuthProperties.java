package com.receipttrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth provider settings. All values come from environment variables so social
 * login stays inert until the relevant client id is configured.
 */
@ConfigurationProperties(prefix = "receipttrust.social")
public class SocialAuthProperties {

    private final Google google = new Google();
    private final Facebook facebook = new Facebook();

    public Google getGoogle() {
        return google;
    }

    public Facebook getFacebook() {
        return facebook;
    }

    public static class Google {
        /** OAuth Web client id (also the expected ID-token audience). Empty = disabled. */
        private String clientId = "";

        public boolean isEnabled() {
            return clientId != null && !clientId.isBlank();
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public static class Facebook {
        /** Facebook App id. Empty = disabled (prepared for later). */
        private String appId = "";
        /** Facebook App secret. Empty = disabled. */
        private String appSecret = "";

        public boolean isEnabled() {
            return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }
    }
}

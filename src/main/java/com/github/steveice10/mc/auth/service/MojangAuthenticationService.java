package com.github.steveice10.mc.auth.service;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.util.HTTP;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MojangAuthenticationService extends AuthenticationService {
    private static final URI DEFAULT_BASE_URI = URI.create("https://authserver.mojang.com/");
    private static final String AUTHENTICATE_ENDPOINT = "authenticate";
    private static final String REFRESH_ENDPOINT = "refresh";
    private static final String INVALIDATE_ENDPOINT = "invalidate";

    private String id;
    private String clientToken;
    protected String password;

    /**
     * Creates a new AuthenticationService instance.
     */
    public MojangAuthenticationService() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Creates a new AuthenticationService instance.
     *
     * @param clientToken Client token to use when making authentication requests.
     */
    public MojangAuthenticationService(String clientToken) {
        super(DEFAULT_BASE_URI);

        if(clientToken == null) {
            throw new IllegalArgumentException("ClientToken cannot be null.");
        }

        this.clientToken = clientToken;
    }

    /**
     * Gets the ID of the user logged in with the service.
     *
     * @return The user's ID.
     */
    public String getId() {
        return this.id;
    }

    public String getClientToken() {
        return clientToken;
    }

    /**
     * Gets the password of the service.
     *
     * @return The user's ID.
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Sets the password of the service.
     *
     * @param password Password to set.
     */
    public void setPassword(String password) {
        if(this.loggedIn && this.selectedProfile != null) {
            throw new IllegalStateException("Cannot change password while user is logged in and profile is selected.");
        } else {
            this.password = password;
        }
    }

    @Override
    public void login() throws RequestException {
        if(this.username == null || this.username.equals("")) {
            throw new InvalidCredentialsException("Invalid username.");
        }

        boolean token = this.accessToken != null && !this.accessToken.equals("");
        boolean password = this.password != null && !this.password.equals("");
        if(!token && !password) {
            throw new InvalidCredentialsException("Invalid password or access token.");
        }

        AuthenticateRefreshResponse response;
        if(token) {
            RefreshRequest request = new RefreshRequest(this.clientToken, this.accessToken, null);
            response = HTTP.makeRequest(this.getProxy(), this.getEndpointUri(REFRESH_ENDPOINT), request, AuthenticateRefreshResponse.class);
        } else {
            AuthenticationRequest request = new AuthenticationRequest(this.username, this.password, this.clientToken);
            response = HTTP.makeRequest(this.getProxy(), this.getEndpointUri(AUTHENTICATE_ENDPOINT), request, AuthenticateRefreshResponse.class);
        }

        if(response == null) {
            throw new RequestException("Server returned invalid response.");
        } else if(!response.clientToken.equals(this.clientToken)) {
            throw new RequestException("Server responded with incorrect client token.");
        }

        if(response.user != null && response.user.id != null) {
            this.id = response.user.id;
        } else {
            this.id = this.username;
        }

        this.accessToken = response.accessToken;
        this.profiles = response.availableProfiles != null ? Arrays.asList(response.availableProfiles) : Collections.<GameProfile>emptyList();
        this.selectedProfile = response.selectedProfile;

        this.properties.clear();
        if(response.user != null && response.user.properties != null) {
            this.properties.addAll(response.user.properties);
        }

        this.loggedIn = true;
    }

    public void logout() throws RequestException {
        MojangAuthenticationService.InvalidateRequest request = new MojangAuthenticationService.InvalidateRequest(this.clientToken, this.accessToken);
        HTTP.makeRequest(this.getProxy(), this.getEndpointUri(INVALIDATE_ENDPOINT), request);

        super.logout();
        this.id = null;
    }

    /**
     * Selects a game profile.
     *
     * @param profile Profile to select.
     * @throws RequestException If an error occurs while making the request.
     */
    public void selectGameProfile(GameProfile profile) throws RequestException {
        if(!this.loggedIn) {
            throw new RequestException("Cannot change game profile while not logged in.");
        } else if(this.selectedProfile != null) {
            throw new RequestException("Cannot change game profile when it is already selected.");
        } else if(profile == null || !this.profiles.contains(profile)) {
            throw new IllegalArgumentException("Invalid profile '" + profile + "'.");
        }

        RefreshRequest request = new RefreshRequest(this.clientToken, this.accessToken, profile);
        AuthenticateRefreshResponse response = HTTP.makeRequest(this.getProxy(), this.getEndpointUri(REFRESH_ENDPOINT), request, AuthenticateRefreshResponse.class);
        if(response == null) {
            throw new RequestException("Server returned invalid response.");
        } else if(!response.clientToken.equals(this.clientToken)) {
            throw new RequestException("Server responded with incorrect client token.");
        }

        this.accessToken = response.accessToken;
        this.selectedProfile = response.selectedProfile;
    }

    @Override
    public String toString() {
        return "MojangUserAuthentication{clientToken=" + this.clientToken + ", username=" + this.username + ", accessToken=" + this.accessToken + ", loggedIn=" + this.loggedIn + ", profiles=" + this.profiles + ", selectedProfile=" + this.selectedProfile + "}";
    }

    private static class Agent {
        private String name;
        private int version;

        protected Agent(String name, int version) {
            this.name = name;
            this.version = version;
        }
    }

    private static class User {
        public String id;
        public List<GameProfile.Property> properties;
    }

    private static class AuthenticationRequest {
        private Agent agent;
        private String username;
        private String password;
        private String clientToken;
        private boolean requestUser;

        protected AuthenticationRequest(String username, String password, String clientToken) {
            this.agent = new Agent("Minecraft", 1);
            this.username = username;
            this.password = password;
            this.clientToken = clientToken;
            this.requestUser = true;
        }
    }

    private static class RefreshRequest {
        private String clientToken;
        private String accessToken;
        private GameProfile selectedProfile;
        private boolean requestUser;

        protected RefreshRequest(String clientToken, String accessToken, GameProfile selectedProfile) {
            this.clientToken = clientToken;
            this.accessToken = accessToken;
            this.selectedProfile = selectedProfile;
            this.requestUser = true;
        }
    }

    private static class InvalidateRequest {
        private String clientToken;
        private String accessToken;

        protected InvalidateRequest(String clientToken, String accessToken) {
            this.clientToken = clientToken;
            this.accessToken = accessToken;
        }
    }

    private static class AuthenticateRefreshResponse {
        public String accessToken;
        public String clientToken;
        public GameProfile selectedProfile;
        public GameProfile[] availableProfiles;
        public User user;
    }
}

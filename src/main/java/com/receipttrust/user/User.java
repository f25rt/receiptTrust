package com.receipttrust.user;

import com.receipttrust.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uq_users_email", columnNames = "email")
})
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    /** Null for social accounts that have not set a local password. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "profile_image_path")
    private String profileImagePath;

    /** When the current profile image was uploaded (for expiry sweeps). */
    @Column(name = "profile_image_uploaded_at")
    private java.time.Instant profileImageUploadedAt;

    @Column(name = "trust_score", nullable = false)
    private int trustScore;

    /** How the account authenticates: LOCAL, GOOGLE, or FACEBOOK. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    /** Provider's stable user id (e.g. Google "sub"); null for LOCAL. */
    @Column(name = "provider_subject")
    private String providerSubject;

    /** Preferred display currency: "USD" (default) or "PHP". */
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "gender")
    private String gender;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    protected User() {
    }

    /** Local (password) account. */
    public User(String fullName, String username, String email, String passwordHash, int trustScore) {
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.trustScore = trustScore;
        this.provider = AuthProvider.LOCAL;
    }

    /** Social (OAuth) account with no local password. */
    public User(String fullName, String username, String email, int trustScore,
                AuthProvider provider, String providerSubject) {
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.passwordHash = null;
        this.trustScore = trustScore;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    /** Whether this account can sign in with a local password. */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getProfileImagePath() {
        return profileImagePath;
    }

    public void setProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
        this.profileImageUploadedAt = profileImagePath != null ? java.time.Instant.now() : null;
    }

    public java.time.Instant getProfileImageUploadedAt() {
        return profileImageUploadedAt;
    }

    public void setProfileImageUploadedAt(java.time.Instant profileImageUploadedAt) {
        this.profileImageUploadedAt = profileImageUploadedAt;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(int trustScore) {
        this.trustScore = trustScore;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public String getCurrency() {
        return currency == null ? "USD" : currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}

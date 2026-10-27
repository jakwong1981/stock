package com.cyberstock.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    public enum ColorScheme { GREEN_UP, RED_UP }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "preferred_locale", nullable = false)
    private String preferredLocale;

    @Column(name = "color_scheme", nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private ColorScheme colorScheme = ColorScheme.GREEN_UP;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {}

    public User(String email, String passwordHash, String displayName, String preferredLocale) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.preferredLocale = preferredLocale;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getPreferredLocale() { return preferredLocale; }
    public ColorScheme getColorScheme() { return colorScheme; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }
    public void setColorScheme(ColorScheme colorScheme) { this.colorScheme = colorScheme; }
}

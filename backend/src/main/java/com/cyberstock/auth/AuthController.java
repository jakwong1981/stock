package com.cyberstock.auth;

import com.cyberstock.common.ApiException;
import com.cyberstock.common.Locales;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 80) String displayName,
            String locale) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record TokenResponse(String accessToken, Instant expiresAt, UserController.Me user) {}

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
        }
        String locale = req.locale() != null && Locales.SUPPORTED.contains(req.locale()) ? req.locale() : Locales.EN_US;
        User u = users.save(new User(email, passwordEncoder.encode(req.password()), req.displayName(), locale));
        return token(u);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
                // Same error for unknown email and wrong password to avoid account enumeration
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        return token(u);
    }

    private TokenResponse token(User u) {
        JwtService.IssuedToken t = jwt.issue(u.getId());
        return new TokenResponse(t.token(), t.expiresAt(), UserController.Me.from(u));
    }
}

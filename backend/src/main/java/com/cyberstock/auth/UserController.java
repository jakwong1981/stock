package com.cyberstock.auth;

import com.cyberstock.common.ApiException;
import com.cyberstock.common.Locales;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    public record Me(UUID id, String email, String displayName, String preferredLocale, String colorScheme) {
        static Me from(User u) {
            return new Me(u.getId(), u.getEmail(), u.getDisplayName(), u.getPreferredLocale(), u.getColorScheme().name());
        }
    }

    public record UpdatePreferences(@Size(max = 80) String displayName, String preferredLocale, String colorScheme) {}

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private User current(Jwt jwt) {
        return users.findById(userId(jwt))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND"));
    }

    @GetMapping
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return Me.from(current(jwt));
    }

    @PatchMapping
    @Transactional
    public Me update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdatePreferences req) {
        User u = current(jwt);
        if (req.displayName() != null) {
            u.setDisplayName(req.displayName());
        }
        if (req.preferredLocale() != null) {
            if (!Locales.SUPPORTED.contains(req.preferredLocale())) { // non-null checked above
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LOCALE");
            }
            u.setPreferredLocale(req.preferredLocale());
        }
        if (req.colorScheme() != null) {
            try {
                u.setColorScheme(User.ColorScheme.valueOf(req.colorScheme()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COLOR_SCHEME");
            }
        }
        return Me.from(users.save(u));
    }
}

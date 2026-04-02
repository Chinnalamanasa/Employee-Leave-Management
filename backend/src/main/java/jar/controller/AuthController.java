package jar.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final InMemoryUserDetailsManager userDetailsManager;

    public AuthController(InMemoryUserDetailsManager userDetailsManager) {
        this.userDetailsManager = userDetailsManager;
    }

    // ✅ REGISTER
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {

        String username = payload.get("username");
        String password = payload.get("password");

        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username and password are required"));
        }

        if (userDetailsManager.userExists(username)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }

        userDetailsManager.createUser(User.withUsername(username)
                .password("{noop}" + password)
                .roles("EMPLOYEE")
                .build());

        return ResponseEntity.ok(Map.of("message", "Registration successful"));
    }

    // ✅ LOGIN (IMPORTANT)
    @PostMapping("/login")
public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {

    String username = payload.get("username");
    String password = payload.get("password");

    if (!userDetailsManager.userExists(username)) {
        return ResponseEntity.status(401).body(Map.of("message", "User not found"));
    }

    var user = userDetailsManager.loadUserByUsername(username);

    // remove {noop} prefix
    String storedPassword = user.getPassword().replace("{noop}", "");

    if (!storedPassword.equals(password)) {
        return ResponseEntity.status(401).body(Map.of("message", "Invalid password"));
    }

    return ResponseEntity.ok(Map.of(
            "message", "Login successful",
            "username", username
    ));
}
    }
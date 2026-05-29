package com.krish.supportapi.service;

import com.krish.supportapi.config.JwtProperties;
import com.krish.supportapi.domain.dto.request.LoginRequest;
import com.krish.supportapi.domain.dto.request.RegisterRequest;
import com.krish.supportapi.domain.dto.response.AuthResponse;
import com.krish.supportapi.domain.entity.RefreshToken;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.repository.RefreshTokenRepository;
import com.krish.supportapi.repository.UserRepository;
import com.krish.supportapi.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtTokenProvider jwtTokenProvider;

    private final JwtProperties jwtProperties;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final StringRedisTemplate stringRedisTemplate;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        JwtTokenProvider jwtTokenProvider,
        JwtProperties jwtProperties,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail();

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered: " + email);
        }

        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .role(UserRole.CUSTOMER)
            .isActive(true)
            .build();

        User savedUser = userRepository.save(user);
        String accessToken = jwtTokenProvider.generateAccessToken(savedUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser);

        saveRefreshToken(savedUser, refreshToken);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<RefreshToken> activeRefreshTokens =
            refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());
        activeRefreshTokens.forEach(refreshToken -> refreshToken.setRevoked(true));
        refreshTokenRepository.saveAll(activeRefreshTokens);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        saveRefreshToken(user, refreshToken);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public void logout(String refreshTokenString, String accessToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenString)
            .filter(refreshToken -> !refreshToken.isRevoked())
            .ifPresent(refreshToken -> {
                refreshToken.setRevoked(true);
                refreshTokenRepository.save(refreshToken);
            });

        stringRedisTemplate.opsForValue().set(
            "blacklist:" + accessToken,
            "true",
            jwtProperties.getAccessTokenExpiryMs(),
            TimeUnit.MILLISECONDS
        );
    }

    public AuthResponse refreshToken(String refreshTokenString) {
        RefreshToken currentRefreshToken = refreshTokenRepository.findByTokenHash(refreshTokenString)
            .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (currentRefreshToken.isRevoked()) {
            throw new RuntimeException("Refresh token has been revoked");
        }

        if (currentRefreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token has expired");
        }

        User user = currentRefreshToken.getUser();

        currentRefreshToken.setRevoked(true);
        refreshTokenRepository.save(currentRefreshToken);

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        saveRefreshToken(user, refreshToken);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    private void saveRefreshToken(User user, String refreshTokenString) {
        long refreshTokenExpirySeconds = jwtProperties.getRefreshTokenExpiryMs() / 1000;
        LocalDateTime expiresAt = LocalDateTime.now()
            .plus(refreshTokenExpirySeconds, ChronoUnit.SECONDS);

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(refreshTokenString)
            .expiresAt(expiresAt)
            .revoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .role(user.getRole())
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
    }
}

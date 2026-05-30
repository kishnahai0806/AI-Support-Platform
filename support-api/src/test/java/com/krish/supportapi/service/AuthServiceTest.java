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
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    private RegisterRequest registerRequest;

    private LoginRequest loginRequest;

    private String accessToken;

    private String refreshTokenString;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
            .id(UUID.randomUUID())
            .email("customer@example.com")
            .fullName("Sample Customer")
            .passwordHash("encodedPassword")
            .role(UserRole.CUSTOMER)
            .isActive(true)
            .build();

        registerRequest = RegisterRequest.builder()
            .email("customer@example.com")
            .password("password123")
            .fullName("Sample Customer")
            .build();

        loginRequest = LoginRequest.builder()
            .email("customer@example.com")
            .password("password123")
            .build();

        accessToken = "test.access.token";
        refreshTokenString = "test.refresh.token";
    }

    @Test
    void register_success_returnsAuthResponse() {
        Mockito.when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        Mockito.when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class))).thenReturn(sampleUser);
        Mockito.when(jwtTokenProvider.generateAccessToken(sampleUser)).thenReturn(accessToken);
        Mockito.when(jwtTokenProvider.generateRefreshToken(sampleUser)).thenReturn(refreshTokenString);
        Mockito.when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        Mockito.when(refreshTokenRepository.save(ArgumentMatchers.any(RefreshToken.class)))
            .thenReturn(createRefreshToken(false, LocalDateTime.now().plusDays(7)));

        AuthResponse response = authService.register(registerRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(sampleUser.getEmail(), response.getEmail());
        Assertions.assertEquals(UserRole.CUSTOMER, response.getRole());
        Assertions.assertEquals(accessToken, response.getAccessToken());
        Mockito.verify(userRepository, Mockito.times(1)).save(ArgumentMatchers.any(User.class));
        Mockito.verify(refreshTokenRepository, Mockito.times(1))
            .save(ArgumentMatchers.any(RefreshToken.class));
    }

    @Test
    void register_emailAlreadyExists_throwsRuntimeException() {
        Mockito.when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        Assertions.assertThrows(RuntimeException.class, () -> authService.register(registerRequest));

        Mockito.verify(userRepository, Mockito.never()).save(ArgumentMatchers.any(User.class));
    }

    @Test
    void login_success_returnsAuthResponse() {
        Mockito.when(authenticationManager.authenticate(
            ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class)
        )).thenReturn(null);
        Mockito.when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(sampleUser));
        Mockito.when(refreshTokenRepository.findByUserIdAndRevokedFalse(sampleUser.getId()))
            .thenReturn(Collections.emptyList());
        Mockito.when(jwtTokenProvider.generateAccessToken(sampleUser)).thenReturn(accessToken);
        Mockito.when(jwtTokenProvider.generateRefreshToken(sampleUser)).thenReturn(refreshTokenString);
        Mockito.when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        Mockito.when(refreshTokenRepository.save(ArgumentMatchers.any(RefreshToken.class)))
            .thenReturn(createRefreshToken(false, LocalDateTime.now().plusDays(7)));

        AuthResponse response = authService.login(loginRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(sampleUser.getEmail(), response.getEmail());
        Mockito.verify(authenticationManager, Mockito.times(1))
            .authenticate(ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_badCredentials_throwsException() {
        Mockito.when(authenticationManager.authenticate(
            ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class)
        )).thenThrow(new BadCredentialsException("Bad credentials"));

        Assertions.assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        Mockito.verify(userRepository, Mockito.never()).findByEmail(ArgumentMatchers.anyString());
    }

    @Test
    void logout_success_revokesTokenAndBlacklists() {
        RefreshToken refreshToken = createRefreshToken(false, LocalDateTime.now().plusDays(7));

        Mockito.when(refreshTokenRepository.findByTokenHash(refreshTokenString))
            .thenReturn(Optional.of(refreshToken));
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(jwtProperties.getAccessTokenExpiryMs()).thenReturn(900000L);

        authService.logout(refreshTokenString, accessToken);

        Mockito.verify(refreshTokenRepository, Mockito.times(1)).save(refreshToken);
        Mockito.verify(valueOperations, Mockito.times(1))
            .set("blacklist:" + accessToken, "true", 900000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void refreshToken_success_returnsNewAuthResponse() {
        RefreshToken currentRefreshToken = createRefreshToken(false, LocalDateTime.now().plusDays(7));

        Mockito.when(refreshTokenRepository.findByTokenHash(refreshTokenString))
            .thenReturn(Optional.of(currentRefreshToken));
        Mockito.when(jwtTokenProvider.generateAccessToken(sampleUser)).thenReturn(accessToken);
        Mockito.when(jwtTokenProvider.generateRefreshToken(sampleUser)).thenReturn(refreshTokenString);
        Mockito.when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        Mockito.when(refreshTokenRepository.save(ArgumentMatchers.any(RefreshToken.class)))
            .thenReturn(currentRefreshToken);

        AuthResponse response = authService.refreshToken(refreshTokenString);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(accessToken, response.getAccessToken());
        Mockito.verify(refreshTokenRepository, Mockito.times(2))
            .save(ArgumentMatchers.any(RefreshToken.class));
    }

    @Test
    void refreshToken_tokenRevoked_throwsRuntimeException() {
        RefreshToken currentRefreshToken = createRefreshToken(true, LocalDateTime.now().plusDays(7));

        Mockito.when(refreshTokenRepository.findByTokenHash(refreshTokenString))
            .thenReturn(Optional.of(currentRefreshToken));

        Assertions.assertThrows(RuntimeException.class, () -> authService.refreshToken(refreshTokenString));

        Mockito.verify(jwtTokenProvider, Mockito.never()).generateAccessToken(ArgumentMatchers.any(User.class));
    }

    @Test
    void refreshToken_tokenExpired_throwsRuntimeException() {
        RefreshToken currentRefreshToken = createRefreshToken(false, LocalDateTime.now().minusDays(1));

        Mockito.when(refreshTokenRepository.findByTokenHash(refreshTokenString))
            .thenReturn(Optional.of(currentRefreshToken));

        Assertions.assertThrows(RuntimeException.class, () -> authService.refreshToken(refreshTokenString));

        Mockito.verify(jwtTokenProvider, Mockito.never()).generateAccessToken(ArgumentMatchers.any(User.class));
    }

    private RefreshToken createRefreshToken(boolean revoked, LocalDateTime expiresAt) {
        return RefreshToken.builder()
            .id(UUID.randomUUID())
            .user(sampleUser)
            .tokenHash(refreshTokenString)
            .expiresAt(expiresAt)
            .revoked(revoked)
            .build();
    }
}

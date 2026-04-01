package com.cryptonex.security;

import com.cryptonex.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitingFilterUnitTest {

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private IpBanService ipBanService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Bucket bucket;

    @Mock
    private ConsumptionProbe probe;

    @InjectMocks
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    public void setup() throws Exception {
        // Default behavior: IP is not banned
        lenient().when(ipBanService.isBanned(anyString())).thenReturn(false);

        // Mock getWriter
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        // Mock MeterRegistry counter
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mock(Counter.class));
    }

    @Test
    public void testGlobalLimit_Passes() throws Exception {
        when(request.getRequestURI()).thenReturn("/some/path");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Global bucket setup
        when(rateLimitingService.resolveBucket(startsWith("global_ip_"))).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    public void testGlobalLimit_Exceeded() throws Exception {
        when(request.getRequestURI()).thenReturn("/some/path");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Global bucket setup
        when(rateLimitingService.resolveBucket(startsWith("global_ip_"))).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false); // Fail
        when(probe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L); // 5s

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        verify(response).setStatus(429);
        verify(ipBanService).recordViolation(anyString());
    }

    @Test
    public void testAuthSignin_Limit() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/signin");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Global bucket mock (pass)
        Bucket globalBucket = mock(Bucket.class);
        ConsumptionProbe globalProbe = mock(ConsumptionProbe.class);
        when(rateLimitingService.resolveBucket(startsWith("global_ip_"))).thenReturn(globalBucket);
        when(globalBucket.tryConsumeAndReturnRemaining(1)).thenReturn(globalProbe);
        when(globalProbe.isConsumed()).thenReturn(true);

        // Auth bucket mock
        when(rateLimitingService.resolveBucket(startsWith("auth_signin_ip_"))).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true); // pass

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitingService).resolveBucket(startsWith("auth_signin_ip_"));
    }
}

package com.wayrapass.service;

import com.wayrapass.dto.request.DriverRequest;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.*;
import com.wayrapass.repository.DriverRepository;
import com.wayrapass.repository.TripRepository;
import com.wayrapass.repository.UserRepository;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceC4Test {
    @Mock DriverRepository drivers;
    @Mock UserRepository users;
    @Mock TripRepository trips;
    @Mock PasswordEncoder encoder;
    @Mock CurrentUser current;
    @InjectMocks DriverService service;

    @Test
    void createsAuthorizedAvailableDriverWithEnabledAccount() {
        DriverRequest request = request(AuthorizationStatus.AUTHORIZED, DriverOperationalStatus.AVAILABLE,
                LocalDate.now().plusYears(1));
        when(encoder.encode("Password123!")).thenReturn("hash");
        when(users.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(20L);
            return user;
        });
        when(drivers.save(any())).thenAnswer(invocation -> {
            Driver driver = invocation.getArgument(0);
            driver.setId(10L);
            return driver;
        });

        var result = service.create(request);

        assertEquals(10L, result.id());
        assertEquals(AuthorizationStatus.AUTHORIZED, result.authorizationStatus());
        assertTrue(result.operationalStatus() == DriverOperationalStatus.AVAILABLE);
        assertTrue(result.userId() == 20L);
    }

    @Test
    void listsDriversWithAdministrativeStatuses() {
        Driver driver = driver(DriverOperationalStatus.UNAVAILABLE);
        when(drivers.findAllByOrderByIdAsc()).thenReturn(List.of(driver));

        var result = service.all();

        assertEquals(1, result.size());
        assertEquals(AuthorizationStatus.AUTHORIZED, result.getFirst().authorizationStatus());
        assertEquals(DriverOperationalStatus.UNAVAILABLE, result.getFirst().operationalStatus());
    }

    @Test
    void availableDriverMustBeAuthorized() {
        DriverRequest request = request(AuthorizationStatus.PENDING, DriverOperationalStatus.AVAILABLE,
                LocalDate.now().plusYears(1));

        assertThrows(BusinessRuleException.class, () -> service.create(request));
        verifyNoInteractions(encoder);
    }

    @Test
    void authorizedDriverMustHaveCurrentLicense() {
        DriverRequest request = request(AuthorizationStatus.AUTHORIZED, DriverOperationalStatus.UNAVAILABLE,
                LocalDate.now().minusDays(1));

        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void updateCanReenableInactiveDriverAndChangeEmail() {
        Driver driver = driver(DriverOperationalStatus.INACTIVE);
        DriverRequest request = request(AuthorizationStatus.AUTHORIZED, DriverOperationalStatus.AVAILABLE,
                LocalDate.now().plusYears(1));
        when(drivers.findById(10L)).thenReturn(Optional.of(driver));

        var result = service.update(10L, request);

        assertEquals(DriverOperationalStatus.AVAILABLE, result.operationalStatus());
        assertTrue(driver.getUser().isEnabled());
        assertEquals("driver@wayrapass.test", driver.getUser().getEmail());
    }

    @Test
    void updateRejectsDuplicateEmail() {
        Driver driver = driver(DriverOperationalStatus.UNAVAILABLE);
        DriverRequest request = request(AuthorizationStatus.AUTHORIZED, DriverOperationalStatus.UNAVAILABLE,
                LocalDate.now().plusYears(1));
        when(drivers.findById(10L)).thenReturn(Optional.of(driver));
        when(users.existsByEmailIgnoreCaseAndIdNot(request.email(), 20L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.update(10L, request));
    }

    @Test
    void deleteSoftDisablesDriverAndPreservesEntity() {
        Driver driver = driver(DriverOperationalStatus.AVAILABLE);
        when(drivers.findById(10L)).thenReturn(Optional.of(driver));

        service.delete(10L);

        assertEquals(DriverOperationalStatus.INACTIVE, driver.getOperationalStatus());
        assertFalse(driver.getUser().isEnabled());
        verify(drivers, never()).delete(any());
    }

    @Test
    void driverWithOperationalTripCannotBeDisabled() {
        Driver driver = driver(DriverOperationalStatus.AVAILABLE);
        when(drivers.findById(10L)).thenReturn(Optional.of(driver));
        when(trips.existsByDriverIdAndStatusIn(eq(10L), anyCollection())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.delete(10L));
        assertEquals(DriverOperationalStatus.AVAILABLE, driver.getOperationalStatus());
    }

    private DriverRequest request(AuthorizationStatus authorization, DriverOperationalStatus status,
                                  LocalDate expiration) {
        return new DriverRequest("Carlos Chofer", "driver@wayrapass.test", "Password123!", "999999999",
                "LIC-123", "AIII-C", expiration, "https://cdn.test/credential.png", authorization, status);
    }

    private Driver driver(DriverOperationalStatus status) {
        User user = new User();
        user.setId(20L);
        user.setEmail("old@wayrapass.test");
        user.setFullName("Conductor");
        user.setEnabled(status != DriverOperationalStatus.INACTIVE);
        Driver driver = new Driver();
        driver.setId(10L);
        driver.setUser(user);
        driver.setLicenseNumber("LIC-OLD");
        driver.setLicenseCategory("AII-B");
        driver.setLicenseExpiration(LocalDate.now().plusYears(1));
        driver.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        driver.setOperationalStatus(status);
        return driver;
    }
}

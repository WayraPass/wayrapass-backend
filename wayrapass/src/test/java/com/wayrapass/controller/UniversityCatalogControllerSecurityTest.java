package com.wayrapass.controller;

import com.wayrapass.dto.response.CatalogResponses;
import com.wayrapass.service.CatalogService;
import com.wayrapass.service.StudentAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UniversityCatalogControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CatalogService catalogs;
    @MockitoBean StudentAccessService studentAccess;

    @Test
    void publicInstitutionsNeedNoJwtAndExposeOnlyRegistrationFields() throws Exception {
        when(catalogs.publicInstitutions()).thenReturn(List.of(new CatalogResponses.PublicInstitution(
                25L, "UPC", "Monterrico", "UPC — Monterrico", "Santiago de Surco")));

        mockMvc.perform(get("/api/public/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(25))
                .andExpect(jsonPath("$[0].displayName").value("UPC — Monterrico"))
                .andExpect(jsonPath("$[0].district").value("Santiago de Surco"))
                .andExpect(jsonPath("$[0].address").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].active").doesNotExist());
    }

    @Test
    void routeCatalogsAndGroupedSearchRequireStudent() throws Exception {
        mockMvc.perform(get("/api/routes/catalog/districts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/routes/catalog/stops").param("district", "San Miguel"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/routes/search/grouped")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/routes/search/grouped").with(user("family@test").roles("FAMILY")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanUseDistrictAndStopCatalogs() throws Exception {
        when(catalogs.pickupDistricts()).thenReturn(List.of("Barranco", "San Miguel"));
        when(catalogs.pickupStops("San Miguel")).thenReturn(List.of(new CatalogResponses.PickupStop(
                101L, "Plaza San Miguel", "San Miguel", "Av. La Marina")));

        mockMvc.perform(get("/api/routes/catalog/districts").with(user("student@test").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(content().json("[\"Barranco\",\"San Miguel\"]"));
        mockMvc.perform(get("/api/routes/catalog/stops").param("district", "San Miguel")
                        .with(user("student@test").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Plaza San Miguel"))
                .andExpect(jsonPath("$[0].latitude").doesNotExist())
                .andExpect(jsonPath("$[0].longitude").doesNotExist());
    }
}

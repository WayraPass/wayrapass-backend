package com.wayrapass.controller;

import com.wayrapass.dto.response.CatalogResponses;
import com.wayrapass.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCatalogController {
    private final CatalogService catalogs;

    @GetMapping("/institutions")
    public List<CatalogResponses.PublicInstitution> institutions() {
        return catalogs.publicInstitutions();
    }
}

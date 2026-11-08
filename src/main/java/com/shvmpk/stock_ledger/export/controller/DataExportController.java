package com.shvmpk.stock_ledger.export.controller;

import com.shvmpk.stock_ledger.export.service.DataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportService dataExportService;

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<String> exportCatalog() {
        String csv = dataExportService.exportCatalogCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"catalog_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<String> exportOrders() {
        String csv = dataExportService.exportOrdersCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<String> exportHistory() {
        String csv = dataExportService.exportStockHistoryCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock_history_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}

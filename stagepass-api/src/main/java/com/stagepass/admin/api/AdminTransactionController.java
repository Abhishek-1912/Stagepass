package com.stagepass.admin.api;

import com.stagepass.admin.service.AdminRefundService;
import com.stagepass.admin.service.AdminTransactionQueries;
import com.stagepass.booking.api.BookingResponse;
import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminTransactionController {

    private final AdminTransactionQueries queries;
    private final AdminRefundService refunds;
    private final CurrentUser currentUser;

    public AdminTransactionController(AdminTransactionQueries queries, AdminRefundService refunds, CurrentUser currentUser) {
        this.queries = queries;
        this.refunds = refunds;
        this.currentUser = currentUser;
    }

    @GetMapping("/payments")
    public PageResponse<AdminPaymentRow> payments(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.payments(q, status, from, to, page, size);
    }

    @GetMapping("/payments/export")
    public void export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status, HttpServletResponse response) throws IOException {
        AdminTransactionQueries.requireExportRange(from, to);
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(AdminTransactionQueries.csvFilename(from, to)).build().toString());
        Writer out = response.getWriter();
        queries.writePaymentsCsv(currentUser.id(), from, to, status, out);
        out.flush();
    }

    @GetMapping("/refunds")
    public PageResponse<AdminRefundRow> refunds(@RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return queries.refunds(status, q, page, size);
    }

    @GetMapping("/disputes")
    public PageResponse<AdminDisputeRow> disputes(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return queries.disputes(status, page, size);
    }

    @PostMapping("/bookings/{id}/refund")
    public BookingResponse refund(@PathVariable UUID id, @Valid @RequestBody AdminRefundRequest request) {
        return refunds.refund(currentUser.id(), id, request.ticketIds(), request.includeFee(), request.reason());
    }

    @PostMapping("/refunds/{id}/retry")
    public AdminRefundRow retry(@PathVariable UUID id) {
        return refunds.retry(currentUser.id(), id);
    }
}

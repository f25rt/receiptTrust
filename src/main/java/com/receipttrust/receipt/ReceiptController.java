package com.receipttrust.receipt;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final CurrentUserService currentUserService;

    public ReceiptController(ReceiptService receiptService, CurrentUserService currentUserService) {
        this.receiptService = receiptService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptDtos.ReceiptResponse create(
            @RequestParam("storeName") String storeName,
            @RequestParam("purchaseDate") LocalDate purchaseDate,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam("image") MultipartFile image) {
        User me = currentUserService.require();
        return ReceiptDtos.ReceiptResponse.from(
                receiptService.create(me, storeName, purchaseDate, notes, image));
    }

    @GetMapping("/{id}")
    public ReceiptDtos.ReceiptResponse get(@PathVariable Long id) {
        User me = currentUserService.require();
        return ReceiptDtos.ReceiptResponse.from(receiptService.getForViewer(me, id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<InputStreamResource> image(@PathVariable Long id) {
        User me = currentUserService.require();
        Receipt receipt = receiptService.getForViewer(me, id);
        InputStreamResource resource = new InputStreamResource(receiptService.openImage(me, id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.getImageContentType()))
                .body(resource);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptDtos.ItemResponse addItem(@PathVariable Long id,
                                            @Valid @RequestBody ReceiptDtos.ItemCreateRequest request) {
        User me = currentUserService.require();
        return ReceiptDtos.ItemResponse.from(
                receiptService.addItem(me, id, request.name(), request.quantity(), request.unitPrice()));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ReceiptDtos.ItemResponse updateItem(@PathVariable Long id,
                                               @PathVariable Long itemId,
                                               @Valid @RequestBody ReceiptDtos.ItemCreateRequest request) {
        User me = currentUserService.require();
        return ReceiptDtos.ItemResponse.from(
                receiptService.updateItem(me, id, itemId,
                        request.name(), request.quantity(), request.unitPrice()));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        User me = currentUserService.require();
        receiptService.deleteItem(me, id, itemId);
    }

    @GetMapping("/{id}/items")
    public List<ReceiptDtos.ItemResponse> items(@PathVariable Long id) {
        User me = currentUserService.require();
        return receiptService.listItems(me, id).stream()
                .map(ReceiptDtos.ItemResponse::from)
                .toList();
    }
}

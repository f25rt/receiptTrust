package com.receipttrust.receipt;

import com.receipttrust.common.exception.ApiExceptions;
import com.receipttrust.common.storage.FileStorageService;
import com.receipttrust.common.storage.StoredFile;
import com.receipttrust.debt.DebtRepository;
import com.receipttrust.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptItemRepository itemRepository;
    private final DebtRepository debtRepository;
    private final FileStorageService fileStorageService;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ReceiptItemRepository itemRepository,
                          DebtRepository debtRepository,
                          FileStorageService fileStorageService) {
        this.receiptRepository = receiptRepository;
        this.itemRepository = itemRepository;
        this.debtRepository = debtRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public Receipt create(User owner, String storeName, LocalDate purchaseDate,
                          String notes, MultipartFile image) {
        if (storeName == null || storeName.isBlank()) {
            throw new ApiExceptions.ValidationException("Store name is required");
        }
        if (purchaseDate == null) {
            throw new ApiExceptions.ValidationException("Purchase date is required");
        }
        StoredFile stored = fileStorageService.storeReceiptImage(image);
        Receipt receipt = new Receipt(owner, storeName, purchaseDate, notes,
                stored.relativePath(), stored.contentType());
        return receiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Receipt getForViewer(User viewer, Long receiptId) {
        Receipt receipt = require(receiptId);
        if (receipt.getOwner().getId().equals(viewer.getId())) {
            return receipt;
        }
        boolean isDebtor = debtRepository.findByDebtor(viewer).stream()
                .anyMatch(d -> d.getReceipt().getId().equals(receiptId));
        if (!isDebtor) {
            throw new ApiExceptions.ForbiddenException("Not allowed to view this receipt");
        }
        return receipt;
    }

    @Transactional(readOnly = true)
    public InputStream openImage(User viewer, Long receiptId) {
        Receipt receipt = getForViewer(viewer, receiptId);
        return fileStorageService.openReceiptImage(receipt.getImagePath());
    }

    @Transactional
    public ReceiptItem addItem(User owner, Long receiptId, String name, int quantity, BigDecimal unitPrice) {
        Receipt receipt = requireOwned(owner, receiptId);
        assertNotFinalized(receipt);
        if (quantity < 1) {
            throw new ApiExceptions.ValidationException("Quantity must be at least 1");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new ApiExceptions.ValidationException("Price must not be negative");
        }
        return itemRepository.save(new ReceiptItem(receipt, name, quantity, unitPrice));
    }

    @Transactional
    public ReceiptItem updateItem(User owner, Long receiptId, Long itemId,
                                  String name, int quantity, BigDecimal unitPrice) {
        Receipt receipt = requireOwned(owner, receiptId);
        assertNotFinalized(receipt);
        if (quantity < 1) {
            throw new ApiExceptions.ValidationException("Quantity must be at least 1");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new ApiExceptions.ValidationException("Price must not be negative");
        }
        ReceiptItem item = requireItem(receipt, itemId);
        item.setName(name);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return itemRepository.save(item);
    }

    @Transactional
    public void deleteItem(User owner, Long receiptId, Long itemId) {
        Receipt receipt = requireOwned(owner, receiptId);
        assertNotFinalized(receipt);
        ReceiptItem item = requireItem(receipt, itemId);
        itemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public List<ReceiptItem> listItems(User viewer, Long receiptId) {
        Receipt receipt = getForViewer(viewer, receiptId);
        return itemRepository.findByReceipt(receipt);
    }

    // ---- helpers ----

    public Receipt require(Long receiptId) {
        return receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Receipt not found"));
    }

    public Receipt requireOwned(User owner, Long receiptId) {
        Receipt receipt = require(receiptId);
        if (!receipt.getOwner().getId().equals(owner.getId())) {
            throw new ApiExceptions.ForbiddenException("Not the receipt owner");
        }
        return receipt;
    }

    private ReceiptItem requireItem(Receipt receipt, Long itemId) {
        ReceiptItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Item not found"));
        if (!item.getReceipt().getId().equals(receipt.getId())) {
            throw new ApiExceptions.ResourceNotFoundException("Item not found on this receipt");
        }
        return item;
    }

    private void assertNotFinalized(Receipt receipt) {
        if (receipt.isFinalized()) {
            throw new ApiExceptions.ConflictException("Receipt already finalized; items are locked");
        }
    }
}

package com.receipttrust.receipt;

import com.receipttrust.common.BaseEntity;
import com.receipttrust.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receipts")
public class Receipt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    /** Receipt/invoice number from the printed receipt, kept as proof. Optional. */
    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(columnDefinition = "text")
    private String notes;

    /** Optional: null when the receipt was created without an uploaded image. */
    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "image_content_type")
    private String imageContentType;

    @Column(nullable = false)
    private boolean finalized;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptItem> items = new ArrayList<>();

    protected Receipt() {
    }

    public Receipt(User owner, String storeName, LocalDate purchaseDate, String notes,
                   String imagePath, String imageContentType) {
        this.owner = owner;
        this.storeName = storeName;
        this.purchaseDate = purchaseDate;
        this.notes = notes;
        this.imagePath = imagePath;
        this.imageContentType = imageContentType;
        this.finalized = false;
    }

    /** Whether this receipt has a stored proof image. */
    public boolean hasImage() {
        return imagePath != null;
    }

    /** Clears the stored image reference (used when the image file is expired/deleted). */
    public void clearImage() {
        this.imagePath = null;
        this.imageContentType = null;
    }

    public User getOwner() {
        return owner;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }

    public List<ReceiptItem> getItems() {
        return items;
    }
}

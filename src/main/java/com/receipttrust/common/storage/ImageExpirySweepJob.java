package com.receipttrust.common.storage;

import com.receipttrust.config.ImageExpiryProperties;
import com.receipttrust.receipt.Receipt;
import com.receipttrust.receipt.ReceiptRepository;
import com.receipttrust.user.User;
import com.receipttrust.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Deletes expired uploaded images when {@code receipttrust.image-expiry.enabled}
 * is true (testing phase). When false (live), images are retained.
 *
 * Receipt images expire after receiptTtlSeconds (default 60s); profile images
 * after profileTtlSeconds (default 3600s). When a profile image is removed, the
 * user's profile_image_path is cleared and the UI falls back to the built-in
 * initials avatar.
 *
 * Note: runs only while the service is awake. On free hosting the disk is
 * ephemeral, so a restart clears everything regardless.
 */
@Component
public class ImageExpirySweepJob {

    private static final Logger log = LoggerFactory.getLogger(ImageExpirySweepJob.class);

    private final ImageExpiryProperties properties;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public ImageExpirySweepJob(ImageExpiryProperties properties,
                               ReceiptRepository receiptRepository,
                               UserRepository userRepository,
                               FileStorageService fileStorageService) {
        this.properties = properties;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    /** Runs every 30 seconds; no-op unless expiry is enabled. */
    @Scheduled(fixedDelayString = "${receipttrust.image-expiry.sweep-interval-ms:30000}")
    @Transactional
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();

        // Receipt images
        Instant receiptCutoff = now.minusSeconds(properties.getReceiptTtlSeconds());
        List<Receipt> expiredReceipts = receiptRepository.findWithImageOlderThan(receiptCutoff);
        for (Receipt r : expiredReceipts) {
            fileStorageService.deleteReceiptImage(r.getImagePath());
            r.clearImage();
            receiptRepository.save(r);
        }

        // Profile images
        Instant profileCutoff = now.minusSeconds(properties.getProfileTtlSeconds());
        List<User> expiredProfiles = userRepository.findProfileImageOlderThan(profileCutoff);
        for (User u : expiredProfiles) {
            fileStorageService.deleteProfileImage(u.getProfileImagePath());
            u.setProfileImagePath(null); // also nulls uploadedAt via setter
            userRepository.save(u);
        }

        if (!expiredReceipts.isEmpty() || !expiredProfiles.isEmpty()) {
            log.info("Image expiry sweep removed {} receipt image(s), {} profile image(s)",
                    expiredReceipts.size(), expiredProfiles.size());
        }
    }
}

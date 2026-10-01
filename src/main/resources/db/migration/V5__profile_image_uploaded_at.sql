-- Track when a profile image was uploaded, for expiry sweeps.
ALTER TABLE users ADD COLUMN profile_image_uploaded_at TIMESTAMPTZ;

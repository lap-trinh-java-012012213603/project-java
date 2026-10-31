package com.analoghub.platform.common.exception;

public class ArchiveStorageException extends BusinessException {

    public ArchiveStorageException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ArchiveStorageException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public ArchiveStorageException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }

    public static ArchiveStorageException archiveNotUnlocked() {
        return new ArchiveStorageException(ErrorCode.ARCHIVE_NOT_UNLOCKED);
    }

    public static ArchiveStorageException rollNotInScanningState(Object currentStatus) {
        return new ArchiveStorageException(
                ErrorCode.ROLL_NOT_IN_SCANNING_STATE,
                String.format("Cuộn phim đang ở trạng thái [%s], cần ở trạng thái SCANNING hoặc RESCAN_REQUIRED để tải ảnh lên", currentStatus)
        );
    }

    public static ArchiveStorageException batchLimitExceeded(int count, int maxAllowed) {
        return new ArchiveStorageException(
                ErrorCode.BATCH_UPLOAD_LIMIT_EXCEEDED,
                String.format("Bạn đang tải lên %d ảnh, vượt quá giới hạn tối đa %d kiểu/cuộn", count, maxAllowed)
        );
    }

    public static ArchiveStorageException unsupportedFormat(String contentType) {
        return new ArchiveStorageException(
                ErrorCode.UNSUPPORTED_IMAGE_FORMAT,
                String.format("Định dạng file [%s] không được hỗ trợ (Chỉ nhận JPEG, PNG, WEBP, TIFF)", contentType)
        );
    }

    public static ArchiveStorageException fileSizeExceeded(String fileName, long sizeInBytes) {
        double sizeMb = sizeInBytes / (1024.0 * 1024.0);
        return new ArchiveStorageException(
                ErrorCode.FILE_SIZE_LIMIT_EXCEEDED,
                String.format("Ảnh [%s] có dung lượng %.2f MB, vượt quá giới hạn 15MB", fileName, sizeMb)
        );
    }

    public static ArchiveStorageException presignFailed(Throwable cause) {
        return new ArchiveStorageException(
                ErrorCode.STORAGE_PRESIGN_FAILED,
                ErrorCode.STORAGE_PRESIGN_FAILED.getMessage(),
                cause
        );
    }

    public static ArchiveStorageException objectNotFoundOnStorage(String objectKey) {
        return new ArchiveStorageException(
                ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                String.format("Không tìm thấy file [%s] trên R2/MinIO Storage", objectKey)
        );
    }
}
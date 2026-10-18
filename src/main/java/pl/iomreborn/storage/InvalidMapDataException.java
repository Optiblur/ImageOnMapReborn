package pl.iomreborn.storage;

/** Thrown when an entry of a map store file cannot be understood. */
public final class InvalidMapDataException extends Exception {

    public InvalidMapDataException(String message) {
        super(message);
    }
}

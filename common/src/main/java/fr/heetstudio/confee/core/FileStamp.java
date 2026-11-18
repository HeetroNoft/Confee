package fr.heetstudio.confee.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Last-modified time and size of a file, to notice changes made outside Confee (spec 14).
 * <p>Internal: may change without notice.</p>
 */
public record FileStamp(long modified, long size) {

    public static final FileStamp MISSING = new FileStamp(-1, -1);

    public static FileStamp of(Path file) {
        try {
            if (!Files.exists(file)) {
                return MISSING;
            }
            return new FileStamp(Files.getLastModifiedTime(file).toMillis(), Files.size(file));
        } catch (IOException e) {
            return MISSING;
        }
    }
}

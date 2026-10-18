package edu.courseassist.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Content-addressed fixed copies. Only these copies are passed to the PDF adapter. */
public final class ManagedPdfStore {
    public record Blob(String md5, long size) {}
    private final Path directory;
    public ManagedPdfStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Blob copy(Path selected) throws IOException {
        if (!selected.isAbsolute()) throw new IllegalArgumentException("Selected PDF must be absolute");
        Files.createDirectories(directory);
        Path staging = Files.createTempFile(directory, "copy-", ".staging");
        try {
            Files.copy(selected, staging, StandardCopyOption.REPLACE_EXISTING);
            try (var channel = FileChannel.open(staging, StandardOpenOption.WRITE)) { channel.force(true); }
            String md5 = digest(staging); long size = Files.size(staging);
            synchronized (this) {
                Path destination = path(md5);
                if (Files.exists(destination)) verify(new Blob(md5, size));
                else Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            return new Blob(md5, size);
        } finally { Files.deleteIfExists(staging); }
    }
    public Path path(String md5) {
        if (!md5.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid blob MD5");
        return directory.resolve(md5 + ".pdf");
    }
    public synchronized void verify(Blob blob) throws IOException {
        Path file = path(blob.md5());
        if (!Files.isRegularFile(file) || Files.size(file) != blob.size() || !digest(file).equals(blob.md5()))
            throw new IOException("Managed PDF missing or damaged: " + blob.md5());
    }
    public synchronized void delete(String md5) throws IOException { Files.deleteIfExists(path(md5)); }
    public synchronized Set<String> files() throws IOException {
        if (!Files.exists(directory)) return Set.of();
        Set<String> result = new HashSet<>();
        try (var paths = Files.list(directory)) {
            paths.map(p -> p.getFileName().toString()).filter(n -> n.matches("[0-9a-f]{32}\\.pdf"))
                    .forEach(n -> result.add(n.substring(0, 32)));
        }
        return Set.copyOf(result);
    }
    private static String digest(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("MD5");
            try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) { in.transferTo(java.io.OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

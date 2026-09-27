package tech.wenisch.adapterfs.fs;

import java.time.Instant;

public record FileEntry(String name, String path, boolean directory, long size, Instant modified) {}

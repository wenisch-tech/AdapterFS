package tech.wenisch.adapterfs.fs;

import java.util.List;

public record FilePage(String export, String path, List<FileEntry> entries, int page, int size, long total) {}

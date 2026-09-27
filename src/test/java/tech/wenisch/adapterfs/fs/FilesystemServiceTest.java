package tech.wenisch.adapterfs.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.wenisch.adapterfs.config.AdapterFsProperties;

class FilesystemServiceTest {
    @TempDir Path temporary;
    Path root; FilesystemService service;
    @BeforeEach void setUp() throws Exception {
        root=temporary.resolve("export");
        AdapterFsProperties properties=new AdapterFsProperties();
        properties.setExports(List.of(new AdapterFsProperties.Export("files",root,false)));
        service=new FilesystemService(properties);service.initialize();
    }
    @Test void writesListsMovesAndDeletesUnicodeFiles() throws Exception {
        service.mkdir("files","documents");
        service.upload("files","documents/Grüße.txt",new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),false);
        assertThat(service.list("files","documents",0,20).entries()).extracting(FileEntry::name).containsExactly("Grüße.txt");
        service.move("files","documents/Grüße.txt","documents/renamed.txt",false);
        assertThat(Files.readString(root.resolve("documents/renamed.txt"))).isEqualTo("hello");
        service.delete("files","documents"); assertThat(root.resolve("documents")).doesNotExist();
    }
    @Test void seesFilesCreatedOutsideApplication() throws Exception {
        Files.writeString(root.resolve("external.txt"),"visible");
        assertThat(service.list("files","",0,20).entries()).extracting(FileEntry::name).contains("external.txt");
    }
    @Test void refusesTraversalAndSymlinks() throws Exception {
        Path outside=temporary.resolve("outside");Files.createDirectory(outside);Files.createSymbolicLink(root.resolve("escape"),outside);
        assertThatThrownBy(()->service.list("files","../outside",0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.list("files","escape",0,20)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refusesOverwriteUnlessRequested() throws Exception {
        service.upload("files","item",new ByteArrayInputStream(new byte[]{1}),false);
        assertThatThrownBy(()->service.upload("files","item",new ByteArrayInputStream(new byte[]{2}),false)).isInstanceOf(FilesystemService.FileAlreadyExistsException.class);
        service.upload("files","item",new ByteArrayInputStream(new byte[]{2}),true);assertThat(Files.readAllBytes(root.resolve("item"))).containsExactly(2);
    }
    @Test void enforcesReadOnlyExport() throws Exception {
        AdapterFsProperties properties=new AdapterFsProperties();properties.setExports(List.of(new AdapterFsProperties.Export("locked",temporary.resolve("locked"),true)));
        FilesystemService locked=new FilesystemService(properties);locked.initialize();
        assertThatThrownBy(()->locked.mkdir("locked","new")).isInstanceOf(FilesystemService.ReadOnlyException.class);
    }
}

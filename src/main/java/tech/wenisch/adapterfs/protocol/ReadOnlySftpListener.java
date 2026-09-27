package tech.wenisch.adapterfs.protocol;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;

final class ReadOnlySftpListener implements SftpEventListener {
    private static final Set<StandardOpenOption> MUTATING = Set.of(StandardOpenOption.WRITE, StandardOpenOption.APPEND,
            StandardOpenOption.CREATE, StandardOpenOption.CREATE_NEW, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DELETE_ON_CLOSE);
    private final Set<String> exports;
    private final Set<String> readOnly;
    ReadOnlySftpListener(Set<String> exports,Set<String> readOnly) { this.exports=exports;this.readOnly = readOnly; }
    @Override public void opening(ServerSession session, String remoteHandle, Handle handle) throws IOException {
        rejectNestedLinks(handle.getFile());
        if (handle instanceof FileHandle file && file.getOpenOptions().stream().anyMatch(MUTATING::contains)) deny(file.getFile());
    }
    @Override public void readingEntries(ServerSession session,String handle,DirectoryHandle directory)throws IOException{rejectNestedLinks(directory.getFile());}
    @Override public void reading(ServerSession session,String handle,FileHandle file,long offset,byte[] data,int dataOffset,int dataLen)throws IOException{rejectNestedLinks(file.getFile());}
    @Override public void writing(ServerSession session,String handle,FileHandle file,long offset,byte[] data,int dataOffset,int dataLen)throws IOException{deny(file.getFile());}
    @Override public void creating(ServerSession session,Path path,Map<String,?> attributes)throws IOException{deny(path);}
    @Override public void moving(ServerSession session,Path source,Path target,Collection<java.nio.file.CopyOption> options)throws IOException{deny(source);deny(target);}
    @Override public void removing(ServerSession session,Path path,boolean directory)throws IOException{deny(path);}
    @Override public void linking(ServerSession session,Path source,Path target,boolean symbolic)throws IOException{throw new AccessDeniedException(target.toString(),null,"Links are disabled");}
    @Override public void modifyingAttributes(ServerSession session,Path path,Map<String,?> attributes)throws IOException{deny(path);}
    private void deny(Path path)throws IOException{for(Path component:path.normalize())if(exports.contains(component.toString())){if(readOnly.contains(component.toString()))throw new AccessDeniedException(path.toString(),null,"Export is read-only");return;}}
    private void rejectNestedLinks(Path path)throws IOException{
        Path current=path.getRoot();boolean insideExport=false;
        for(Path component:path.normalize()){
            current=current==null?component:current.resolve(component);
            if(insideExport&&java.nio.file.Files.isSymbolicLink(current))throw new AccessDeniedException(path.toString(),null,"Symbolic links are disabled");
            if(!insideExport&&exports.contains(component.toString()))insideExport=true;
        }
    }
}

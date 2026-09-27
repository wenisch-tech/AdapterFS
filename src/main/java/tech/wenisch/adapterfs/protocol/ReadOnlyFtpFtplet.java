package tech.wenisch.adapterfs.protocol;

import java.io.IOException;
import java.util.Set;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;

final class ReadOnlyFtpFtplet implements Ftplet {
    private static final Set<String> MUTATING=Set.of("APPE","DELE","MKD","RMD","RNFR","RNTO","SITE","STOR","STOU");
    private final Set<String> readOnly;
    ReadOnlyFtpFtplet(Set<String> readOnly){this.readOnly=readOnly;}
    @Override public FtpletResult beforeCommand(FtpSession session,FtpRequest request)throws FtpException,IOException{
        if(!MUTATING.contains(request.getCommand().toUpperCase())||!session.isLoggedIn())return FtpletResult.DEFAULT;
        String working=session.getFileSystemView().getWorkingDirectory().getAbsolutePath();String argument=request.hasArgument()?request.getArgument():"";
        String candidate=argument.startsWith("/")?argument:working+"/"+argument;
        String[] parts=candidate.replaceFirst("^/+","").split("/",2);
        if(parts.length>0&&readOnly.contains(parts[0])){session.write(new DefaultFtpReply(550,"Export is read-only"));return FtpletResult.SKIP;}
        return FtpletResult.DEFAULT;
    }
    @Override public void init(FtpletContext context){}
    @Override public void destroy(){}
    @Override public FtpletResult afterCommand(FtpSession session,FtpRequest request,FtpReply reply){return FtpletResult.DEFAULT;}
    @Override public FtpletResult onConnect(FtpSession session){return FtpletResult.DEFAULT;}
    @Override public FtpletResult onDisconnect(FtpSession session){return FtpletResult.DEFAULT;}
}

package edu.courseassist.bootstrap;

import edu.courseassist.core.session.SessionData.Location;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinDef.DWORD;

/** Real paths collapse aliases. Native directory identity proves same-volume moves; a copied
 * project UUID, missing old path or equal content is never used as continuity evidence. */
public final class ProjectLocations {
    private ProjectLocations(){}
    public static Location read(Path path) throws IOException {
        Path real=path.toRealPath();var attributes=Files.readAttributes(real,BasicFileAttributes.class);
        if(!attributes.isDirectory())throw new IOException("Project location is not a directory");
        // Windows JDK's BasicFileAttributes.fileKey() is null. Query the OS handle through
        // JNA already bundled by the IDE, with no helper process or extra native distribution.
        if(System.getProperty("os.name").startsWith("Windows"))
            return new Location(real.toString(),windowsIdentity(real)+"|created:"+attributes.creationTime());
        if(attributes.fileKey()==null)throw new IOException("目录身份不可验证，请显式选择新的项目身份");
        var volume=Files.getFileStore(real);
        return new Location(real.toString(),volume.name()+"|"+volume.type()+"|"+attributes.fileKey());
    }
    private static String windowsIdentity(Path path) throws IOException {
        var api=Kernel32.INSTANCE;
        var handle=api.CreateFile(path.toString(),WinNT.FILE_READ_ATTRIBUTES,
                WinNT.FILE_SHARE_READ|WinNT.FILE_SHARE_WRITE|WinNT.FILE_SHARE_DELETE,null,
                WinNT.OPEN_EXISTING,WinNT.FILE_FLAG_BACKUP_SEMANTICS,null);
        if(handle==null||WinBase.INVALID_HANDLE_VALUE.equals(handle))throw new IOException("Cannot open directory identity handle: "+api.GetLastError());
        IOException failure=null;
        try {
            var info=new WinBase.FILE_ID_INFO();
            // FILE_INFO_BY_HANDLE_CLASS.FileIdInfo = 0x12 (Windows SDK).
            if(!api.GetFileInformationByHandleEx(handle,0x12,info.getPointer(),new DWORD(info.size())))
                throw new IOException("Cannot query native directory identity: "+api.GetLastError());
            info.read();byte[] id=new byte[16];for(int n=0;n<id.length;n++)id[n]=info.FileId.Identifier[n].byteValue();
            return "windows-file-id:"+Long.toUnsignedString(info.VolumeSerialNumber)+":"+java.util.HexFormat.of().formatHex(id);
        }catch(IOException error){failure=error;throw error;}
        finally{if(!api.CloseHandle(handle)){IOException close=new IOException("Cannot close directory identity handle: "+api.GetLastError());if(failure!=null)failure.addSuppressed(close);else throw close;}}
    }
}

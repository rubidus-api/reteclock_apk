package com.reteclock;

import android.app.backup.*;
import java.io.*;

/** Full backup keeps the ordinary app data but never the local administrator verifier or journal. */
public final class WebBackupAgent extends BackupAgentHelper {
    @Override public void onFullBackup(FullBackupDataOutput data)throws IOException {
        File root=getFilesDir().getParentFile();
        File[] prefs=new File(root,"shared_prefs").listFiles();
        if(prefs!=null)for(File f:prefs)if(!f.getName().startsWith("web_admin.xml"))fullBackupFile(f,data);
        File[] files=getFilesDir().listFiles();
        if(files!=null)for(File f:files)if(!f.getName().startsWith("web-import-journal"))backupTree(f,data);
        File[] databases=new File(root,"databases").listFiles();
        if(databases!=null)for(File f:databases)fullBackupFile(f,data);
    }
    private void backupTree(File file,FullBackupDataOutput data)throws IOException {
        if(file.isFile()){fullBackupFile(file,data);return;}File[] list=file.listFiles();if(list!=null)for(File child:list)backupTree(child,data);
    }
}

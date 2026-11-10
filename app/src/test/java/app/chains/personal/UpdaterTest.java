package app.chains.personal;

import android.content.pm.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.*;
import java.security.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=27)
public class UpdaterTest {
    String json(int code,String url,String hash,long size) throws Exception {return new JSONObject().put("versionCode",code).put("versionName","1.1.0").put("apkUrl",url).put("sha256",hash).put("size",size).toString();}
    String url="https://github.com/rnghx/chains-android/releases/download/v1.1.0/app.apk";
    String hash="0".repeat(64);
    @Test public void comparesIntegerVersions() throws Exception {Updater.Release r=new Updater.Release(json(10,url,hash,100));assertTrue(r.newer(9));assertFalse(r.newer(10));assertFalse(r.newer(11));}
    @Test public void rejectsUnsafeManifestAndRedirectHosts() throws Exception {
        for(String u:new String[]{"http://github.com/rnghx/chains-android/releases/download/v1.1.0/app.apk","https://example.com/app.apk","https://github.com/rnghx/money-android/releases/download/v1.1.0/app.apk",url+"?redirect=1","https://user@github.com/rnghx/chains-android/releases/download/v1.1.0/app.apk"}){
            try{new Updater.Release(json(2,u,hash,100));fail(u);}catch(IOException expected){}
        }
        assertFalse(Updater.allowed(new URL("http://github.com/test")));assertFalse(Updater.allowed(new URL("https://github.com.evil.com/test")));assertFalse(Updater.allowed(new URL("https://github.com:444/test")));assertTrue(Updater.allowed(new URL("https://release-assets.githubusercontent.com/test")));
    }
    @Test public void rejectsBadHashSizeAndInterruptedDownloads() throws Exception {
        File f=File.createTempFile("chains-test",".apk");try{try(OutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3});}String actual=Updater.hex(MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3}));Updater.verifyPayload(f,new Updater.Release(json(2,url,actual,3)));
            for(Updater.Release bad:new Updater.Release[]{new Updater.Release(json(2,url,hash,3)),new Updater.Release(json(2,url,actual,4))}){try{Updater.verifyPayload(f,bad);fail();}catch(GeneralSecurityException expected){}}
            try{Updater.readManifest(new InputStream(){int i=0;public int read()throws IOException{if(++i>10)throw new IOException("interrupted");return 'a';}});fail();}catch(IOException expected){}
        }finally{f.delete();}
    }
    PackageInfo info(String pkg,int code,byte signer){PackageInfo p=new PackageInfo();p.packageName=pkg;p.versionCode=code;p.signatures=new android.content.pm.Signature[]{new android.content.pm.Signature(new byte[]{signer})};return p;}
    @Test public void rejectsWrongPackageSignatureAndVersion() throws Exception {
        Updater.Release r=new Updater.Release(json(2,url,hash,100));PackageInfo installed=info("app.chains.personal",1,(byte)1);Updater.validateMetadata(info("app.chains.personal",2,(byte)1),installed,r);
        for(PackageInfo bad:new PackageInfo[]{info("wrong.package",2,(byte)1),info("app.chains.personal",2,(byte)2),info("app.chains.personal",1,(byte)1),null}){try{Updater.validateMetadata(bad,installed,r);fail();}catch(GeneralSecurityException expected){}}
    }
    @Test public void noUpdateNetworkOrInstallerOnStartup(){
        try(var ctrl=Robolectric.buildActivity(MainActivity.class).setup()){assertNull(Shadows.shadowOf(ctrl.get()).getNextStartedActivity());assertFalse(ctrl.get().updater.busy.get());assertNull(ctrl.get().updater.pendingFile);}
    }
    @Test public void cancelledInstallDoesNotMutateData() throws Exception {
        try(var ctrl=Robolectric.buildActivity(MainActivity.class).setup()){MainActivity a=ctrl.get();Habit h=new Habit();h.name="Test";a.store.save(h);String before=a.store.exportJson();a.onActivityResult(103,0,null);assertEquals(before,a.store.exportJson());assertNull(Shadows.shadowOf(a).getNextStartedActivity());}
    }
    @Test public void providerRejectsArbitraryFilesAndWrites() throws Exception {
        UpdateProvider p=Robolectric.buildContentProvider(UpdateProvider.class).create().get();
        try{p.openFile(Uri.parse("content://app.chains.personal.updates/../chains.db"),"r");fail();}catch(FileNotFoundException expected){}
        try{p.openFile(UpdateProvider.URI,"w");fail();}catch(FileNotFoundException expected){}
    }
}

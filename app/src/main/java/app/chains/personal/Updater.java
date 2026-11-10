package app.chains.personal;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class Updater {
    static final String REPOSITORY="rnghx/chains-android";
    static final String MANIFEST="https://github.com/"+REPOSITORY+"/releases/latest/download/update.json";
    static final long MAX_APK=64L*1024*1024;
    final MainActivity activity;
    final AtomicBoolean busy=new AtomicBoolean(false);
    File pendingFile; Release pendingRelease;
    Updater(MainActivity a){activity=a;}

    static final class Release {
        final int code;final String name,url,hash;final long size;
        Release(String raw) throws Exception {
            JSONObject json=new JSONObject(raw);code=json.getInt("versionCode");name=json.getString("versionName");url=json.getString("apkUrl");hash=json.getString("sha256").toLowerCase(Locale.ROOT);size=json.getLong("size");
            URI uri=new URI(url);
            if(code<1||!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!hash.matches("[a-f0-9]{64}")||size<1||size>MAX_APK||!"https".equals(uri.getScheme())||!"github.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().matches("/"+REPOSITORY+"/releases/download/[a-zA-Z0-9._-]+/app\\.apk")) throw new IOException("Invalid update manifest");
        }
        boolean newer(long current){return code>current;}
    }
    interface StreamReader<T>{T read(InputStream in) throws Exception;}
    static boolean allowed(URL url){return "https".equals(url.getProtocol())&&(url.getPort()==-1||url.getPort()==443)&&url.getUserInfo()==null&&Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(url.getHost());}
    static <T>T fetch(String address,StreamReader<T> reader) throws Exception {
        URL url=new URL(address);
        for(int redirects=0;redirects<6;redirects++){
            if(!allowed(url))throw new IOException("Blocked update host");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Chains-Android");c.setRequestProperty("Accept-Encoding","identity");
            try{int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String target=c.getHeaderField("Location");if(target==null)throw new IOException("Missing redirect");url=new URL(url,target);continue;}if(status!=200)throw new IOException("HTTP "+status);try(InputStream in=c.getInputStream()){return reader.read(in);}}finally{c.disconnect();}
        }
        throw new IOException("Too many redirects");
    }
    static String readManifest(InputStream in) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Manifest too large");out.write(b,0,n);}return out.toString(StandardCharsets.UTF_8.name());}
    static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    static Set<String> certificates(PackageInfo p) throws Exception {
        android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;
        if(signatures==null||signatures.length==0)throw new GeneralSecurityException("Missing APK certificate");
        Set<String> result=new HashSet<>();for(android.content.pm.Signature s:signatures)result.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return result;
    }
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static void validateMetadata(PackageInfo apk,PackageInfo installed,Release r) throws Exception {
        if(apk==null||!installed.packageName.equals(apk.packageName)||version(apk)!=r.code||!r.newer(version(installed))||!certificates(apk).equals(certificates(installed)))throw new GeneralSecurityException("APK identity mismatch");
    }
    static void verifyPayload(File f,Release r) throws Exception {
        if(f.length()!=r.size)throw new GeneralSecurityException("APK size mismatch");MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}if(!hex(digest.digest()).equals(r.hash))throw new GeneralSecurityException("APK hash mismatch");
    }
    void verify(File file,Release release) throws Exception {
        verifyPayload(file,release);PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        validateMetadata(pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),pm.getPackageInfo(activity.getPackageName(),flags),release);
    }
    void ui(Runnable r){activity.runOnUiThread(()->{if(!activity.isDestroyed()&&!activity.isFinishing())r.run();});}
    void check(){
        if(!busy.compareAndSet(false,true))return;
        AlertDialog progress=new AlertDialog.Builder(activity).setMessage("Проверка обновлений…").setCancelable(false).show();
        new Thread(()->{
            try{Release release=new Release(fetch(MANIFEST,Updater::readManifest));long current=version(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0));ui(()->{progress.dismiss();busy.set(false);if(!release.newer(current))new AlertDialog.Builder(activity).setMessage("Установлена последняя версия").setPositiveButton("Закрыть",null).show();else new AlertDialog.Builder(activity).setTitle("chains "+release.name).setMessage("Доступно обновление").setNegativeButton("Позже",null).setPositiveButton("Скачать",(d,w)->download(release)).show();});}
            catch(Exception e){ui(()->{progress.dismiss();busy.set(false);activity.error("Не удалось проверить обновление. Попробуй позже.");});}
        },"chains-update-check").start();
    }
    void download(Release release){
        if(!busy.compareAndSet(false,true))return;
        AtomicBoolean cancelled=new AtomicBoolean(false);AlertDialog progress=new AlertDialog.Builder(activity).setMessage("Загрузка обновления…").setNegativeButton("Отмена",(d,w)->cancelled.set(true)).setOnCancelListener(d->cancelled.set(true)).show();
        new Thread(()->{File part=new File(activity.getCacheDir(),"update.part");
            try{fetch(release.url,in->{try(OutputStream out=new FileOutputStream(part)){byte[] b=new byte[16384];long total=0;int n;while((n=in.read(b))!=-1){if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>release.size||total>MAX_APK)throw new IOException("APK too large");out.write(b,0,n);}}return null;});if(cancelled.get())throw new InterruptedIOException();verify(part,release);File apk=new File(activity.getCacheDir(),"update.apk");if(!part.renameTo(apk))throw new IOException("Cannot stage APK");ui(()->{progress.dismiss();busy.set(false);if(cancelled.get())return;pendingFile=apk;pendingRelease=release;new AlertDialog.Builder(activity).setTitle("chains "+release.name).setMessage("Обновление готово").setNegativeButton("Позже",(d,w)->{pendingFile=null;pendingRelease=null;}).setPositiveButton("Установить",(d,w)->installPending()).show();});}
            catch(Exception e){part.delete();ui(()->{progress.dismiss();busy.set(false);if(!cancelled.get())activity.error("Не удалось загрузить или проверить APK. Попробуй ещё раз.");});}
        },"chains-update-download").start();
    }
    void installPending(){
        if(pendingFile==null||pendingRelease==null)return;
        try{
            verify(pendingFile,pendingRelease);
            if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
                activity.startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),103);return;
            }
            Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateProvider.URI,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.setClipData(ClipData.newRawUri("chains update",UpdateProvider.URI));activity.startActivity(install);pendingFile=null;pendingRelease=null;
        }catch(Exception e){pendingFile=null;pendingRelease=null;activity.error("Не удалось открыть установку обновления");}
    }
}

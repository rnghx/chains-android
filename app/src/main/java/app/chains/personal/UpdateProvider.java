package app.chains.personal;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Exposes exactly one verified cached APK, only through a temporary read grant. */
public final class UpdateProvider extends ContentProvider {
    static final Uri URI=Uri.parse("content://app.chains.personal.updates/update.apk");
    public boolean onCreate(){return true;}
    private File file(Uri uri) throws FileNotFoundException {
        if(!URI.equals(uri))throw new FileNotFoundException();
        File f=new File(getContext().getCacheDir(),"update.apk");
        if(!f.isFile())throw new FileNotFoundException();return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public String getType(Uri uri){return URI.equals(uri)?"application/vnd.android.package-archive":null;}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File f=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(columns);Object[] values=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]="chains-update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=f.length();}c.addRow(values);return c;}catch(FileNotFoundException e){return null;}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}

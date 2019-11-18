package com.htetznaing.app_updater;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static android.content.Context.DOWNLOAD_SERVICE;

public class AppUpdater {
    private Activity activity;
    private String downloadPath;
    private DownloadManager mDownloadManager;
    private long mDownloadedFileID;
    private String download = null;
    private String versionName = null;
    private boolean uninstall = false,force=true;
    private String json_url;
    private TedPermission.Builder permission;
    private MaterialStyledDialog.Builder builder;
    private String url,fileName;
    private boolean showMessage;
    private ProgressDialog progressDialog;
    @RequiresPermission(Manifest.permission.INTERNET)
    public AppUpdater(Activity activity,String json_url) {
        this.json_url = json_url;
        this.activity = activity;

        progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage("Checking...");

        builder = new MaterialStyledDialog.Builder(activity);
        mDownloadManager = (DownloadManager) activity.getSystemService(DOWNLOAD_SERVICE);
        downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()+"/";
        PermissionListener permissionlistener = new PermissionListener() {
            @Override
            public void onPermissionGranted() {
                dlFile(url,fileName);
            }

            @Override
            public void onPermissionDenied(List<String> deniedPermissions) {
                permission.check();
            }
        };
        permission = TedPermission.with(activity);
        permission.setPermissionListener(permissionlistener)
                .setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public void check(boolean showMessage){
        this.showMessage=showMessage;
        checkUpdate();
    }

    private boolean checkPermissions() {
        int storage = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (storage != PackageManager.PERMISSION_GRANTED) {
            permission.check();
            return false;
        }

        File n = new File(downloadPath);
        if (!n.exists()){
            n.mkdirs();
        }
        return true;
    }

    private void checkUpdate() {
        new AsyncTask<Void,Void,String>(){

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (showMessage){
                    progressDialog.show();
                }
            }

            @Override
            protected String doInBackground(Void... voids) {
                String s = null;
                try {
                    Document document = Jsoup.connect(json_url).get();
                    if (json_url.contains("blogspot.com") || document.hasClass("post-body entry-content")) {
                        //Blogspot Link
                        s = document.getElementsByClass("post-body entry-content").get(0).text();
                    }else {
                        s = document.text();
                    }
                    assert s != null;
                    int start = s.indexOf("{");
                    int end = s.lastIndexOf("}")+1;
                    s = s.substring(start,end);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return s;
            }


            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                if (showMessage){
                    progressDialog.dismiss();
                }
                if (s!=null){
                    letCheck(s);
                }
            }
        }.execute();
    }

    private void letCheck(String response){
        UpdateModel data = new UpdateModel();
        if (response!=null) {
            try {
                JSONObject object = new JSONObject(response);
                data.setVersionName(object.getString("versionName"));
                data.setTitle(object.getString("title"));
                data.setMessage(object.getString("message"));
                data.setDownload(object.getString("download"));
                data.setPlaystore(object.getString("playstore"));
                data.setUninstall(object.getBoolean("uninstall"));
                data.setVersionCode(object.getInt("versionCode"));
                data.setForce(object.getBoolean("force"));

                if (object.has("what")) {
                    JSONObject what = object.getJSONObject("what");
                    data.setAll(what.getBoolean("all"));
                    JSONArray versions = what.getJSONArray("version");

                    int v_int [] = new int[versions.length()];
                    for (int i=0;i<versions.length();i++){
                        v_int[i] = versions.getInt(i);
                    }
                    data.setVersions(v_int);

                    JSONArray models = what.getJSONArray("model");
                    String models_string [] = new String[models.length()];
                    for (int i=0;i<models.length();i++){
                        models_string[i] = models.getString(i);
                    }
                    data.setModels(models_string);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        if (data!=null){
            final String title = data.getTitle();
            final String message = data.getMessage();
            final String playStore = data.getPlaystore();
            uninstall = data.isUninstall();
            int versionCode = data.getVersionCode();
            versionName = data.getVersionName();
            download = data.getDownload();
            force = data.isForce();

            PackageManager manager = activity.getPackageManager();
            PackageInfo info;
            int currentVersion = 0;
            try {
                info = manager.getPackageInfo(activity.getPackageName(), 0);
                currentVersion = info.versionCode;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            if (versionCode ==currentVersion || versionCode <currentVersion){
                if (showMessage) {
                    builder.setTitle("Congratulations!")
                            .setDescription("You are on latest version!")
                            .setStyle(Style.HEADER_WITH_ICON)
                            .setIcon(R.drawable.ic_emotion)
                            .withDialogAnimation(true)
                            .setPositiveText("OK");
                    builder.show();
                }
            }else{
                if (data.isAll()){
                    letUpdate(title,message,playStore);
                }else {
                    String my_model = Build.MANUFACTURER.toLowerCase();
                    int my_version = Build.VERSION.SDK_INT;
                    boolean match_model = false,match_version=false;

                    for (String string:data.getModels()){
                        if (my_model.equalsIgnoreCase(string)){
                            match_model = true;
                        }
                    }


                    for (int i:data.getVersions()){
                        if (my_version==i){
                            match_version = true;
                        }
                    }

                    if (match_model && match_version){
                        letUpdate(title,message,playStore);
                    }
                }
            }
        }
    }

    private void letUpdate(final String title, final String message, final String playStore){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.setTitle(title)
                        .setDescription(message)
                        .setStyle(Style.HEADER_WITH_ICON)
                        .setCancelable(!force)
                        .setIcon(R.drawable.ic_update)
                        .withDialogAnimation(true)
                        .setPositiveText("Download")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dlFile(download, title + versionName + ".apk");
                            }
                        })
                        .setNegativeText("Close")
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                if (!force){
                                    dialog.dismiss();
                                }else{
                                    activity.finish();
                                }

                            }
                        });

                if (playStore!=null && !playStore.isEmpty()){
                    builder.setNeutralText("Play Store");
                    builder.onNeutral(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            final String appPackageName = playStore;
                            if (uninstall){
                                uninstall();
                            }
                            try {
                                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                            } catch (android.content.ActivityNotFoundException anfe) {
                                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                            }
                        }
                    });
                }
                builder.show();
            }
        });
    }

    private void dlFile(String url, String fileName){
        this.url=url;
        this.fileName=fileName;
        if (checkPermissions()) {
            try {
                String mBaseFolderPath = downloadPath;
                if (!new File(mBaseFolderPath).exists()) {
                    new File(mBaseFolderPath).mkdir();
                }
                File myFile = new File(mBaseFolderPath + fileName);
                if (!myFile.exists()) {
                    String mFilePath = "file://" + mBaseFolderPath + fileName;
                    Uri downloadUri = Uri.parse(url);
                    DownloadManager.Request mRequest = new DownloadManager.Request(downloadUri);
                    mRequest.setDestinationUri(Uri.parse(mFilePath));
                    mRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    mDownloadedFileID = mDownloadManager.enqueue(mRequest);
                    IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                    activity.registerReceiver(downloadReceiver, filter);
                    Toast.makeText(activity, "Starting Download : " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    openFile(myFile.toString());
                }
            } catch (Exception e) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        }
    }

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //check if the broadcast message is for our enqueued download
            final Uri uri = mDownloadManager.getUriForDownloadedFile(mDownloadedFileID);
            final String apk = getRealPathFromURI(uri);
            Toast.makeText(context, "Downloaded : "+new File(apk).getName(), Toast.LENGTH_SHORT).show();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle("Download Completed!")
                    .setCancelable(false)
                    .setMessage("Please install this latest apk! ")
                    .setPositiveButton("Install Now", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (uninstall){
                                uninstall();
                            }
                            openFile(apk);
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    };

    private void uninstall(){
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:"+activity.getPackageName()));
        activity.startActivity(intent);
    }

    private void openFile(String apk){
        if(Build.VERSION.SDK_INT>=24){ try{ Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure"); m.invoke(null); }catch(Exception e){ e.printStackTrace(); } }
        Intent intent2 = new Intent(Intent.ACTION_VIEW);
        intent2.setDataAndType(Uri.parse("file://"+apk), "application/vnd.android.package-archive");
        intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Toast.makeText(activity, "Please install!!!", Toast.LENGTH_SHORT).show();
        activity.startActivity(intent2);
    }

    private String getRealPathFromURI (Uri contentUri) {
        String path = null;
        String[] proj = { MediaStore.MediaColumns.DATA };
        Cursor cursor = activity.getContentResolver().query(contentUri, proj, null, null, null);
        assert cursor != null;
        if (cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            path = cursor.getString(column_index);
        }
        cursor.close();
        return path;
    }
}

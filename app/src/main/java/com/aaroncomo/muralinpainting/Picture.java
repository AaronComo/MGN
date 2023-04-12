package com.aaroncomo.muralinpainting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.muralinpainting.R;


import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Picture extends Activity {
    private Button pictureSave, inpainting;
    private ProgressBar progressBar, progressBar1;
    private ImageView imageView;
    private Handler msgHandler;
    private int progress = 0;
    private Boolean moduleOn = false;
    public static final int CHOOSE_PHOTO = 2;
    private String realPath = null;
    private final String ip = "120.78.130.95";
    private final String port = "8005";
    private String localFile = null, remoteFile = null, fileName = null;

    @SuppressLint("HandlerLeak")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picture);

        inpainting = findViewById(R.id.inpainting);
        pictureSave = findViewById(R.id.pictureSave);
        imageView = findViewById(R.id.picture);
        progressBar = findViewById(R.id.progressBar);
        progressBar1 = findViewById(R.id.progressBar1);
        imageView.setBackgroundResource(R.drawable.new_picture);

        // 创建消息处理器，接收子线程消息，更新进度条
        msgHandler = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == 0x10) { // 更新上传进度条
                    progressBar.setProgress(progress);
                }
                else if (msg.what == 0x12) {
                    progressBar1.setVisibility(View.GONE);  // 圆形进度条结束
                    moduleOn = false;   // 复位
                    Toast.makeText(Picture.this, "修复完成！", Toast.LENGTH_SHORT).show();
                }
                else { // 0x11 上传进度条结束
                    Toast.makeText(Picture.this, "上传成功，正在修复中...", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                }
            }
        };

        // 隐藏进度条，启用部件监听
        progressBar1.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.INVISIBLE);
        buttonListener(imageView);
        buttonListener();
    }

    public void buttonListener() {
        inpainting.setOnClickListener(v -> startModule());
        pictureSave.setOnClickListener(v -> saveImage());
    }

    public void buttonListener(View view) {
        // 动态申请文件读写权限
        if (ContextCompat.checkSelfPermission(Picture.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(Picture.this, new String[] {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        view.setOnClickListener(v -> openAlbum());
    }

    private void startModule() {
        if (!moduleOn && realPath != null) {    // 防止多次创建进程
            moduleOn = true;
            progress = 0;
            progressBar.setProgress(progress);
            progressBar.setVisibility(View.VISIBLE);  // 显示进度条
            progressBar1.setVisibility(View.VISIBLE);
            new Thread(new Runnable() {     // AI模型线程
                @Override
                public void run() {
                    if (!Objects.equals(realPath, null)) {
                        uploadImage(new File(realPath));   // 获取真实路径上的文件，上传服务器
                        runBar();
                        while (!waitForResult());   // 等待模型结果
                        realPath = null;
                    }
                }

                private boolean waitForResult(){
                    SSHUtils utils = new SSHUtils();
                    utils.exec("sh /home/ServerHandler/run_module.sh"); // 调用模型
                    utils.log();
                    try {
                        Thread.sleep(40000);    // 等待模型执行，减少query
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    utils.clean();
                    try {
                        do {
                            utils.exec("ls /home/ServerHandler/output");
                            utils.log();
                            Thread.sleep(1000);
                        } while (utils.getReturnLength() == 0); // 阻塞线程，直到输出文件夹非空

                        // 下载图片
                        fileName = utils.getFile();
                        remoteFile = "/home/ServerHandler/output".concat("/").concat(fileName);
                        localFile = "/storage/emulated/0/Pictures";
                        utils.download(remoteFile, localFile);

                        // 更新ImageView
                        msgHandler.sendEmptyMessage(0x12); // 模型调用结束，通知主线程禁用圆形进度条
                        displayImage(localFile.concat("/").concat(fileName));

                        // 清理输入输出文件夹，关闭连接
                        utils.exec("rm ".concat(remoteFile));
                        utils.exec("rm ".concat("/home/generate/static/images/*"));
                        utils.closeConnection();
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }

                private void runBar() {    // 由于okhttp3的bug，回调函数总会返回timeout，只能模拟上传进度
                    for (int i = 0; i < 100; i++) {
                        try {
                            progress = i;
                            msgHandler.sendEmptyMessage(0x10);
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Toast.makeText(Picture.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                    msgHandler.sendEmptyMessage(0x11);
                }
            }).start();
        }
        else {
            if (realPath == null) {
                Toast.makeText(Picture.this, "请重新选择图像", Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(Picture.this, "模型正在使用中...", Toast.LENGTH_SHORT).show();

            }
        }
    }

    private void openAlbum() {
        realPath = null;
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO);   //打开相册
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            Toast.makeText(this, "没有选择图像！", Toast.LENGTH_SHORT).show();
            imageView.setImageBitmap(null);
            imageView.setBackgroundResource(R.drawable.new_picture);
        }
        else {
            handleImageOnKitKat(data);  // 处理图像并显示
        }
    }

    private void handleImageOnKitKat(Intent data) {
        String imagePath = null;
        Uri uri = data.getData();
        if (DocumentsContract.isDocumentUri(this, uri)) {
            // 如果是document类型的Uri，则通过document id处理
            String docId = DocumentsContract.getDocumentId(uri);
            if("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1]; // 解析出数字格式的id
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            }
            else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),
                        Long.parseLong(docId));
                imagePath = getImagePath(contentUri, null);
            }
        }
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // 如果是content类型的Uri，则使用普通方式处理
            imagePath = getImagePath(uri, null);
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // 如果是file类型的Uri，直接获取图片路径即可
            imagePath = uri.getPath();
        }
        displayImage(imagePath); // 根据图片路径显示图片
    }

    @SuppressLint("Range")
    private String getImagePath(Uri uri, String selection) {
        String path = null;
        // 通过Uri和selection来获取真实的图片路径
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void displayImage(String imagePath) {
        if (imagePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            imageView.setBackground(null);
            imageView.setImageBitmap(bitmap);
            realPath = imagePath;
        }
        else {
            Toast.makeText(this, "failed to get image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImage() {
        if (fileName != null) {
            File file = new File(localFile.concat("/").concat(fileName));
            boolean ret = file.renameTo(new File("/storage/emulated/0/DCIM/".concat(fileName).replaceFirst(".png", "").concat("_generated.png")));
            if (ret) {
                fileName = localFile = remoteFile = null;   // 清空数据
                Toast.makeText(Picture.this, "成功保存到系统相册！", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(Picture.this, "保存失败！", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(Picture.this, "图片已经保存！", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImage(File img) {
        // 创建请求体
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)//请求类型
                .addFormDataPart("name", img.getName())    // 图片名称
                .addFormDataPart("uploadfile", "uploadfile", RequestBody.create(MediaType.parse("*/*"), img)) // 第一个参数传到服务器的字段名，第二个你自己的文件名，第三个MediaType.parse("*/*")数据类型，这个是所有类型的意思
                .build();
        // 调用工具类上传图片以及参数
        HttpUtil.uploadFile("http://" + ip + ":" + port +  "/ServerHandler/upload", requestBody, new Callback() {
            //请求失败回调函数
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                System.out.println("=============");
                System.out.println("异常: ");
                e.printStackTrace();
                System.out.println("=============");
            }

            // 请求成功响应函数
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
//                showResponse();//在主线程中显示提示框
            }
        });
    }

}
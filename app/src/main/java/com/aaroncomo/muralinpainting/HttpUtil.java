package com.aaroncomo.muralinpainting;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtil {
    protected static OkHttpClient client;
    /**
     *
     * @param address  服务器地址
     * @param requestBody  请求体数据
     * @param callback  回调接口
     */
    public static void uploadFile(String address, RequestBody requestBody , okhttp3.Callback callback) {

        //发送请求
        client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)   //设置连接超时时间
                .readTimeout(1, TimeUnit.SECONDS)  //设置读取超时时间
                .build();
        Request request = new Request.Builder()
                .url(address)
                .post(requestBody)
                .build();
        client.newCall(request).enqueue(callback);
    }
}
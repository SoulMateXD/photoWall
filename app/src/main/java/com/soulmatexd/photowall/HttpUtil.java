package com.soulmatexd.photowall;

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by SoulMateXD on 2016/10/30.
 */

    //模板代码。要熟练写出来
    //虽然很蠢，改进很多。。
public class HttpUtil {
    public static String post(String url, String content){
        HttpURLConnection httpURLConnection = null;
        try {
            URL mUrl = new URL(url);
            httpURLConnection = (HttpURLConnection) mUrl.openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setConnectTimeout(10000);
            httpURLConnection.setReadTimeout(5000);

            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(content.getBytes());
            outputStream.flush();
            outputStream.close();
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == 200){
                InputStream inputStream = httpURLConnection.getInputStream();
                String response = getResponseString(inputStream);
                return response;
            }else {
                throw new NetworkErrorException("response code is" + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String get(String url){
        HttpURLConnection httpURLConnection = null;
        try {
            URL mUrl = new URL(url);
            httpURLConnection = (HttpURLConnection) mUrl.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setReadTimeout(5000);
            httpURLConnection.setConnectTimeout(5000);
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == 200){  //请求成功
                InputStream inputStream = httpURLConnection.getInputStream();
                String response = getResponseString(inputStream);
                return response;
            }else {
                Log.d("Myresponse", "response status is" + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (httpURLConnection != null)
            httpURLConnection.disconnect();
        }
        return null;
    }

    public static Bitmap getBitMap(final String url, int reqWidth, int reqHeight){
        Bitmap bitmap = null;
        HttpURLConnection httpURLConnection = null;
        try {
            URL mUrl = new URL(url);
            httpURLConnection = (HttpURLConnection) mUrl.openConnection();
            httpURLConnection.setRequestMethod("GET");
            int responseCode = httpURLConnection.getResponseCode();
            Log.e("HttpUtil", responseCode + "111");
            if (responseCode == 200){  //请求成功
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(httpURLConnection.getInputStream(), null, options);
                int inSampleSize = MyBitmapFactory.caculateInSampleSize(options, reqWidth, reqHeight);
                //decodeStream无法对同一inputstream解析两次，必须重新开一个
                httpURLConnection.disconnect();
                httpURLConnection = (HttpURLConnection) mUrl.openConnection();
                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                bitmap = BitmapFactory.decodeStream(httpURLConnection.getInputStream(), null, options);
                Log.e("HttpUtil", responseCode + "");
            }else {
                throw new NetworkErrorException("response status is" + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (httpURLConnection != null)
                httpURLConnection.disconnect();
        }
        return bitmap;
    }
    private static String getResponseString(InputStream inputStream){
        //ByteArrayOutputStream实现了一个输出流，其中的数据被写入一个 byte 数组。
        // 缓冲区会随着数据的不断写入而自动增长。可使用 toByteArray()和 toString()获取数据。
        //可以用来缓存数据，多次写入一次获取
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int len = -1;
        try {
            //SoulMateXD啊，你当初写的时候为什么会把这里写成if呢？你TM算算你找了多久
            while ((len = inputStream.read(bytes)) != -1){
                byteArrayOutputStream.write(bytes, 0, len);
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String state = byteArrayOutputStream.toString();
        try {
            byteArrayOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return state;
    }


}

class MyBitmapFactory {

    public static int getMaxMemoryKB(){
        return (int) (Runtime.getRuntime().maxMemory()/1024);
    }

    public static BitmapFactory.Options getOptions(Context context, int id){
        BitmapFactory.Options options = new BitmapFactory.Options();
        BitmapFactory.decodeResource(context.getResources(), id, options);
        return options;
    }

    //如果没想好要多宽多长，就输入0吧;
    public static int caculateInSampleSize(BitmapFactory.Options options,
                                           int reqWidth, int reqHeight){
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if ( reqHeight == 0 || reqWidth == 0){
            return inSampleSize;
        }
        if (height > reqHeight || width > reqWidth){
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize >= reqHeight) && (halfWidth/inSampleSize >= reqWidth)){
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampleBitmapFromResource(Resources res, int resId,
                                                        int width, int height){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        int inSampleSize = caculateInSampleSize(options, width, height);
        options.inSampleSize = inSampleSize;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static Bitmap decodeSampleBitmapFromSnapshot(DiskLruCache.Snapshot snapshot,
                                                        int reqWidth, int reqHeight){
        Bitmap bitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        FileInputStream inputStream = (FileInputStream) snapshot.getInputStream(0);
        try {
            FileDescriptor fileDescriptor = inputStream.getFD();
            BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
            options.inSampleSize = caculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}

class Util{
    public static File getCacheDiskDir(Context context, String uniqueName){
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()){
            cachePath = context.getExternalCacheDir().getPath();
        }else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    //这个是抄的， 等有时间了再研究
    //将String 转换为MD5码的形式，便于做key
    public static String hashKeyFromString(String string){
        String cacheKey;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(string.getBytes());
            cacheKey = bytesToHexString(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(string.hashCode());
        }
        return cacheKey;
    }

    private static String bytesToHexString(byte[] bytes){
        StringBuilder stringBuilder = new StringBuilder();
        for (int i=0; i<bytes.length; i++){
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1){
                stringBuilder.append('0');
            }
            stringBuilder.append(hex);
        }
        return stringBuilder.toString();
    }

    //将请求到的response 写入所给的outputStream中
    public static boolean downloadUrlToStream(String urlString, OutputStream outputStream){
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;
        BufferedOutputStream out = null;

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream());
            out = new BufferedOutputStream(outputStream);
            int b;   //为什么是int?
            while ((b = in.read()) != -1){
                out.write(b);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (urlConnection != null)
                urlConnection.disconnect();
            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}



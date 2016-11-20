package com.soulmatexd.photowall;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.BoolRes;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.jakewharton.disklrucache.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.zip.Inflater;

import static com.soulmatexd.photowall.MyBitmapFactory.caculateInSampleSize;
import static com.soulmatexd.photowall.MyBitmapFactory.decodeSampleBitmapFromSnapshot;

/**
 * Created by SoulMateXD on 2016/11/14.
 */

public class RecyclerViewAdapter extends RecyclerView.Adapter<MyViewHolder> implements View.OnClickListener{

    private static final int MAXSIZE = (int) (Runtime.getRuntime().maxMemory()/1024);
    private static final int CACHESIZE = MAXSIZE/8;
    private static final long DISK_CACHE_SIZE = 1024*1024*50;//50MB


    private ArrayList<MyImage> datas;
    private LayoutInflater inflater;
    private MyViewHolder myViewHolder;
    private RecyclerView mPhotoWall;
    private LruCache<String, Bitmap> mMemoryCache;
    private boolean isRecyclerViewIdle = true;
    private int imageReqWidth = 500;
    private int imageReqHeight = 500;
    private Resources resources;
    private DiskLruCache mDiskLruCache;
    private LinearLayoutManager linearLayoutManager;
    private int lastVisibleItem;
    private Context context;
    private ImgClick imgClick;


//    private int firstVisibleItem;
//    private int visibleItemCount;
//    private boolean isFrist = true;


    RecyclerViewAdapter(ArrayList<MyImage> datas, Context context,
                        RecyclerView photoWall, Resources resources){
        this.datas = datas;
        this.resources = resources;
        inflater = LayoutInflater.from(context);
        this.context = context;
        mPhotoWall = photoWall;
        linearLayoutManager = (LinearLayoutManager) mPhotoWall.getLayoutManager();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        imageReqWidth = displayMetrics.widthPixels;
        imageReqHeight = imageReqWidth;

        //内存缓存
        mMemoryCache = new LruCache<String, Bitmap>(CACHESIZE){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount()/1024;   //换为KB
            }
        };



        //创建硬盘缓存
        File directory = Util.getCacheDiskDir(context, "bitmap");
        if (!directory.exists()){   //不存在则创建一次
            directory.mkdirs();
        }
        try {
            mDiskLruCache = DiskLruCache.open(directory, 1, 1, DISK_CACHE_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setIsRecyclerViewIdel(Boolean isRecyclerIdel){
        isRecyclerViewIdle = isRecyclerIdel;
    }

    public LruCache<String, Bitmap> getmMemoryCache(){
        return mMemoryCache;
    }

    public void setDatas(ArrayList<MyImage> datas){
        this.datas = datas;
    }
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        myViewHolder = new MyViewHolder(inflater.inflate(R.layout.image_item, parent, false));
        return myViewHolder;
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.imageView.setTag(datas.get(position).getUrl());   //以url为key
        setBitmap(holder.imageView, datas.get(position).getUrl());
        holder.textView.setText(datas.get(position).getWho());
        holder.imageView.setTag(position);
        holder.imageView.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return datas.size();
    }







    //图片三级缓存逻辑函数
    private void setBitmap(ImageView imageView, String url){
        Bitmap bitmap = getBitmapFromMemoryCache(url);
        if(bitmap != null){
            imageView.setImageBitmap(bitmap);
        }else {
            bitmap = MyBitmapFactory.decodeSampleBitmapFromResource(resources, R.drawable.xd,
                    imageReqWidth, imageReqHeight);
            imageView.setImageBitmap(bitmap);
            if (isRecyclerViewIdle)
                setBitmapFromHttp(imageView,url);
        }
    }

    private void setBitmapFromHttp(ImageView img, String url) {
        BitMapWorkerTask task = new BitMapWorkerTask();
        MyImageView myImageview = new MyImageView(img, url);
        task.execute(myImageview);
    }



    //对内存的缓存
    private Bitmap getBitmapFromMemoryCache(String url){
        return mMemoryCache.get(url);
    }

    private void addBitmapToMemoryCache(String url, Bitmap bitmap){
        if (getBitmapFromMemoryCache(url) == null){
            mMemoryCache.put(url, bitmap);
        }
    }


    class BitMapWorkerTask extends AsyncTask<MyImageView, Void , MyImageView>{

        @Override
        //下载文件并缓存至DiskLruCache
        protected MyImageView doInBackground(MyImageView... params) {
            MyImageView myImageView = params[0];
            String key = Util.hashKeyFromString(myImageView.url);
            try {
                DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                if (editor != null){
                    OutputStream out = editor.newOutputStream(0);
                    if (Util.downloadUrlToStream(myImageView.url, out)){
                        editor.commit();
                    }else {
                        editor.abort();
                    }
                }
                mDiskLruCache.flush();  //写入journal（日志）
            } catch (IOException e) {
                e.printStackTrace();
            }

            return myImageView;
        }

        @Override
        //从DiskLruCache中读取文件并放入内存中，顺便set下imageView
        protected void onPostExecute(MyImageView myImageView) {
            ImageView imageView = myImageView.imgView;
            Bitmap bitmap = null;
            String key = Util.hashKeyFromString(myImageView.url);
            try {
                DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
                if (snapShot != null){
                    bitmap = decodeSampleBitmapFromSnapshot(snapShot,
                            imageReqWidth, imageReqHeight);
                    if (bitmap != null)
                    addBitmapToMemoryCache(myImageView.url, bitmap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (imageView!= null && bitmap != null){
                imageView.setImageBitmap(bitmap);
            }
        }
    }



    //    @Override
//    public void onScrollStateChanged(AbsListView view, int scrollState) {
//        if (scrollState == SCROLL_STATE_IDLE){
//            loadBitmaps(firstVisibleItem, visibleItemCount);
//        }else {
//            cancleTasks();
//        }
//    }
//
//    @Override
//    public void onScroll(AbsListView view, int firstVisibleItem,
//                         int visibleItemCount, int totalItemCount) {
//        this.firstVisibleItem = firstVisibleItem;
//        this.visibleItemCount = visibleItemCount;
//        if (isFrist && visibleItemCount>0){
//            loadBitmaps(firstVisibleItem, visibleItemCount);
//            isFrist = false;
//        }
//    }

//    private void loadBitmaps(int firstVisibleItem, int visibleItemCount) {
//        try {
//            for (int i=firstVisibleItem; i<firstVisibleItem+visibleItemCount; i++){
//                String url = datas.get(i).getUrl();
//                Bitmap bitmap = getBitmapFromMemoryCache(url);
//                if (bitmap != null){
//                    ImageView imageView = (ImageView) mPhotoWall.findViewWithTag(url);
//                    imageView.setImageBitmap(bitmap);
//                }else {
//                    BitMapWorkerTask task = new BitMapWorkerTask();
//                    task.execute(url);
//                    taskConllection.add(task);
//                }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//    private void cancleTasks(){
//        for (BitMapWorkerTask task : taskConllection){
//            task.cancel(false);
//        }
//    }

    public interface ImgClick{
        void onItemClick(ArrayList<MyImage> datas, int position);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.image_item_img:
                imgClick.onItemClick(datas, (Integer) v.getTag());
        }
    }

    public void setImgClick(ImgClick imgClick){
        this.imgClick = imgClick;
    }
}

class MyViewHolder extends RecyclerView.ViewHolder{
    ImageView imageView;
    TextView textView;

    public MyViewHolder(View itemView) {
        super(itemView);
        imageView = (ImageView) itemView.findViewById(R.id.image_item_img);
        textView = (TextView) itemView.findViewById(R.id.image_item_author);
    }

}

class MyImageView {
    ImageView imgView;
    String url;
    Bitmap bitmap;

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public MyImageView(ImageView img, String url) {
        this.imgView = img;
        this.url = url;
    }
}



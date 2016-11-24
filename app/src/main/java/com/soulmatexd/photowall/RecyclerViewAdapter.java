package com.soulmatexd.photowall;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.annotation.BoolRes;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.util.DisplayMetrics;
import android.util.Log;
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
    private static final int CACHESIZE = MAXSIZE/4;
    private static final long DISK_CACHE_SIZE = 1024*1024*50;//50MB


    private ArrayList<MyImage> datas;
    private LayoutInflater inflater;
    private MyViewHolder myViewHolder;
    private RecyclerView mPhotoWall;
    private LruCache<String, Bitmap> mMemoryCache;
    private boolean isRecyclerViewIdle = true;
    private int imageReqWidth;
    private int imageReqHeight;
    private Resources resources;
    private DiskLruCache mDiskLruCache;
    private LinearLayoutManager linearLayoutManager;
    private int lastVisibleItem;
    private Context context;
    private ImgClick imgClick;

    RecyclerViewAdapter(ArrayList<MyImage> datas, Context context,
                        RecyclerView photoWall, Resources resources){
        this.datas = datas;
        this.resources = resources;
        inflater = LayoutInflater.from(context);
        this.context = context;
        mPhotoWall = photoWall;
        linearLayoutManager = (LinearLayoutManager) mPhotoWall.getLayoutManager();
        imageReqHeight = imageReqWidth = 300;

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

    public void addDatas(ArrayList<MyImage> datas){
        this.datas.addAll(datas);
    }

    public void setIsRecyclerViewIdel(Boolean isRecyclerIdel){
        isRecyclerViewIdle = isRecyclerIdel;
    }

    public LruCache<String, Bitmap> getmMemoryCache(){
        return mMemoryCache;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        myViewHolder = new MyViewHolder(inflater.inflate(R.layout.image_item, parent, false));
        return myViewHolder;
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.textView.setText(datas.get(position).getWho());
        holder.imageView.setMinimumHeight(400);
        holder.imageView.setMaxHeight(400);
        holder.imageView.setOnClickListener(this);
        //当设置多个tag时，必须在ids.xml中进行设置
        holder.imageView.setTag(R.id.tag_intent, position);
        setBitmap(holder, datas.get(position).getUrl());
    }

    @Override
    public void onViewRecycled(MyViewHolder holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return datas.size();
    }

    //图片三级缓存逻辑函数
    private void setBitmap(MyViewHolder holder, String url){
        Bitmap bitmap = getBitmapFromMemoryCache(url);
        if(bitmap != null){
            holder.imageView.setImageBitmap(bitmap);
            return;
        }
        try {
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(Util.hashKeyFromString(url));
            if (snapshot != null){
                bitmap = MyBitmapFactory.decodeSampleBitmapFromSnapshot(snapshot, imageReqWidth,imageReqHeight);
                if (bitmap != null)
                holder.imageView.setImageBitmap(bitmap);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (isRecyclerViewIdle) {
            setBitmapFromHttp(holder.imageView, url, holder.cardView);
        }
    }

    private void setBitmapFromHttp(ImageView img, String url, CardView cardView) {
        BitMapWorkerTask task = new BitMapWorkerTask();
        //防止图片闪烁
        cardView.setTag(R.id.tag_set_bitmap,url);
        img.setTag(R.id.tag_set_bitmap, url);
        MyImageView myImageview = new MyImageView(img, url, cardView);
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

    class BitMapWorkerTask extends AsyncTask<MyImageView, Void , MyImageView> {

        @Override
        //下载文件并缓存至DiskLruCache
        protected MyImageView doInBackground(MyImageView... params) {
            MyImageView myImageView = params[0];
            String key = Util.hashKeyFromString(myImageView.url);
            try {
                DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                if (editor != null) {
                    OutputStream out = editor.newOutputStream(0);
                    if (Util.downloadUrlToStream(myImageView.url, out)) {
                        editor.commit();
                    } else {
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
            Bitmap bitmap = null;
            String key = Util.hashKeyFromString(myImageView.url);
            try {
                DiskLruCache.Snapshot snapShot = mDiskLruCache.get(key);
                if (snapShot != null) {
                    bitmap = decodeSampleBitmapFromSnapshot(snapShot,
                            imageReqWidth, imageReqHeight);
                    if (bitmap != null)
                        addBitmapToMemoryCache(myImageView.url, bitmap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //如果对图片执行了异步处理后，现在视野中的那个imgView没有被回收重用（重新setTag）则将bitmap设置进去
            //注意，此处设置的Tag是为CardView设置的Tag
            //Question：  为什么给imageView设置Tag就不行？recyclerview回收到底回收啥？
            Log.d("MainAdapter", myImageView.imgView.getTag().toString());
            if (myImageView.imgView.getTag() == null){
                Log.d("MainAdapter", "null null null");
            }
            if (myImageView.url.equals(myImageView.cardView.getTag(R.id.tag_set_bitmap))  && bitmap != null){
                myImageView.imgView.setImageBitmap(bitmap);
            }
        }
    }

    public interface ImgClick{
        void onItemClick(ArrayList<MyImage> datas, int position);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.image_item_img:
                imgClick.onItemClick(datas, (Integer) v.getTag(R.id.tag_intent));
        }
    }

    public void setImgClick(ImgClick imgClick){
        this.imgClick = imgClick;
    }
}

class MyViewHolder extends RecyclerView.ViewHolder{
    ImageView imageView;
    TextView textView;
    CardView cardView;

    public MyViewHolder(View itemView) {
        super(itemView);
        imageView = (ImageView) itemView.findViewById(R.id.image_item_img);
        textView = (TextView) itemView.findViewById(R.id.image_item_author);
        cardView = (CardView) itemView.findViewById(R.id.itme_cardview);
    }

}

class MyImageView {
    CardView cardView;
    ImageView imgView;
    String url;

    public MyImageView(ImageView img, String url, CardView cardView) {
        this.imgView = img;
        this.url = url;
    }
}



package com.soulmatexd.photowall;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.AsyncTask;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.ContentFrameLayout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class ShowImg extends AppCompatActivity {

    private static final int MAXSIZE = (int) (Runtime.getRuntime().maxMemory()/1024);
    private static final int CACHESIZE = MAXSIZE/8;
    private static final long DISK_CACHE_SIZE = 1024*1024*50;//50MB


    private ImgAdapter imgAdapter;
    private ViewPager viewPager;
    private ArrayList<MyImage> datas;
    private int startPosition;
    private DiskLruCache mDiskLruCache;
    private LruCache<String, Bitmap> mLruCache;
    int reqWidth;
    int reqHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_img);
        Intent intent = getIntent();
        datas = intent.getParcelableArrayListExtra("datas");
        startPosition = intent.getIntExtra("position", 0);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        reqHeight = displayMetrics.heightPixels;
        reqWidth = displayMetrics.widthPixels;


        File directory = Util.getCacheDiskDir(this, "bitmap");
        try {
            mDiskLruCache = DiskLruCache.open(directory, 1, 1, DISK_CACHE_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }


        mLruCache = new LruCache<String, Bitmap>(CACHESIZE){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount()/1024;
            }
        };



        viewPager = (ViewPager) findViewById(R.id.show_img_view_pager);
        imgAdapter = new ImgAdapter(datas, this);
        viewPager.setOffscreenPageLimit(4);
        viewPager.setAdapter(imgAdapter);

        //这句话按逻辑来说必须放在setAdapter之后， 否则你TM连adapter都没有它去哪里给你set？
        viewPager.setCurrentItem(startPosition);


    }

    private Bitmap getBitmapFromMemoryCache(String url){
        return mLruCache.get(url);
    }

    private void addBitmapToMemoryCache(String url, Bitmap bitmap){
        if (getBitmapFromMemoryCache(url) == null){
            mLruCache.put(url, bitmap);
        }
    }

    class ImgAdapter extends PagerAdapter{
        private ArrayList<MyImage> datas;
        private Context context;


        ImgAdapter(ArrayList<MyImage> datas, Context context){
            this.datas = datas;
            this.context = context;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = getBitmapFromMemoryCache(datas.get(position).getUrl());
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                container.addView(imageView);
                return imageView;
            }
            DiskLruCache.Snapshot snapshot = null;
            try {
                 snapshot = mDiskLruCache.get(Util.hashKeyFromString(datas.get(position).getUrl()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (snapshot != null){
                bitmap = MyBitmapFactory.decodeSampleBitmapFromSnapshot(snapshot, reqWidth, reqHeight);
                if (bitmap != null){
                    imageView.setImageBitmap(bitmap);
                    container.addView(imageView);
                    return imageView;
                }
            }
            imageView.setImageBitmap(MyBitmapFactory.
                    decodeSampleBitmapFromResource(getResources(), R.drawable.xd, reqWidth, reqHeight));
            ShowImg.DownLoadTask downLoadTask = new ShowImg.DownLoadTask(imageView);
            downLoadTask.execute(datas.get(position));
            container.addView(imageView);
            return imageView;

        }

        @Override
        public int getCount() {
            return datas.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((ImageView) object);
        }
    }


    public class DownLoadTask extends AsyncTask<MyImage, Void, MyImage>{
        ImageView imageView;

        DownLoadTask(ImageView imageView){
            this.imageView = imageView;
        }
        @Override
        protected MyImage doInBackground(MyImage... params) {
            MyImage myImage = params[0];
            String key = Util.hashKeyFromString(myImage.getUrl());

            try {
                DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                if (editor != null){
                    OutputStream outputStream = editor.newOutputStream(0);
                    if (Util.downloadUrlToStream(myImage.getUrl(), outputStream)){
                        editor.commit();
                    }else {
                        editor.abort();
                    }
                }
                mDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return myImage;
        }

        @Override
        protected void onPostExecute(MyImage myImage) {
            Bitmap bitmap = null;
            String key = Util.hashKeyFromString(myImage.getUrl());
            try {
                DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
                if (snapshot != null)
                bitmap = MyBitmapFactory.decodeSampleBitmapFromSnapshot(snapshot, reqWidth, reqHeight);
                if (bitmap != null){
                    addBitmapToMemoryCache(myImage.getUrl(), bitmap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (bitmap != null && imageView != null){
                imageView.setImageBitmap(bitmap);
            }

        }
    }
}





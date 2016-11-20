package com.soulmatexd.photowall;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ArrayList<MyImage> datas;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter adapter ;
    private static final int REFRESHURL = 1;
    private static final int ADDDATAS = 2;
    private String mApiUrl = "http://gank.io/api/data/%E7%A6%8F%E5%88%A9/10/";
    private int page = 1;
    private int maxPage = 38;
    private SwipeRefreshLayout swipeRefreshLayout;
    private int mLastVisibleItem;
    private boolean isRecyclerViewIdle = true;
    private LinearLayoutManager linearLayoutManager;

    String stringResponse;
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            String response;
            Gson gson = new Gson();
            Type type = new TypeToken<Bean<ArrayList<MyImage>>>() {
            }.getType();
            switch (msg.what) {
                case REFRESHURL:
                    response = msg.obj.toString();
                    Bean<ArrayList<MyImage>> bean1 = gson.fromJson(response, type);
                    datas = bean1.results;
//                    for (int i = 0; i < datas.size(); i++) {//设置默认图片
//                        datas.get(i).setBitmap(MyBitmapFactory.decodeSampleBitmapFromResource(getResources(),
//                                R.drawable.xd, 300, 300));
//                    }
                    //此处必须要重新set一次Data，我也不知道为什么。
                    adapter.setDatas(datas);
                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                    //一次性将所有bitmap加载到内存中后再交给recyclerview显示
//                    new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        for (int i=0; i<datas.size(); i++){
//                                Log.d("MyPhotoURL", datas.get(i).getWho());
//                                Bitmap bitmap = HttpUtil.getBitMap(datas.get(i).getUrl());
//                                datas.get(i).setBitmap(bitmap);
//                            }
//                        Message message = new Message();
//                        message.what = GETPHOTOES;
//                        handler.sendMessage(message);
//                        }
//                    }).start();
                    break;
                case ADDDATAS:
                    response = msg.obj.toString();
                    Bean<ArrayList<MyImage>> bean2 = gson.fromJson(response, type);
                    datas.addAll(bean2.results);
                    adapter.setDatas(datas);
                    adapter.notifyDataSetChanged();
                default:
                    break;
            }

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        datas = new ArrayList<>();
        recyclerView = (RecyclerView) findViewById(R.id.main_recycler_view);
        adapter = new RecyclerViewAdapter(datas, this, recyclerView, getResources());
        adapter.setImgClick(new RecyclerViewAdapter.ImgClick() {
            @Override
            public void onItemClick(ArrayList<MyImage> datas, int position) {
                Intent intent = new Intent(MainActivity.this, ShowImg.class);
                intent.putParcelableArrayListExtra("datas", datas);
                intent.putExtra("position", position);
                startActivity(intent);
            }
        });
//        recyclerView.setLayoutManager(
//                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == 0){
                    isRecyclerViewIdle = true;
                    adapter.notifyDataSetChanged();
                    if (mLastVisibleItem == adapter.getItemCount() -1){

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                page++;
                                String mApi = "http://gank.io/api/data/%E7%A6%8F%E5%88%A9/10/" + page;
                                Message message = new Message();
                                message.what = ADDDATAS;
                                String response = HttpUtil.get(mApi);
                                message.obj = response;
                                handler.sendMessage(message);
                            }
                        }).start();
                    }
                    adapter.setIsRecyclerViewIdel(isRecyclerViewIdle);
                } else {
                    isRecyclerViewIdle = false;
                    adapter.setIsRecyclerViewIdel(isRecyclerViewIdle);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mLastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
            }
        });





                //设置swipeRefreshLayout
                swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.main_swipe_refresh);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_light,android.R.color.holo_green_light
                ,android.R.color.holo_orange_light, android.R.color.holo_red_light);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                final String url = "http://gank.io/api/data/%E7%A6%8F%E5%88%A9/10/" + page;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        stringResponse = HttpUtil.get(url);
                        Message message = new Message();
                        message.what = REFRESHURL;
                        message.obj = stringResponse;
                        handler.sendMessage(message);
                    }
                }).start();
                page++;
                if (page == maxPage){
                    page = 1;
                }
            }
        });


    }
}

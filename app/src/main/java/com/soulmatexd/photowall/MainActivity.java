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
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener{
    private ArrayList<MyImage> datas;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter adapter ;
    private static final int REFRESHURL = 1;
    private static final int ADDDATAS = 2;
    private static final int FIRSTIN = 3;
    private int page = 1;
    private int maxPage = 38;
    private SwipeRefreshLayout swipeRefreshLayout;
    private int mLastVisibleItem;
    private boolean isRecyclerViewIdle = true;
    private LinearLayoutManager linearLayoutManager;
    private StaggeredGridLayoutManager staggeredGridLayoutManager;
    private String mApiHead = "http://gank.io/api/data/%E7%A6%8F%E5%88%A9/10/";
    private String stringResponse;
    private int recyclerHeight = 0;
    private boolean isBegin = true;
    private Toolbar toolbar;
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            String response;
            Gson gson = new Gson();
            Type type = new TypeToken<List<MyImage>>() {
            }.getType();
            response = (String)msg.obj;
            try {
                JSONObject jsonObject = new JSONObject(response);
                JSONArray jsonArray = jsonObject.getJSONArray("results");
                ArrayList<MyImage> data = gson.fromJson(jsonArray.toString(), type);
                if (isBegin){
                    datas.addAll(data);
                    isBegin = false;
                }else {
                    adapter.addDatas(data);
                }
                adapter.notifyDataSetChanged();
                swipeRefreshLayout.setRefreshing(false);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        datas = new ArrayList<>();

        toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);


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
        staggeredGridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        //避免recyclerview 的item乱换位置
        staggeredGridLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        recyclerView.setLayoutManager(staggeredGridLayoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            boolean isSlidingToUp = false;
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (newState == 0){
                    isRecyclerViewIdle = true;
                    if (layoutManager instanceof StaggeredGridLayoutManager){
                        StaggeredGridLayoutManager staggeredGridLayoutManager =
                                (StaggeredGridLayoutManager) layoutManager;
                        //获得每一排的最后一个item的position
                        int[] ints = staggeredGridLayoutManager.findLastVisibleItemPositions(null);
                        int max = findMax(ints);
                        if (max == adapter.getItemCount()-1 && isSlidingToUp){
                            onRefresh();
                        }
                        //防止滑回到顶部的时候有空白处
                        staggeredGridLayoutManager.invalidateSpanAssignments();
                        adapter.notifyDataSetChanged();
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
                if (dy > 0){
                    isSlidingToUp = true;
                }else {
                    isSlidingToUp = false;
                }
            }
        });

        //设置swipeRefreshLayout
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.main_swipe_refresh);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_light,android.R.color.holo_green_light
                ,android.R.color.holo_orange_light, android.R.color.holo_red_light);
        swipeRefreshLayout.setProgressViewOffset(false, 0, 52);
        swipeRefreshLayout.setOnRefreshListener(this);

        //onCreate生成Activity的时候只会执行一次
        onRefresh();

    }

    @Override
    public void onRefresh() {
        swipeRefreshLayout.setRefreshing(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Message message = new Message();
                stringResponse = HttpUtil.get(mApiHead + page);
                message.obj = stringResponse;
                handler.sendMessage(message);
                page++;
                if (page > maxPage){
                    page = 1;
                }
            }
        }).start();
    }

    public int findMax(int[] ints){
        int max = 0;
        for (int i : ints){
            if (i>max){
                max = i;
            }
        }
        return max;
    }
}

package com.soulmatexd.photowall;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by SoulMateXD on 2016/11/14.
 */

public class MyImage implements Parcelable{
    private String url;
    private String who;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    public String getWho() {
        return who;
    }

    public void setWho(String who) {
        this.who = who;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
        dest.writeString(who);
    }

    MyImage(Parcel in){
        this.url = in.readString();
        this.who = in.readString();
    }

    public static final Creator<MyImage> CREATOR = new Creator<MyImage>() {
        @Override
        public MyImage createFromParcel(Parcel in) {
            return new MyImage(in);
        }

        @Override
        public MyImage[] newArray(int size) {
            return new MyImage[size];
        }
    };
}

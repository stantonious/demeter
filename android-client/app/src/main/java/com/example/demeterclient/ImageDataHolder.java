package com.example.demeterclient;

import java.util.ArrayList;
import java.util.List;

public class ImageDataHolder {
    private static final ImageDataHolder instance = new ImageDataHolder();
    private List<byte[]> imageList = new ArrayList<>();

    private ImageDataHolder() {}

    public static ImageDataHolder getInstance() {
        return instance;
    }

    public void setImageList(List<byte[]> imageList) {
        this.imageList = imageList;
    }

    public List<byte[]> getImageList() {
        return this.imageList;
    }

    public void clearImageList() {
        this.imageList.clear();
    }
}
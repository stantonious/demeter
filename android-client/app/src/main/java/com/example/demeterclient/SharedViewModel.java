package com.example.demeterclient;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SharedViewModel extends ViewModel {

    private final MutableLiveData<HashMap<String, ArrayList<Float>>> sensorDataHistory = new MutableLiveData<>();
    private final MutableLiveData<MainActivity.BleConnectionStatus> bleConnectionStatus = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAugmenting = new MutableLiveData<>(false);
    private final MutableLiveData<List<byte[]>> augmentedResult = new MutableLiveData<>();

    private String plantType;
    private String subType;
    private String age;

    public LiveData<HashMap<String, ArrayList<Float>>> getSensorDataHistory() {
        return sensorDataHistory;
    }

    public void updateSensorDataHistory(HashMap<String, ArrayList<Float>> history) {
        sensorDataHistory.postValue(history);
    }

    public LiveData<MainActivity.BleConnectionStatus> getBleConnectionStatus() {
        return bleConnectionStatus;
    }

    public void setBleConnectionStatus(MainActivity.BleConnectionStatus status) {
        bleConnectionStatus.postValue(status);
    }

    public LiveData<Boolean> getIsAugmenting() {
        return isAugmenting;
    }

    public void setIsAugmenting(boolean augmenting) {
        isAugmenting.postValue(augmenting);
    }

    public LiveData<List<byte[]>> getAugmentedResult() {
        return augmentedResult;
    }

    public void setAugmentedResult(List<byte[]> images) {
        augmentedResult.postValue(images);
    }

    public String getPlantType() {
        return plantType;
    }

    public void setPlantType(String plantType) {
        this.plantType = plantType;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }
}
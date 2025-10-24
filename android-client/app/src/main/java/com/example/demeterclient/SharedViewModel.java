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
    private final MutableLiveData<Integer> augmentSize = new MutableLiveData<>(65); // Default size
    private final MutableLiveData<Boolean> isSuggesting = new MutableLiveData<>(false);
    private final MutableLiveData<List<PlantSuggestion>> plantSuggestions = new MutableLiveData<>();
    private final MutableLiveData<List<String>> plantTypes = new MutableLiveData<>();
    private final MutableLiveData<List<String>> plantCharacteristics = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isFetchingImage = new MutableLiveData<>(false);

    private String plantType;
    private String subType;
    private String age;
    private final MutableLiveData<String> imageUri = new MutableLiveData<>();

    public LiveData<String> getImageUri() {
        return imageUri;
    }

    public void setImageUri(String uri) {
        imageUri.setValue(uri);
    }

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

    public LiveData<Integer> getAugmentSize() {
        return augmentSize;
    }

    public void setAugmentSize(int size) {
        augmentSize.setValue(size);
    }

    public LiveData<Boolean> getIsSuggesting() {
        return isSuggesting;
    }

    public void setIsSuggesting(boolean suggesting) {
        isSuggesting.postValue(suggesting);
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

    public LiveData<List<PlantSuggestion>> getPlantSuggestions() {
        return plantSuggestions;
    }

    public void setPlantSuggestions(List<PlantSuggestion> suggestions) {
        plantSuggestions.postValue(suggestions);
    }

    public void updatePlantSuggestion(PlantSuggestion suggestion) {
        List<PlantSuggestion> currentSuggestions = plantSuggestions.getValue();
        if (currentSuggestions != null) {
            List<PlantSuggestion> newSuggestions = new ArrayList<>(currentSuggestions);
            for (int i = 0; i < newSuggestions.size(); i++) {
                if (newSuggestions.get(i).getName().equals(suggestion.getName())) {
                    newSuggestions.set(i, suggestion);
                    plantSuggestions.postValue(newSuggestions);
                    return;
                }
            }
        }
    }

    public void clear() {
        sensorDataHistory.postValue(new HashMap<>());
        bleConnectionStatus.postValue(MainActivity.BleConnectionStatus.DISCONNECTED);
        isAugmenting.postValue(false);
        augmentedResult.postValue(null);
        augmentSize.setValue(65);
        isSuggesting.postValue(false);
        plantSuggestions.postValue(new ArrayList<>());
        plantType = null;
        subType = null;
        age = null;
        imageUri.setValue(null);
    }

    public LiveData<List<String>> getPlantTypes() {
        return plantTypes;
    }

    public void setPlantTypes(List<String> types) {
        plantTypes.postValue(types);
    }

    public LiveData<List<String>> getPlantCharacteristics() {
        return plantCharacteristics;
    }

    public void setPlantCharacteristics(List<String> characteristics) {
        plantCharacteristics.postValue(characteristics);
    }

    public LiveData<Boolean> getIsFetchingImage() {
        return isFetchingImage;
    }

    public void setIsFetchingImage(boolean fetching) {
        isFetchingImage.postValue(fetching);
    }
}
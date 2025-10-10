package com.example.demeterclient.ui.aoi;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.demeterclient.PlantSuggestion;
import com.example.demeterclient.R;
import java.util.ArrayList;
import java.util.List;

public class AoiSelectAdapter extends RecyclerView.Adapter<AoiSelectAdapter.SuggestionViewHolder> {

    private List<PlantSuggestion> suggestions;
    private final OnItemClickListener listener;
    private final List<String> selectedPlants = new ArrayList<>();

    public interface OnItemClickListener {
        void onItemClick(PlantSuggestion suggestion);
    }

    public AoiSelectAdapter(List<PlantSuggestion> suggestions, OnItemClickListener listener) {
        this.suggestions = suggestions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.aoi_select_item, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        PlantSuggestion suggestion = suggestions.get(position);
        holder.plantNameTextView.setText(suggestion.getName());
        holder.plantCheckbox.setOnCheckedChangeListener(null); // Avoid listener firing on bind
        holder.plantCheckbox.setChecked(selectedPlants.contains(suggestion.getName()));

        holder.plantCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedPlants.contains(suggestion.getName())) {
                    selectedPlants.add(suggestion.getName());
                }
            } else {
                selectedPlants.remove(suggestion.getName());
            }
        });

        holder.itemView.setOnClickListener(v -> listener.onItemClick(suggestion));

        GradientDrawable background = (GradientDrawable) holder.feasibilityIndicator.getBackground();
        if (suggestion.getFeasibilityScore() >= 0.7) {
            background.setColor(Color.GREEN);
        } else if (suggestion.getFeasibilityScore() >= 0.4) {
            background.setColor(Color.YELLOW);
        } else if (suggestion.getFeasibilityScore() >= 0.0){
            background.setColor(Color.RED);
        } else {
            background.setColor(Color.LTGRAY);
        }
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    public List<String> getSelectedPlants() {
        return selectedPlants;
    }

    public void setSuggestions(List<PlantSuggestion> suggestions) {
        this.suggestions = suggestions;
        notifyDataSetChanged();
    }

    static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        TextView plantNameTextView;
        CheckBox plantCheckbox;
        View feasibilityIndicator;

        SuggestionViewHolder(View itemView) {
            super(itemView);
            plantNameTextView = itemView.findViewById(R.id.plant_name_text_view);
            plantCheckbox = itemView.findViewById(R.id.plant_checkbox);
            feasibilityIndicator = itemView.findViewById(R.id.feasibility_indicator);
        }
    }
}
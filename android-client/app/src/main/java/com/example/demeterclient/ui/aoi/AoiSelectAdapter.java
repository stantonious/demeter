package com.example.demeterclient.ui.aoi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.demeterclient.R;
import java.util.ArrayList;
import java.util.List;

public class AoiSelectAdapter extends RecyclerView.Adapter<AoiSelectAdapter.SuggestionViewHolder> {

    private List<String> suggestions;
    private final OnFeasibilityClickListener feasibilityClickListener;
    private final List<String> selectedPlants = new ArrayList<>();

    public interface OnFeasibilityClickListener {
        void onFeasibilityClick(String plantName);
    }

    public AoiSelectAdapter(List<String> suggestions, OnFeasibilityClickListener listener) {
        this.suggestions = suggestions;
        this.feasibilityClickListener = listener;
    }

    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.aoi_select_item, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        String suggestion = suggestions.get(position);
        holder.plantNameTextView.setText(suggestion);
        holder.plantCheckbox.setOnCheckedChangeListener(null); // Avoid listener firing on bind
        holder.plantCheckbox.setChecked(selectedPlants.contains(suggestion));

        holder.plantCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedPlants.contains(suggestion)) {
                    selectedPlants.add(suggestion);
                }
            } else {
                selectedPlants.remove(suggestion);
            }
        });

        holder.feasibilityIcon.setOnClickListener(v -> {
            if (feasibilityClickListener != null) {
                feasibilityClickListener.onFeasibilityClick(suggestion);
            }
        });
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    public List<String> getSelectedPlants() {
        return selectedPlants;
    }

    static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        TextView plantNameTextView;
        CheckBox plantCheckbox;
        ImageView feasibilityIcon;

        SuggestionViewHolder(View itemView) {
            super(itemView);
            plantNameTextView = itemView.findViewById(R.id.plant_name_text_view);
            plantCheckbox = itemView.findViewById(R.id.plant_checkbox);
            feasibilityIcon = itemView.findViewById(R.id.feasibility_icon);
        }
    }
}
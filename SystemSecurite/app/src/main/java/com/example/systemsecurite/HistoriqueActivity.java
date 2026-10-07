package com.example.systemsecurite;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class HistoriqueActivity extends AppCompatActivity {

    private RecyclerView recyclerHistorique;
    private LinearLayout emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historique);

        recyclerHistorique = findViewById(R.id.recyclerHistorique);
        emptyState = findViewById(R.id.emptyState);
        MaterialButton btnRetour = findViewById(R.id.btnRetour);
        MaterialButton btnEffacer = findViewById(R.id.btnEffacer);

        recyclerHistorique.setLayoutManager(new LinearLayoutManager(this));

        btnRetour.setOnClickListener(v -> finish());

        btnEffacer.setOnClickListener(v -> {
            HistoryManager.clearHistory(this);
            refreshList();
        });

        refreshList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        List<HistoryManager.HistoryEntry> entries = HistoryManager.getEntries(this);
        recyclerHistorique.setAdapter(new HistoriqueAdapter(entries));

        if (entries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerHistorique.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerHistorique.setVisibility(View.VISIBLE);
        }
    }
}
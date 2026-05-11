package com.gamecenter.app.games.pipeline;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;

public class PipelineActivity extends AppCompatActivity {

    private PipelineView pipelineView;
    private PipelineGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pipeline);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_pipeline);

        TextView tvStatus = findViewById(R.id.tv_game_status);

        pipelineView = findViewById(R.id.pipeline_view);
        game = new PipelineGame();
        pipelineView.setGame(game);

        pipelineView.setOnLevelCompleteListener(() -> {
            tvStatus.setText("水管接通！");
            Toast.makeText(this, "恭喜！水管已接通！", Toast.LENGTH_SHORT).show();
        });

        MaterialButton btnRestart = findViewById(R.id.btn_game_restart);
        btnRestart.setOnClickListener(v -> {
            game.reset();
            pipelineView.invalidate();
            tvStatus.setText("");
        });

        pipelineView.invalidate();
    }
}
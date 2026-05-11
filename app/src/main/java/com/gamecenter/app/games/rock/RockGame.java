package com.gamecenter.app.games.rock;

import java.util.Random;

public class RockGame {

    public static final int ROCK = 0;
    public static final int SCISSORS = 1;
    public static final int PAPER = 2;

    public static final int WIN = 1;
    public static final int LOSE = -1;
    public static final int DRAW = 0;

    private int playerScore;
    private int computerScore;
    private int playerChoice = -1;
    private int computerChoice = -1;
    private int lastResult = 0;
    private String lastResultText = "";
    private Random random;

    public RockGame() {
        random = new Random();
        reset();
    }

    public void reset() {
        playerScore = 0;
        computerScore = 0;
        playerChoice = -1;
        computerChoice = -1;
        lastResult = 0;
        lastResultText = "";
    }

    public void choose(int choice) {
        playerChoice = choice;
        computerChoice = random.nextInt(3);

        if (playerChoice == computerChoice) {
            lastResult = DRAW;
            lastResultText = "平局!";
        } else if ((playerChoice == ROCK && computerChoice == SCISSORS)
                || (playerChoice == SCISSORS && computerChoice == PAPER)
                || (playerChoice == PAPER && computerChoice == ROCK)) {
            lastResult = WIN;
            lastResultText = "你赢了!";
            playerScore++;
        } else {
            lastResult = LOSE;
            lastResultText = "电脑赢了!";
            computerScore++;
        }
    }

    public static String getChoiceName(int choice) {
        switch (choice) {
            case ROCK: return "石头";
            case SCISSORS: return "剪刀";
            case PAPER: return "布";
            default: return "?";
        }
    }

    public static String getChoiceEmoji(int choice) {
        switch (choice) {
            case ROCK: return "✊";
            case SCISSORS: return "✌️";
            case PAPER: return "✋";
            default: return "?";
        }
    }

    public int getPlayerScore() { return playerScore; }
    public int getComputerScore() { return computerScore; }
    public int getPlayerChoice() { return playerChoice; }
    public int getComputerChoice() { return computerChoice; }
    public int getLastResult() { return lastResult; }
    public String getLastResultText() { return lastResultText; }
}

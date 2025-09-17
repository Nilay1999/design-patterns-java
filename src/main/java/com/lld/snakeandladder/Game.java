package com.lld.snakeandladder;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import com.lld.snakeandladder.model.Dice;
import com.lld.snakeandladder.model.Player;

public class Game {
    private Board board;
    private Dice dice;
    private Queue<Player> queue;
    private Player winner;

    public Game() {
        initializeGame();
    }

    private void initializeGame() {
        Map<Integer, Jump> jumps = new HashMap<>();
        jumps.put(99, new Snake(99, 5));
        jumps.put(32, new Snake(32, 10));
        jumps.put(5, new Ladder(5, 25));
        jumps.put(13, new Ladder(13, 95));

        this.board = new Board(100, jumps);
        this.dice = new Dice(1, 6);

        this.queue = new LinkedList<>();
        this.queue.add(new Player("Nilay"));
        this.queue.add(new Player("John"));

        this.winner = null;
    }

    public void startGame() {
        System.out.println("Game started !");
        while (winner == null) {
            Player currentPlayer = this.queue.poll();

            int diceNum = this.dice.roll();
            System.out.println(currentPlayer.getUserName() + " rolled: " + diceNum + " at position: "
                    + currentPlayer.getPosition());

            int newPosition = this.board.getFinalPosition(currentPlayer.getPosition(), diceNum);
            currentPlayer.setPosition(newPosition);

            System.out.println("New position of " + currentPlayer.getUserName() + " is " + newPosition);
            if (newPosition == board.getSize()) {
                this.winner = currentPlayer;
                System.out.println("Winner is " + currentPlayer.getUserName());
            }
            queue.add(currentPlayer);
        }
    }
}

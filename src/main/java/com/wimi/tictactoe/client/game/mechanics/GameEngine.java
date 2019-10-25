/*
 * Copyright 2019 Akshit Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wimi.tictactoe.client.game.mechanics;

import com.wimi.tictactoe.App;
import com.wimi.tictactoe.builders.ButtonBuilder;
import com.wimi.tictactoe.builders.TextBuilder;
import com.wimi.tictactoe.client.NoughtsAndCrosses;
import com.wimi.tictactoe.client.game.Dashboard;
import com.wimi.tictactoe.client.game.Structure;
import com.wimi.tictactoe.util.Console;
import com.wimi.tictactoe.util.Themes;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Description The main class where the tic tac toe mechanics takes place.
 */
@SuppressWarnings({"unchecked", "CanBeFinal"})
public class GameEngine {

    private final StackPane root = new StackPane();
    private final Scene scene = new Scene(root, 1366, 768); // Default game dimensions
    private final Structure structure = new Structure();
    private final Text timeElapsed = new TextBuilder("Initiating..") // Shows the time elapsed since game start.
            .setFont(Font.font("Segoe UI", FontPosture.REGULAR, 42))
            .setColor(Color.BROWN)
            .build();
    private final Text nextMove = new TextBuilder("NAN") // Shows what the next move would be.
            .setFont(Font.font("Segoe UI", FontPosture.REGULAR, 48))
            .setColor(Color.DARKRED)
            .build();
    private final Text timeLeft = new TextBuilder(" ") // Time left for player to make a move when playing in Timed mode.
            .setFont(Font.font("Segoe UI", FontPosture.REGULAR, 42))
            .setColor(Color.GREENYELLOW)
            .build();
    private final int n = 3;
    private final Button[][] nodeMatrix = new Button[n][n];
    private final GridPane gameGrid = new GridPane();
    private Dashboard dashboard;
    private File theFile;
    private ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor(); // Timer which CAN reset anytime.
    private JSONObject jsonObject = new JSONObject(); // Primary JSON object.
    private JSONArray jsonArrayX = new JSONArray(); // Contains the time taken for each move by X.
    private JSONArray jsonArrayO = new JSONArray(); // Contains the time taken for each move by O.
    private boolean finished = false; // Is the game finished or not.
    private boolean timed = false; // Is the game in Timed mode or not.
    private long timePlayed = 0; // Time played since game start.
    private long timeForMove = 0; // Time taken to do each move is temporarily saved to this variable.
    private long maxTimeAllowed = 0; // Max time allowed when the game is in timed mode.
    private int movesX = 0; // Total moves made of X
    private int movesO = 0; // Total moves made of O
    private int moves = 0; // Total number of moves.
    private States move = States.NONE; // The current move.
    private States winner = States.NONE; // The winner of the game.
    private Runnable timerRunnable = () -> {
        if (finished) timerExecutor.shutdown();
        else {
            if (timed && moves > 0) {
                timeLeft.setText(String.valueOf(maxTimeAllowed - timeForMove));
                if (timeForMove >= maxTimeAllowed) {
                    winner = structure.getConjugateMove(move); // The other player is the winner as the opponent couldn't make a move in time.
                    Platform.runLater(this::win); // Run on JavaFX thread.
                    Console.log("Time is up for " + move + " to make a move. Hence " + structure.getConjugateMove(move).toString() + " is the winner.");
                    finished = true;
                    timerExecutor.shutdown();
                }
            }

            timeForMove++; // Increment time taken for current move.
        }
    };

    public GameEngine(File file) {
        Console.log("Started the game " + file.getName());
        this.theFile = file;

        ScheduledExecutorService elapsedExecutor = Executors.newSingleThreadScheduledExecutor();
        Runnable elapsedRunnable = () -> {
            if (finished) elapsedExecutor.shutdown();
            else {
                timeElapsed.setText(String.format("%02d:%02d:%02d", timePlayed / 3600, (timePlayed % 3600) / 60, timePlayed % 60));
                // Integer formatted as:            HH : MM : SS

                timePlayed++; // Increment total time played.
            }
        };

        try (FileReader reader = new FileReader(file)) {
            Console.log("Reading the game file.");
            jsonObject = (JSONObject) new JSONParser().parse(reader);
            reader.close();

            if (jsonObject.containsKey("state") && jsonObject.get("state").equals(true) && jsonObject.containsKey("winner")) {
                // Game already finished state.
                winner = structure.getMove(jsonObject.get("winner").toString());
                Console.log("The game is already finished. The game will be switched to the Dashboard.");

                this.dashboard = new Dashboard(file);
                finished = true;
            } else if (jsonObject.containsKey("nodes") && jsonObject.containsKey("ElapsedTime") && jsonObject.containsKey("mode") && jsonObject.containsKey("opponent")) {
                // Resume game state.
                Console.log("Elements necessary to resume the game are found.");

                JSONArray jsonArray = (JSONArray) jsonObject.get("nodes");
                setupMatrix();
                setGameProgress(jsonArray);

                timeForMove = (long) jsonObject.get("LastMoveTime");
                Console.log("Last move already took " + timeForMove + " seconds.");

                timed = jsonObject.get("mode").equals("timed");

                maxTimeAllowed = (long) NoughtsAndCrosses.getWriter().getJsonKey("maxTime");
                if (timed)
                    Console.log("Maximum time allowed in this Timed game mode will be " + maxTimeAllowed + " seconds.");
                else Console.log("This game is being played in Unlimited Time mode.");

                if (timeForMove >= maxTimeAllowed) {
                    Console.log("Max time allowed is already reached. The game will be switched to the Dashboard.");
                    this.dashboard = new Dashboard(file);
                    finished = true;
                } else if (moves > 0) {
                    elapsedExecutor.scheduleWithFixedDelay(elapsedRunnable, 0, 1, TimeUnit.SECONDS);
                    timerExecutor.scheduleWithFixedDelay(timerRunnable, 0, 1, TimeUnit.SECONDS);
                }

                Console.log("The opponent is " + jsonObject.get("opponent").toString());
                // which opponent log here for future

                jsonArrayX = (JSONArray) jsonObject.get("timeX");
                jsonArrayO = (JSONArray) jsonObject.get("timeO");

                timePlayed = (long) jsonObject.get("ElapsedTime");
                Console.log("Previous time played was " + timePlayed + " seconds.");

                if (jsonObject.containsKey("move")) {
                    move = structure.getMove(jsonObject.get("move").toString());
                    Console.log("The last move was " + structure.getConjugateMove(move));
                } else getCurrentMove();
            } else if (jsonObject.containsKey("mode") && jsonObject.containsKey("opponent")) {
                // Default game state.
                Console.log("The current game is running for the first time.");

                move = structure.randMoveGen(); // Starting move.
                Console.log("The starting move will be " + move.toString());

                timed = jsonObject.get("mode").equals("timed");
                Console.log("The timed setting of the game is " + timed);

                setupMatrix();

                if (timed) {
                    maxTimeAllowed = (long) NoughtsAndCrosses.getWriter().getJsonKey("maxTime");
                    Console.log("Max time allowed in this game will be " + maxTimeAllowed + " seconds.");
                } else Console.log("This game is being played in Unlimited Time mode.");

                Console.log("The opponent is " + jsonObject.get("opponent").toString());
                // Opponent log here

                elapsedExecutor.scheduleWithFixedDelay(elapsedRunnable, 1, 1, TimeUnit.SECONDS);
                timerExecutor.scheduleWithFixedDelay(timerRunnable, 0, 1, TimeUnit.SECONDS);
            } else Console.log("Files necessary to start a game are missing.");
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }

        Button exitButton = new ButtonBuilder("Save and exit").setTextColor(Color.RED)
                .setCssScript("-jfx-button-type: RAISED; -fx-background-color: #760d84; -fx-text-fill: white;")
                .onMouseClick(event -> {
                    elapsedExecutor.shutdown();
                    timerExecutor.shutdown();

                    if (moves > 0) saveCurrentProgress(file);
                    else {
                        Console.log("Deleting the game file as no moves are made.");
                        if (file.delete()) Console.log("Deleted game file successfully.");
                        else Console.log("Could not delete game file");
                    }

                    App.getStage().setScene(App.getScene());
                })
                .setPrefWidth(200)
                .build();

        VBox vBox = new VBox(100);
        vBox.setAlignment(Pos.BOTTOM_CENTER);
        vBox.setPadding(new Insets(0, 0, 150, 0));
        vBox.getChildren().addAll(gameGrid, exitButton);
        root.getChildren().add(vBox);

        // Nodes aligned using StackPane.
        StackPane.setMargin(nextMove, new Insets(40, 0, 0, 11));
        StackPane.setMargin(timeElapsed, new Insets(40, 0, 0, 11));
        StackPane.setMargin(timeLeft, new Insets(10));
        StackPane.setAlignment(nextMove, Pos.TOP_RIGHT);
        StackPane.setAlignment(timeElapsed, Pos.TOP_LEFT);
        StackPane.setAlignment(timeLeft, Pos.BOTTOM_RIGHT);
        root.getChildren().addAll(nextMove, timeElapsed, timeLeft);

        nextMove.setText(move.toString().toUpperCase());
        GraphicsEngine graphicsEngine = new GraphicsEngine();
        graphicsEngine.setRoot(root);
        NoughtsAndCrosses.createSceneBackground(root);

        if (!isGameOver())
            App.getStage().setOnCloseRequest(event -> {
                if (moves > 0) saveCurrentProgress(file);
                else if (file.delete()) Console.log("Deleted game file as no moves were made.");
                else Console.log("Could not delete game file.");
            });
    }

    /**
     * Saves the current game progress.
     *
     * @param file File to save game data to.
     */
    private void saveCurrentProgress(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            Console.log("Saving current game progress.");
            jsonObject.put("ElapsedTime", timePlayed); // Contains the time elapsed time since game start.
            jsonObject.put("nodes", getGameProgress()); // The array of nodes which contains the main game progress.
            jsonObject.put("winner", winner.toString()); // Contains the winner of the game.
            jsonObject.put("state", finished); // Contains if the game is finished yet.
            jsonObject.put("move", move.toString()); // Contains the last move which took place in the game.
            jsonObject.put("timeX", jsonArrayX); // Contains the time taken for each move by X.
            jsonObject.put("timeO", jsonArrayO); // Contains the time taken for each move by O.
            jsonObject.put("LastMoveTime", timeForMove); // Contains how much time had elapsed already on the last move.
            writer.write(jsonObject.toJSONString());
            writer.close();
            Console.log("Saved game progress at " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the JSON Array of current tic tac toe game progress.
     *
     * @return Returns a JSON Array from 0 to n which contains the moves of the
     * game.
     */
    private JSONArray getGameProgress() {
        JSONArray array = new JSONArray();
        for (int i = 0; i < n; i++) { // Rows
            for (int j = 0; j < n; j++) { // Columns
                array.add(nodeMatrix[i][j].getText());
            }
        }

        return array;
    }

    /**
     * Sets the JSON Array of current tic tac toe game progress.
     *
     * @param array The JSON Array from which the game progress should be saved
     *              from.
     */
    private void setGameProgress(JSONArray array) {
        int x = 0;
        for (int i = 0; i < nodeMatrix[0].length; i++) {
            for (int j = 0; j < nodeMatrix[1].length; j++) {
                nodeMatrix[i][j].setText(array.get(x).toString());

                if (!nodeMatrix[i][j].getText().equals(" ")) nodeMatrix[i][j].setDisable(true);

                if (structure.getMove(nodeMatrix[i][j].getText()).equals(States.X))
                    movesX++; // Number of moves which are Crosses.
                else if (structure.getMove(nodeMatrix[i][j].getText()).equals(States.O))
                    movesO++; // Number of moves which are Noughts.

                moves = movesX + movesO; // Add both to get the total number of moves.
                x++;
            }
        }

        Console.log("Previous game nodes were found as " + array);
    }

    /**
     * Gets the next move based on moves already made or generates a random move.
     * Used only when the next move is not specified in JSON of the game file.
     */
    private void getCurrentMove() {
        Console.log("The method to get the next move was triggered. Possibly due to 'move' node not being found in the JSON save file.");

        // Figure out the next move based on the moves already made.
        if (movesX > movesO) move = States.O;
        else if (movesX < movesO) move = States.X;
        else { // Pick up a random move if the moves by both sides are the same.
            move = structure.randMoveGen();
            Console.log("Both moves are equal. Choosing the next move randomly.");
        }

        Console.log("Set " + move.toString().toUpperCase() + " as the current move.");
    }

    /**
     * Sets up each buttons of the game grid matrix and its properties.
     */
    private void setupMatrix() {
        for (int i = 0; i < n; i++) { // Setting up each button and its properties.
            for (int j = 0; j < n; j++) {
                int finalI = i; // Iteration number has to be effectively final for usage in lambda expressions.
                int finalJ = j;
                nodeMatrix[i][j] = new ButtonBuilder(" ")
                        .setFont(Font.font("Arial", FontWeight.BOLD, 32))
                        .setPrefHeight(100)
                        .setPrefWidth(100)
                        .setCssScript("-jfx-button-type: RAISED; -fx-background-color: gold; -fx-text-fill: blue;")
                        .onMouseClick(event -> {
                            nodeMatrix[finalI][finalJ].setText(move.toString());
                            nodeMatrix[finalI][finalJ].setDisable(true);

                            switch (move) {
                                case X:
                                    Console.log("Time taken for X's move " + (movesX + 1) + " was " + timeForMove + "s");
                                    movesX++;
                                    jsonArrayX.add(timeForMove);
                                    move = States.O;
                                    break;
                                case O:
                                    Console.log("Time taken for O's move " + (movesO + 1) + " was " + timeForMove + "s");
                                    movesO++;
                                    jsonArrayO.add(timeForMove);
                                    move = States.X;
                                    break;
                                default:
                                    throw new IllegalStateException("Check the move passed! Move can only be a nought or a cross.");
                            }

                            moves = movesO + movesX;
                            nextMove.setText(move.toString().toUpperCase());

                            timeForMove = 0; // Reset move timer.
                            resetTimer();
                            checkForWin(); // Check if there is a win.
                        })
                        .build();

                gameGrid.addRow(i, nodeMatrix[i][j]); // Add node to game grid.
            }
        }

        gameGrid.setVgap(30);
        gameGrid.setHgap(30);
        gameGrid.setPadding(new Insets(10));
        gameGrid.setAlignment(Pos.CENTER);
        if (NoughtsAndCrosses.getWriter().getJsonKey("theme").equals(Themes.DARK.toString()))
            gameGrid.setGridLinesVisible(true);
    }

    /**
     * Block of code which is executed when a game is finished.
     */
    private synchronized void win() {
        Console.log("A win for " + winner.toString());

        saveCurrentProgress(theFile);
        App.getStage().setOnCloseRequest(null); // Save on close request no longer needed as the game is over.

        this.dashboard = new Dashboard(theFile);
        App.getStage().setScene(dashboard.getScene());
    }

    /**
     * Check if there is a win in the game yet.
     */
    private void checkForWin() {
        States[][] checkWinMoves = structure.getMoveStates(nodeMatrix);
        long timeForAlgorithm = System.nanoTime(); // Used to calculate the time taken for the system to conclude the winner.

        for (int i = 0; i < n; i++) {
            if (checkWinMoves[i][0] == checkWinMoves[i][1] && checkWinMoves[i][1] == checkWinMoves[i][2]) { // Check for win in rows.
                if (checkWinMoves[i][0] != States.NONE) {
                    Console.log("Win found in row: " + (i + 1));
                    winner = structure.getConjugateMove(move); // The last move was the winner.
                    finished = true;
                    Console.log("Time taken for algorithm to check for game result was " + (System.nanoTime() - timeForAlgorithm) + "ns.");
                    win();
                    return;
                }
            }

            if (checkWinMoves[0][i] == checkWinMoves[1][i] && checkWinMoves[1][i] == checkWinMoves[2][i]) { // Check for win in columns.
                if (checkWinMoves[0][i] != States.NONE) {
                    Console.log("Win found in column: " + (i + 1));
                    winner = structure.getConjugateMove(move);
                    finished = true;
                    Console.log("Time taken for algorithm to check for game result was " + (System.nanoTime() - timeForAlgorithm) + "ns.");
                    win();
                    return;
                }
            }
        }

        if (checkWinMoves[0][0] == checkWinMoves[1][1] && checkWinMoves[1][1] == checkWinMoves[2][2]) { // Check for win diagonally.
            if (checkWinMoves[0][0] != States.NONE) {
                Console.log("A win is found diagonally.");
                winner = structure.getConjugateMove(move);
                finished = true;
                Console.log("Time taken for algorithm to check for game result was " + (System.nanoTime() - timeForAlgorithm) + "ns.");
                win();
                return;
            }
        }

        if (checkWinMoves[0][2] == checkWinMoves[1][1] && checkWinMoves[1][1] == checkWinMoves[2][0]) { // Check for win anti-diagonally.
            if (checkWinMoves[0][2] != States.NONE) {
                Console.log("A win is found anti-diagonally.");
                winner = structure.getConjugateMove(move);
                finished = true;
                Console.log("Time taken for algorithm to check for game result was " + (System.nanoTime() - timeForAlgorithm) + "ns.");
                win();
                return;
            }
        }

        if (moves == 9) { // Game draw condition.
            winner = States.NONE;
            Console.log("The game is a draw!");

            finished = true;
            win(); // Runs block of code which switches to the dashboard scene.
        }
    }

    /**
     * @return Is game over
     */
    private boolean isGameOver() {
        return finished;
    }

    /**
     * Resets the time of the Timer Executor.
     * As soon as a move is made the previous timer is stopped and a new one is started.
     */
    private void resetTimer() {
        timeForMove = 0; // Reset time passed.
        timerExecutor.shutdown();
        timerExecutor = Executors.newSingleThreadScheduledExecutor();
        timerExecutor.scheduleWithFixedDelay(timerRunnable, 0, 1, TimeUnit.SECONDS);
    }

    public Scene getScene() {
        if (isGameOver()) return this.dashboard.getScene();
        else return scene;
    }

    /**
     * Contains the possible states of a tic tac toe game.
     * <p>
     * Blank(None), Cross(X) or a Nought(O).
     * </p>
     */
    public enum States {
        NONE,
        X,
        O
    }
}

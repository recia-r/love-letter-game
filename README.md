# Love Letter Game

A simple implementation of the Love Letter card game built with Clojure/ClojureScript.

## Game Rules

Love Letter is a card game where players try to win the affection of a princess by getting their love letter as close to her as possible. The game is played in rounds, and the last player standing wins each round.

### Cards and Abilities

- **Guard (1)**: Guess a non-Guard card in another player's hand. If correct, they are eliminated.
- **Priest (2)**: Look at another player's hand.
- **Baron (3)**: Compare hands with another player. Lower value is eliminated.
- **Handmaid (4)**: Protection from effects until your next turn.
- **Prince (5)**: Force a player to discard their hand and draw a new card.
- **King (6)**: Trade hands with another player.
- **Countess (7)**: Must be discarded if you have King or Prince.
- **Princess (8)**: If discarded, you are eliminated.

### How to Play

1. Each player starts with one card
2. On your turn, draw a card and play one card from your hand
3. Apply the card's effect
4. The round ends when only one player remains or the deck is empty
5. Winner gets an affection token
6. First player to reach the required tokens wins the game

## Running the Game

### Prerequisites

- Java 8+
- Clojure CLI tools
- Node.js (for shadow-cljs)

### Setup

1. Install dependencies:
   ```bash
   clojure -M:dev:test
   ```

2. Start the backend server:
   ```bash
   clojure -M:dev
   ```

3. In another terminal, start the frontend:
   ```bash
   npx shadow-cljs watch demo
   ```

4. Open your browser to `http://localhost:8000`

## Game Flow

1. Enter player names (comma-separated) and click "Start Game"
2. Each player will see their hand of cards
3. On your turn, select a card to play
4. If the card requires a target, select the target player
5. Click "Confirm Play" to execute the action
6. The game automatically progresses through rounds
7. First player to reach the required affection tokens wins!

## Technical Details

- **Backend**: Clojure with Ring HTTP server
- **Frontend**: ClojureScript with Reagent (React wrapper)
- **Build Tool**: shadow-cljs
- **State Management**: Server-side game state with client-side UI state

This is a simplified version for learning purposes, focusing on core game mechanics without complex styling or session management.


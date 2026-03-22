# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multiplayer Love Letter card game built with Clojure (backend) and ClojureScript (frontend). Players join rooms, play cards with special abilities, and compete in best-of-5 rounds.

## Development Commands

**Start backend server (port 8200):**
```
clojure -M:dev
```

**Start frontend dev build (watches for changes):**
```
npx shadow-cljs watch app
```

**Run tests (RCF framework):**
Tests use `hyperfiddle.rcf` with `(rcf/enable!)`. Run via REPL or:
```
clojure -M:test
```
Test files: `test/duck_dynasty/game_test.clj`, `test/duck_dynasty/rooms_test.clj`

**Runtime requirements:** Java 17, Node.js 22.14.0 (see `.tool-versions`)

## Architecture

**Isomorphic game logic** in `src/duck_dynasty/game.cljc` — shared `.cljc` file used by both backend and frontend. Contains all card definitions, game state transitions, turn logic, round/game win conditions.

**Backend** (`src/clj/`):
- `routes.clj` — HTTP-Kit server setup and custom route pattern matching
- `handler.clj` — API endpoint handlers (game actions, room management)
- `rooms.clj` — Room lifecycle (create, join, start, replay) and player management
- `state.clj` — Single global atom holding all room state

**Frontend** (`src/client/`):
- `core.cljs` — App initialization and browser history routing
- `ui.cljs` — Top-level app shell, page dispatch
- `game.cljs` — Game page with 1-second polling for state updates
- `home.cljs` — Lobby: room creation, joining, player name setup
- `state.cljs` — Reagent atoms for client state (page, rooms, player-name)

**Data flow:** Frontend makes fetch requests to `/api/*` endpoints → backend updates global `@state/rooms` atom → returns EDN response → frontend updates local Reagent atoms → re-render.

**State is server-authoritative.** All game state lives in the backend rooms atom. The frontend polls and displays it. No WebSockets — uses HTTP polling.

## Key Game Concepts

- 9 card types (Minion=1, Abbot=2, Rogue=3, Knight=4, Wizard=5, Fool=6, Queen=7, King=9, Princeling=0) with different targeting rules
- Players draw a card, then play one of their two cards
- Some cards are untargetable (protected players), some are self-only
- Round ends when deck is empty or one player remains; game ends at 3 round wins (best-of-5)
- Room states: `:pre-game` → `:in-game` → `:post-game`

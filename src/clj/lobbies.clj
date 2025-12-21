(ns clj.lobbies
  (:require
   [duck-dynasty.game :as dd]))

(defn new-lobby [player-name]
  {:lobby/player-name player-name
   :lobby/game nil})

(defn add-player-to-lobby [lobby player-name]
  (assoc lobby :lobby/player-name player-name))

(defn remove-player-from-lobby [lobby player-name]
  (dissoc lobby :lobby/player-name))

;; will need state - can mutate state or return a new state?

(active-games state player-name)
(open-lobbies state)

(new-game! state player-name)

;; 3 states: lobby, game, end of game

(lobby-state state lobby-id)  ;; :lobby.state/in-lobby :lobby.state/in-game :lobb.state/end-of-game
(player-in-lobby? state player-name lobby-id)
(add-player-to-lobby! state player-name lobby-id)
(players-in-lobby state lobby-id)
(start-game! state lobby-id)

(quickstart-game! state player-names) ;; immediately starts

(exists? state lobby-id)

;; is it game-id or lobby-id
;; or something selse?
;; like a session (that starts a lobby?)


;; /  if not "logged in"
;;     show a form to enter their name
;;       when submit, create a cookie for the player (name="Micah")
;;       redirect to /
;;    else
;;     see their active games
;;     see open "lobbies" (games in a certain state)
;;     "new game" button
;;         when submit, create a new lobby and add player to it
;;         redirect to /game/15

;; /game/15
;;    page is polling
;;
;;    if not logged in
;;       show a "you're not logged in" message
;;    if in "lobby" state
;;       if player is not part of lobby
;;          show a button to "join the lobby"
;;          when submit, add player to lobby
;;       else player is part of lobby
;;          show who is in lobby
;;          "start game" button
;;             when submit, swap lobby with new game state
;;    else  --if in game state
;;       if player part of game
;;          show the game
;;       else
;;          show a "you're not part of this game" message
;;   else if in end of game state
;;       show end of game screen, 
;;         w/ "replay" button 
;;         (which immediately creats and starts a new game with the same players)
;;         w/ home button
;;   if game does not exist
;;      show a "game not found" message

;; front end will need two tabs to test
;; how store active games?
;; what state of game determins lobby vs started?
;; how do we "refresh" the state for a game? (poll? long polling? websockets?)
;; when does a game get removed from the active games?

;; games management should be in a separate namespace
;;   active-games state
;;   helper fns (new game, see player's active games, etc.)

;; routes namespace, which talks to the games management namespace and the game namespace

#_{:lobby/players []}
#_{:game/players ["Alice" "Bob"]}

#_{:lobby/players []}
#_{:lobby/players ["Alice" "Bob"]
   :lobby/game {}}

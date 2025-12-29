(ns duck-dynasty.rooms-test
  (:require
   [clj.rooms :as rooms]
   [hyperfiddle.rcf :as rcf]))

(rcf/enable!)
;; will need state - can mutate state or return a new state?

(rcf/tests
 (let [state (rooms/initial-state)]
   (rooms/player-rooms state "Alice")
   := []

   (rooms/joinable-rooms state "Alice")
   := [])


 (let [state (rooms/initial-state)
       state (rooms/create-room-with-initial-player state {:player-name "Alice"})]
   (rooms/player-rooms state "Alice")
   := [{:room/id _
        :room/players #{"Alice"}
        :room/game nil
        :room/state :pre-game}]

   (rooms/joinable-rooms state "Alice")
   := []

   (rooms/joinable-rooms state "Bob")
   := [{:room/id _
        :room/players #{"Alice"}
        :room/game nil
        :room/state :pre-game}]

   (let [room-id (:room/id (first (rooms/player-rooms state "Alice")))
         room (rooms/get-room state {:room-id room-id})]
     (rooms/player-in-room? room "Alice")
     := true

     (rooms/player-in-room? room "Bob")
     := false

     (let [state (rooms/join-room state {:room-id room-id :player-name "Bob"})]
       (rooms/player-rooms state "Bob")
       := [{:room/id _
            :room/players #{"Alice" "Bob"}
            :room/game nil
            :room/state :pre-game}]

       (let [state (rooms/start-game state {:room-id room-id})]
         (:room/state (rooms/get-room state {:room-id room-id}))
         := :in-game

         (let [state (rooms/end-game state {:room-id room-id})]
           (:room/state (rooms/get-room state {:room-id room-id}))
           := :post-game

           (let [state (rooms/replay-game state {:room-id room-id})
                 players (:room/players (rooms/get-room state {:room-id room-id}))
                 game-with-players (filter #(and (= (:room/players %) players) (= (:room/state %) :in-game)) (vals state))]
             (count game-with-players)
             := 1)))))))






(comment

  (exists? state lobby-id))

;; is it game-id or lobby-id
;; or something selse?
;; like a session (that starts a lobby?)

;;  room
;;   pre-game -> in-game -> post-game


;; /  if not "logged in"
;;     show a form to enter their name
;;       when submit, create a cookie for the player (name="Micah")
;;       redirect to /
;;    else
;;     ✅ see rooms they are in
;;     ✅ see open "rooms" (rooms in pre-game state)
;;     "new game" button
;;         when submit, ✅ create a new lobby and add player to it
;;         redirect to /room/15

;; /room/15
;;    page is polling  (poll? long polling? websockets?)
;;
;;    if not logged in
;;       show a "you're not logged in" message
;;    if in "pre-game" state
;;       if player is not part of room
;;          show a button to "join the room"
;;          when submit, add player to room
;;       else player is part of room
;;          show who is in room
;;          "start game" button
;;             when submit, swap room with new game state
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

;; when does a game get removed from the active games?


;; routes namespace, which talks to the games management namespace and the game namespace


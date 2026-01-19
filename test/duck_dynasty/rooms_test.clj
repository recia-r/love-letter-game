(ns duck-dynasty.rooms-test
  (:require
   [clj.rooms :as rooms]
   [hyperfiddle.rcf :as rcf]))

(rcf/enable!)

(rcf/tests
 (let [state {}]
   (rooms/player-rooms state "Alice")
   := []

   (rooms/joinable-rooms state "Alice")
   := [])


 (let [state {}
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

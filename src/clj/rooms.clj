(ns clj.rooms
  (:require
   [duck-dynasty.game :as dd]))

(defn initial-state [] {})

;; ROOM

(defn new-room []
  {:room/id (random-uuid)
   :room/players #{}
   :room/game nil
   :room/state :pre-game}) ;; :pre-game :in-game :post-game

(defn add-player-to-room [room player-name]
  (update room :room/players conj player-name))

(defn player-in-room? [room player-name]
  (contains? (:room/players room) player-name))


;; STATE

(defn add-room [state room]
  (assoc state (:room/id room) room))

(defn create-room-with-initial-player [state {:keys [player-name]}]
  (let [room (-> (new-room)
                 (add-player-to-room player-name))]
    (add-room state room)))

(defn player-rooms [state player-name]
  (->> state
       vals
       (filter #(not= (:room/state %) :post-game))
       (filter #(player-in-room? % player-name))))

(defn joinable-rooms [state player-name]
  (->> state
       vals
       (filter #(= (:room/state %) :pre-game))
       (filter #(not (player-in-room? % player-name)))))

(defn get-room [state {:keys [room-id]}]
  (get state room-id))

(defn join-room [state {:keys [room-id player-name]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :pre-game)]}
  (update state room-id add-player-to-room player-name))

(defn start-game [state {:keys [room-id game-state]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :pre-game)]}
  (update state room-id assoc :room/state :in-game :room/game game-state))

(defn end-game [state {:keys [room-id]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :in-game)]}
  (update state room-id assoc :room/state :post-game))

(defn replay-game
  "Start game-state in a new room with the same players, and point the old room at it.
   If the old room already has a next room (another player replayed first), do nothing."
  [state {:keys [room-id game-state]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :post-game)]}
  (let [old-room (get-room state {:room-id room-id})]
    (if (:room/next-room-id old-room)
      state
      (let [room (reduce add-player-to-room (new-room) (:room/players old-room))]
        (-> state
            (add-room room)
            (start-game {:room-id (:room/id room) :game-state game-state})
            (assoc-in [room-id :room/next-room-id] (:room/id room)))))))


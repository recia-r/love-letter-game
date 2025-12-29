(ns clj.rooms
  (:require
   [duck-dynasty.game :as dd]))



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

(defn initial-state []
  {})

(defn add-room [state room]
  (assoc state (:room/id room) room))

(defn create-room-with-initial-player [state {:keys [player-name]}]
  (let [room (-> (new-room)
                 (add-player-to-room player-name))]
    (add-room state room)))

(defn player-rooms [state player-name]
  (->> state
       vals
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

(defn start-game [state {:keys [room-id]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :pre-game)]}
  (update state room-id assoc :room/state :in-game))

(defn end-game [state {:keys [room-id]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :in-game)]}
  (update state room-id assoc :room/state :post-game))

(defn replay-game [state {:keys [room-id]}]
  {:pre [(= (:room/state (get-room state {:room-id room-id})) :post-game)]}
  (let [players (:room/players (get-room state {:room-id room-id}))
        room (reduce add-player-to-room (new-room) players)]
    (-> state
        (add-room room)
        (start-game {:room-id (:room/id room)}))))

;; HTTP HANDLER

(defonce state (atom (initial-state)))

#_(defn handler [request]
  
  ;; add handler for user-info (lists rooms a a user in  in or can join)
 ;; add handler for room-info (for a specific room)

   (and (string/starts-with? (:uri request) "/api/rooms/") 
        (= (:request-method request) :post))
  (let [params (:params request)
        f (case (:fn params)
            "create-room-with-initial-player" create-room-with-initial-player
            "join-room" join-room
            "start-game" start-game
            "end-game" end-game
            "replay-game" replay-game)]
    (swap! state f params)
    {:status 200
     :headers {"Content-Type" "application/edn"}
     }))
  

  ;; fetch("/api/rooms/", {:fn "create-room-with-initial-player", :player "Alice"})
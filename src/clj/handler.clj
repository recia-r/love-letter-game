(ns clj.handler
  (:require
   [duck-dynasty.game :as dd]
   [clj.rooms :as rooms]
   [ring.util.response :refer [file-response]]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clj.state :as state]))


;; Static file handlers
(defn serve-index [_request]
  (file-response "public/index.html"))

(defn serve-main-js [_request]
  (file-response "target/public/app/main.js"))

(defn serve-main-css [_request]
  (file-response "public/main.css"))

(defn serve-card-image [request]
  (let [filename (str/replace (:uri request) #"^/card/" "")
        image-path (str "resources/cardimage/" filename)]
    (file-response image-path)))

(defn return-game-state []
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (pr-str @state)})

;; API handlers
(defn get-game-state [{:strs [room-id]}]
  (return-game-state room-id))


(defn play-card [{:strs [card target guessed-value player room-id]}]
  (swap! state/rooms update-in [room-id :room/game]
         dd/play-card
         player
         (dd/card-by-value (parse-long card))
         {:target-player-name target
          :guessed-card-value (when guessed-value (parse-long guessed-value))})
  (return-game-state room-id))

(defn draw-card [request]
  (let [{:strs [player]} (:params request)]
    (swap! state dd/draw-card player)
    (return-game-state)))

;; Room handlers
(defn return-rooms-state []
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (pr-str @rooms/state)})

(defn create-room [request]
  (let [{:strs [player-name]} (:params request)]
    (swap! rooms/state rooms/create-room-with-initial-player {:player-name player-name})
    (return-rooms-state)))

(defn join-room [request]
  (let [{:strs [room-id player-name]} (:params request)]
    (swap! rooms/state rooms/join-room {:room-id (java.util.UUID/fromString room-id) :player-name player-name})
    (return-rooms-state)))

(defn start-room-game [{:strs [room-id]}]
  (let [room-id (java.util.UUID/fromString room-id)
        player-names (:room/players (rooms/get-room @state/rooms {:room-id room-id}))]
    (swap! state/rooms rooms/start-game {:room-id room-id
                                         :game-state (dd/new-game player-names (dd/create-deck))})
    (return-game-state room-id)))

(defn end-room-game [request]
  (let [{:strs [room-id]} (:params request)]
    (swap! rooms/state rooms/end-game {:room-id (java.util.UUID/fromString room-id)})
    (return-rooms-state)))

(defn replay-room-game [request]
  (let [{:strs [room-id]} (:params request)]
    (swap! rooms/state rooms/replay-game {:room-id (java.util.UUID/fromString room-id)})
    (return-rooms-state)))

(defn get-room-info [request]
  (let [uri (:uri request)
        room-id-str (last (str/split uri #"/"))
        room-id (java.util.UUID/fromString room-id-str)
        room (rooms/get-room @rooms/state {:room-id room-id})]
    (if room
      {:status 200
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str room)}
      {:status 404
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str {:error "Room not found"})})))

(defn get-player-rooms [request]
  (let [uri (:uri request)
        player-name (last (str/split uri #"/"))
        player-rooms-list (rooms/player-rooms @rooms/state player-name)]
    {:status 200
     :headers {"Content-Type" "application/edn"
               "Access-Control-Allow-Origin" "*"}
     :body (pr-str player-rooms-list)}))

(defn get-joinable-rooms [request]
  (let [uri (:uri request)
        player-name (last (str/split uri #"/"))
        joinable-rooms-list (rooms/joinable-rooms @rooms/state player-name)]
    {:status 200
     :headers {"Content-Type" "application/edn"
               "Access-Control-Allow-Origin" "*"}
     :body (pr-str joinable-rooms-list)}))

#_(defn handler [request]
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
       :headers {"Content-Type" "application/edn"}}))
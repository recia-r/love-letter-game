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

(defn serve-card-image [{:strs [value]}]
  (let [filename (str value ".jpg")
        image-path (str "resources/cardimage/" filename)]
    (file-response image-path)))

(defn return-game-state [room-id]
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (pr-str (:room/game (rooms/get-room @state/rooms {:room-id room-id})))})

;; API handlers
(defn get-game-state [{:strs [room-id]}]
  (return-game-state (java.util.UUID/fromString room-id)))

(defn play-card [{:strs [card target guessed-value user-name room-id]}]
  (let [room-id (java.util.UUID/fromString room-id)]
    (swap! state/rooms update-in [room-id :room/game]
           dd/play-card
           user-name
           (dd/card-by-value (parse-long card))
           {:target-player-name target
            :guessed-card-value (when guessed-value (parse-long guessed-value))})
    (return-game-state room-id)))

(defn draw-card [{:strs [user-name room-id]}]
  (let [room-id (java.util.UUID/fromString room-id)]
    (swap! state/rooms update-in [room-id :room/game] dd/draw-card user-name)
    (return-game-state room-id)))

;; Room handlers
(defn return-rooms-state []
  ;; TODO probably don't want to be returning all of rooms/state
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (pr-str @state/rooms)})

(defn create-room [{:strs [user-name]}]
  (swap! state/rooms rooms/create-room-with-initial-player {:player-name user-name})
  (return-rooms-state))

(defn join-room [{:strs [room-id user-name]}]
  (swap! state/rooms rooms/join-room {:room-id (java.util.UUID/fromString room-id) :player-name user-name})
  (return-rooms-state))

(defn start-room-game [{:strs [room-id]}]
  (let [room-id (java.util.UUID/fromString room-id)
        player-names (:room/players (rooms/get-room @state/rooms {:room-id room-id}))]
    (swap! state/rooms rooms/start-game {:room-id room-id
                                         :game-state (dd/new-game player-names (dd/create-deck))})
    (return-rooms-state)))

(defn end-room-game [{:strs [room-id]}]
  (swap! state/rooms rooms/end-game {:room-id (java.util.UUID/fromString room-id)})
  (return-rooms-state))

(defn replay-room-game [{:strs [room-id]}]
  (swap! state/rooms rooms/replay-game {:room-id (java.util.UUID/fromString room-id)})
  (return-rooms-state))

(defn get-room-info [{:strs [room-id]}]
  (let [room-id (java.util.UUID/fromString room-id)
        room (rooms/get-room @state/rooms {:room-id room-id})]
    (if room
      {:status 200
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str room)}
      {:status 404
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str {:error "Room not found"})})))

(defn get-player-rooms [{:strs [user-name]}]
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (pr-str (rooms/player-rooms @state/rooms user-name))})

(defn get-joinable-rooms [{:strs [user-name]}]
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (pr-str (rooms/joinable-rooms @state/rooms user-name))})


(defn set-user-name [{:strs [user-name]}]
  {:status 200
   :cookies {"dd-user-name" {:value user-name
                             :path "/"}}})
(ns clj.handler
  (:require
   [duck-dynasty.game :as dd]
   [ring.util.response :refer [file-response]]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defonce state (atom nil))

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
(defn get-game-state [_request]
  (return-game-state))

(defn start-game [request]
  (let [request (assoc-in request [:params :players] "Micah,Recia")
        {:strs [players]} (:params request)
        player-names (vec (str/split players #","))]
    (reset! state (dd/new-game player-names (dd/create-deck)))
    (return-game-state)))

;; TODO each of these handler fns should just call one fn from game.cljc

(defn play-card [request]
  (let [{:strs [card target guessed-value player]} (:params request)]
    (swap! state dd/play-card
           player
           (dd/card-by-value (parse-long card))
           {:target-player-name target
            :guessed-card-value (parse-long guessed-value)})
    (return-game-state)))

(defn draw-card [request]
  (let [{:strs [player]} (:params request)]
    (swap! state dd/draw-card player)
    (return-game-state)))

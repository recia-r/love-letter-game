(ns clj.handler
  (:require
   [duck-dynasty.game :as dd]
   [org.httpkit.server :as http]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.util.response :refer [file-response]]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defonce state (atom nil))

(defn handler [request]
  (cond
    ;; TODO - use a single public/* type end point for all static files
    (and (= (:uri request) "/") (= (:request-method request) :get))
    (file-response "public/index.html")

    (and (= (:uri request) "/app/main.js") (= (:request-method request) :get))
    (file-response "target/public/app/main.js")

    (and (= (:uri request) "/main.css") (= (:request-method request) :get))
    (file-response "public/main.css")

    (and (str/starts-with? (:uri request) "/card/") (= (:request-method request) :get))
    (let [filename (str/replace (:uri request) #"^/card/" "") 
          image-path (str "resources/cardimage/" filename)]
      (file-response image-path))

    (and (= (:uri request) "/api/game-state") (= (:request-method request) :get))
    {:status 200
     :headers {"Content-Type" "application/edn"
               "Access-Control-Allow-Origin" "*"}
     :body (pr-str @state)}

    (and (= (:uri request) "/api/start-game") (= (:request-method request) :post))
    (let [params (:params request)
          players-str (get params "players" "Alice,Bob")
          player-names (vec (str/split players-str #","))]
      (reset! state (dd/new-game player-names (dd/create-deck)))
      (swap! state dd/draw-card (first player-names))
      {:status 200
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str @state)})

    (and (= (:uri request) "/api/play-card") (= (:request-method request) :post))
    (let [params (:params request)
          player-name (get params "player")
          card-value (edn/read-string (get params "card"))
          card (dd/card-by-value card-value)
          target-player-name (get params "target")
          guessed-value (get params "guessed-value")
          extra-args (cond-> {}
                       target-player-name (assoc :target-player-name target-player-name)
                       guessed-value (assoc :guessed-card-value (edn/read-string guessed-value)))]
      (swap! state dd/play-card player-name card extra-args)
      (let [next-player (:state/current-player @state)
            next-hand (dd/player-hand @state next-player)]
        (when (= (count next-hand) 1)
          (swap! state dd/draw-card next-player)))
      {:status 200
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str @state)})

    (and (= (:uri request) "/api/draw-card") (= (:request-method request) :post))
    (let [params (:params request)
          player-name (get params "player")]
      (swap! state dd/draw-card player-name)
      {:status 200
       :headers {"Content-Type" "application/edn"
                 "Access-Control-Allow-Origin" "*"}
       :body (pr-str @state)})

    (= (:request-method request) :options)
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body ""}

    :else
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Page not found"}))

(def wrapped-app (-> handler
                     wrap-multipart-params
                     wrap-params))


#_(server)
#_(def server (http/run-server #'wrapped-app {:port 8200}))

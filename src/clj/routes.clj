(ns clj.routes
  (:require
   [clj.handler :as handlers]
   [clojure.string :as str]
   [org.httpkit.server :as http]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]))

(defn match-route [request]
  (let [method (:request-method request)
        uri (:uri request)
        _ (println "uri" uri)]
    (cond
      ;; Static file routes
      (and (= method :get) (= uri "/"))
      handlers/serve-index

      (and (= method :get) (str/starts-with? uri "/room/"))
      handlers/serve-index

      (and (= method :get) (= uri "/app/main.js"))
      handlers/serve-main-js

      (and (= method :get) (= uri "/main.css"))
      handlers/serve-main-css

      (and (= method :get) (= uri "/card"))
      handlers/serve-card-image

      ;; PLAYER routes

      (and (= method :post) (= uri "/api/user/set-name"))
      handlers/set-user-name

      ;; GAME routes
      (and (= method :get) (= uri "/api/game-state"))
      handlers/get-game-state

      (and (= method :post) (= uri "/api/play-card"))
      handlers/play-card

      (and (= method :post) (= uri "/api/draw-card"))
      handlers/draw-card

      ;; Room routes
      (and (= method :post) (= uri "/api/rooms/create"))
      handlers/create-room

      (and (= method :post) (= uri "/api/rooms/join"))
      handlers/join-room

      (and (= method :post) (= uri "/api/rooms/start-game"))
      handlers/start-room-game

      (and (= method :post) (= uri "/api/rooms/end-game"))
      handlers/end-room-game

      (and (= method :post) (= uri "/api/rooms/replay"))
      handlers/replay-room-game

      (and (= method :get) (str/starts-with? uri "/api/rooms/room/"))
      handlers/get-room-info

      (and (= method :get) (= uri "/api/rooms/player"))
      handlers/get-player-rooms

      (and (= method :get) (= uri "/api/rooms/joinable"))
      handlers/get-joinable-rooms

      :else nil)))

(defn app [request]
  (if-let [handler-fn (match-route request)]
    (handler-fn (assoc (:params request) "user-name" (get-in request [:cookies "dd-user-name" :value])))
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Page not found"}))


;;;;;;;;;;;;;;;;;
;; Main
;;;;;;;;;;;;;;;;;

(def wrapped-app (-> app
                     wrap-multipart-params
                     wrap-params
                     wrap-cookies))


(defonce server (atom nil))

(defn -main []
  (when @server
    (.stop @server))
  (reset! server (http/run-server #'wrapped-app {:port 8200})))

#_(-main)

#_(.stop @server)

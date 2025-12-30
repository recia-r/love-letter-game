(ns clj.routes
  (:require
   [clj.handler :as handlers]
   [clojure.string :as str]
   [org.httpkit.server :as http]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]))

(defn match-route [request]
  (let [method (:request-method request)
        uri (:uri request)]
    (cond
      ;; Static file routes
      (and (= method :get) (= uri "/"))
      [:get "/" handlers/serve-index]

      (and (= method :get) (= uri "/app/main.js"))
      [:get "/app/main.js" handlers/serve-main-js]

      (and (= method :get) (= uri "/main.css"))
      [:get "/main.css" handlers/serve-main-css]

      (and (= method :get) (str/starts-with? uri "/card/"))
      [:get "/card/*" handlers/serve-card-image]

      ;; API routes
      (and (= method :get) (= uri "/api/game-state"))
      [:get "/api/game-state" handlers/get-game-state]

      (and (= method :post) (= uri "/api/start-game"))
      [:post "/api/start-game" handlers/start-game]

      (and (= method :post) (= uri "/api/play-card"))
      [:post "/api/play-card" handlers/play-card]

      (and (= method :post) (= uri "/api/draw-card"))
      [:post "/api/draw-card" handlers/draw-card]

      :else nil)))

(defn app [request]
  (if-let [[_ _ handler-fn] (match-route request)]
    (handler-fn request)
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Page not found"}))


;;;;;;;;;;;;;;;;;
;; Main
;;;;;;;;;;;;;;;;;

(def wrapped-app (-> app
                     wrap-multipart-params
                     wrap-params))


#_(server)
#_(def server (http/run-server #'wrapped-app {:port 8200}))
#!/usr/bin/env clojure

(require '[clj.game :as game])
(require '[org.httpkit.server :as http])

(println "Starting Love Letter server...")

(def server (http/run-server #'game/wrapped-app {:port 8000}))

(println "Love Letter server running on http://localhost:8000")
(println "Press Ctrl+C to stop the server")

;; Keep the server running
(while true
  (Thread/sleep 1000))

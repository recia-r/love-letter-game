#!/usr/bin/env clojure

(require '[clj.game :as game])
(require '[org.httpkit.server :as http])

(println "Starting Love Letter server for testing...")

(def server (http/run-server #'game/wrapped-app {:port 8000}))

(println "Love Letter server running on http://localhost:8000")
(println "Testing endpoints...")

;; Test the server is running
(Thread/sleep 1000)

;; Test game state endpoint
(println "\nTesting /api/game-state...")
(-> (http/get "http://localhost:8000/api/game-state")
    (.then #(println "Game state response:" %))
    (.catch #(println "Error:" %)))

;; Test init default endpoint
(println "\nTesting /api/init-default...")
(-> (http/post "http://localhost:8000/api/init-default")
    (.then #(println "Init default response:" %))
    (.catch #(println "Error:" %)))

(println "\nServer is running. Press Ctrl+C to stop.")
(println "You can now test the frontend!")

;; Keep the server running
(while true
  (Thread/sleep 1000))

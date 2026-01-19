(ns client.ui
  (:require
   [client.state :as state]
   [client.game :as game]
   [client.home :as home]))

(defn init [])

(defn app []
  (case (first @state/page)
    :page/game [game/game-page @state/page]
    :page/home [home/home-page]))

(ns clj.state
  (:require
   [clj.rooms :as rooms]))

(defonce rooms (atom (rooms/initial-state)))
#_(reset! rooms nil)

#_@rooms

(comment
  {:uuid5 {:room/id :uuid5
           :room/players #{"Micah" "Recia"}
           :room/game {:state/players ["Micah" "Recia"]
                       :state/current-player "Micah"
                       :state/deck (dd/create-deck)
                       :state/hidden-card nil
                       :state/player-hands {}
                       :state/protected-players #{}
                       :state/discard-pile []
                       :state/rounds 10
                       :state/round 1
                       :state/round-winners []
                       :state/game-winner nil
                       :state/abbot-reveal nil}
           :room/state :pre-game}}
  )
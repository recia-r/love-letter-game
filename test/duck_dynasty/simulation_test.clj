(ns duck-dynasty.game-test
  (:require
   [clojure.pprint :as pp]
   [duck-dynasty.game :as dd]
   [malli.instrument :as mi]))

#_(mi/collect!)
(mi/instrument!)

(defn fake-deck [& values]
  (let [initial-cards (map dd/card-by-value values)
        value-counts (frequencies initial-cards)]
    (vec (concat initial-cards
                 (let [card-list (for [card dd/cards
                                       _ (range (- (:card/count card)
                                                   (get value-counts card 0)))]
                                   card)]
                   (shuffle card-list))))))

(let [state (dd/new-game ["Alice" "Bob" "Charlie"] (fake-deck
                                                    1 ;; alice deal
                                                    5 ;; bob deal
                                                    1 ;; charlie deal
                                                    0 ;; hidden
                                                    3 ;; alice draw
                                                    ))
      state (dd/draw-card state "Alice")
      card-to-play (dd/card-by-value 1)]
  (-> state
      (assoc :state/hidden-card nil) ;; MALLI SHOULD THROW
      (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                          :guessed-card-value 5})))
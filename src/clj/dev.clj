(ns clj.dev
  (:require
   [duck-dynasty.game :as dd]
   [hyperfiddle.rcf :as rcf]))

(rcf/enable!)

(defn fake-deck [& values]
  (let [initial-cards (map dd/card-by-value values)
        value-counts (frequencies initial-cards)]
    (vec (concat initial-cards
                 (let [card-list (for [card dd/cards
                                       _ (range (- (:card/count card)
                                                   (get value-counts card 0)))]
                                   card)]
                   (shuffle card-list))))))

(map :card/value (fake-deck 6 1 1))


(rcf/tests
 (dd/eliminated-player? {:state/player-hands {"Alice" [1]}} "Alice") := false
 (dd/eliminated-player? {:state/player-hands {"Alice" [1]}} "Bob") := true

 (dd/eliminate-player {:state/player-hands {"Alice" [1] "Bob" [2]}} "Bob") := {:state/player-hands {"Alice" [1]}}

 (dd/remove-card-from-hand-upon-play {:state/player-hands {"Alice" [1 1] "Bob" [2]}} "Alice" 1) := {:state/player-hands {"Alice" [1] "Bob" [2]}}
 (dd/add-card-to-discard-pile {:state/discard-pile [1]} 2) := {:state/discard-pile [1 2]}

 "Eliminating Player"
 (-> (dd/new-game ["Alice"] (dd/create-deck))
     (dd/eliminated-player? "Alice"))
 := false

 (-> (dd/new-game ["Alice"] (dd/create-deck))
     (dd/eliminate-player "Alice")
     (dd/eliminated-player? "Alice"))
 := true

 "Swapping Cards in Hands"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice draw
                                   5 ;; bob draw 
                                   ))
     (dd/swap-cards-in-hands "Alice" "Bob")
     (dd/player-hand "Alice"))
 := [(dd/card-by-value 5)]

 "Playing Minion E2E"
 (let [state (dd/new-game ["Alice" "Bob"] (dd/create-deck))
       state (dd/draw-card state "Alice")
       ;; force Alice to have two minions to test minion logic
       state (assoc-in state [:state/player-hands "Alice"] [(dd/card-by-value 1) (dd/card-by-value 1)])
       state (assoc-in state [:state/player-hands "Bob"] [(dd/card-by-value 5)])
       alice-hand (dd/player-hand state "Alice")
       card-to-play (first alice-hand)]

   "Eliminates when matching"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 5})
       (dd/eliminated-player? "Bob"))
   := true

   "Does not eliminate when not matching"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 7})
       (dd/eliminated-player? "Bob"))
   := false

   "Does not allow guessing minion"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 1}))
   :throws java.lang.AssertionError

   "after playing card, it is removed from the hand"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 5})
       (dd/player-hand "Alice"))
   := [(dd/card-by-value 1)]

   "Correct guess ends game"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 5})
       (dd/game-over?))
   := true

   "Winner is the only active player"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 5})
       (dd/game-winner))
   := "Alice")

 "Playing Rogue, Knight, Wizard, Fool, Queen, King"
 (let [state (dd/new-game ["Alice" "Bob"] (dd/create-deck))
       state (dd/draw-card state "Alice")]

   "playing rogue eliminates target player when their card has lower value"
   (-> state
       (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 3) (dd/card-by-value 5)])
       (assoc-in [:state/player-hands "Bob"] [(dd/card-by-value 2)])
       (dd/play-card "Alice" (dd/card-by-value 3) {:target-player-name "Bob"})
       (dd/eliminated-player? "Bob"))
   := true

   "playing knight protects from effects until next turn"
   (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                     4 ;; alice deal
                                     2 ;; bob deal
                                     ))
       (dd/draw-card "Alice")
       (dd/play-card "Alice" (dd/card-by-value 4) {})
       (dd/draw-card "Bob")
       (dd/play-card "Bob" (dd/card-by-value 2) {:target-player-name "Alice"}))
   :throws java.lang.AssertionError

   "playing wizard discards target player's card and draws a new card"
   (-> (dd/new-game ["Alice" "Bob"] (dd/create-deck))
       (dd/draw-card "Alice")
       (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 3) (dd/card-by-value 5)])
       (assoc-in [:state/deck 0] (dd/card-by-value 1)) ;; force next card to be 1
       (dd/play-card "Alice" (dd/card-by-value 5) {:target-player-name "Bob"})
       (dd/player-hand "Bob"))
   := [(dd/card-by-value 1)]

   "Can't play card that is not in hand"
   (-> (dd/new-game ["Alice" "Bob"] (dd/create-deck))
       (dd/draw-card "Alice")
       (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 1) (dd/card-by-value 2)])
       (dd/play-card "Alice" (dd/card-by-value 7) {:target-player-name "Bob"
                                                   :guessed-card-value 5}))
   :throws java.lang.AssertionError

   "Player advances to next player after playing card"
   (-> (dd/new-game ["Alice" "Bob"] (dd/create-deck))
       (dd/draw-card "Alice")
       (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 1) (dd/card-by-value 2)])
       (dd/play-card "Alice" (dd/card-by-value 2) {:target-player-name "Bob"})
       (:state/current-player))
   := "Bob"

   "Playing full round of game"
   (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                     1 ;; alice draw
                                     5 ;; bob draw
                                     4 ;; hidden
                                     2 ;; alice play 1
                                     2 ;; bob play 1
                                     ))
       ;; play 1
       (dd/draw-card  "Alice")
       #_(assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 1) (dd/card-by-value 2)]) ;; force Alice to have this hand
       (dd/play-card "Alice" (dd/card-by-value 2) {:target-player-name "Bob"})
       ;; play 2
       (dd/draw-card "Bob")
       #_(assoc-in [:state/player-hands "Bob"] [(dd/card-by-value 5) (dd/card-by-value 2)]) ;; force Bob to have this hand
       (dd/play-card "Bob" (dd/card-by-value 2) {:target-player-name "Alice"})
       ;; play 3
       (dd/play-card "Alice" (dd/card-by-value 1) {:target-player-name "Bob"
                                                   :guessed-card-value 5})
       (dd/game-winner))
   := "Alice"

   nil))
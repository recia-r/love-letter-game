(ns duck-dynasty.game-test
  (:require
   [clojure.pprint :as pp]
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

(rcf/tests
 (dd/eliminated-player? {:state/player-hands {"Alice" [1]}} "Alice") := false
 (dd/eliminated-player? {:state/player-hands {"Alice" [1]}} "Bob") := true

 (dd/eliminate-player {:state/player-hands {"Alice" [1] "Bob" [2]}} "Bob") := {:state/player-hands {"Alice" [1]}}

 (dd/remove-card-from-hand-upon-play {:state/player-hands {"Alice" [1 1] "Bob" [2]}} "Alice" 1) := {:state/player-hands {"Alice" [1] "Bob" [2]}}
 (dd/add-card-to-discard-pile {:state/discard-pile [1]} 2) := {:state/discard-pile [1 2]} 

 (dd/players-with-highest-value-card {:state/player-hands {"Alice" [{:card/end-value 5}]
                                                           "Bob" [{:card/end-value 4}]
                                                           "Charlie" [{:card/end-value 5}]}}) := ["Alice" "Charlie"]

 (dd/players-with-highest-value-card {:state/player-hands {"Alice" [{:card/end-value 7}]
                                                           "Bob" [{:card/end-value 5}]
                                                           "Charlie" [{:card/end-value 9}]}}) := ["Charlie"]

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
                                   1 ;; alice deal
                                   5 ;; bob deal
                                   ))
     (dd/swap-cards-in-hands "Alice" "Bob")
     (dd/player-hand "Alice"))
 := [(dd/card-by-value 5)]

 "Playing Minion E2E"
 (let [state (dd/new-game ["Alice" "Bob" "Charlie"] (fake-deck
                                                     1 ;; alice deal
                                                     5 ;; bob deal
                                                     1 ;; charlie deal
                                                     0 ;; hidden
                                                     3 ;; alice draw
                                                     ))
       state (dd/draw-card state "Alice")
       card-to-play (dd/card-by-value 1)]

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
   := [(dd/card-by-value 3)]

   "Correct guess ends round"
   (-> state
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 5})
       (dd/play-card "Charlie" card-to-play {:target-player-name "Alice"
                                             :guessed-card-value 3})
       :state/round)
   := 2

   "Winner is the only active player"
   (-> state
       (assoc :state/round-win-counts {"Charlie" 2})
       (dd/play-card "Alice" card-to-play {:target-player-name "Bob"
                                           :guessed-card-value 5})
       (dd/play-card "Charlie" card-to-play {:target-player-name "Alice"
                                             :guessed-card-value 3})
       (dd/game-winners))
   := ["Charlie"])

 "playing rogue eliminates target player when their card has lower value"
 (-> (dd/new-game ["Alice" "Bob" "Charlie"] (fake-deck
                                             3 ;; alice deal
                                             2 ;; bob deal
                                             5 ;; charlie deal
                                             1 ;; hidden
                                             5 ;; alice draw 
                                             ))
     (dd/draw-card "Alice")
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
     (dd/play-card "Bob" (dd/card-by-value 2) {:target-player-name "Alice"})
     (get :state/current-player)) 
 := "Alice"

 "playing wizard discards target player's card and draws a new card"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   3 ;; alice deal
                                   2 ;; bob deal
                                   1 ;; hidden
                                   5 ;; alice draw 
                                   1 ;; bob draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 5) {:target-player-name "Bob"})
     (dd/player-hand "Bob"))
 := [(dd/card-by-value 1)] ;; bob should have drawn a 1

 "playing rogue on a tie eliminated no player"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice deal
                                   1 ;; bob deal
                                   1 ;; hidden
                                   3 ;; alice draw
                                   4 ;; bob draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 3) {:target-player-name "Bob"})
     :state/round)
 := 1
 
 "playing fool swaps hands with target player"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice deal
                                   2 ;; bob deal
                                   1 ;; hidden
                                   6 ;; alice draw
                                   5 ;; bob draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 6) {:target-player-name "Bob"})
     (dd/player-hand "Alice"))
 := [(dd/card-by-value 2)] ;; alice should have swapped hands with bob
 
 "playing queen not allowed if player has fool or wizard"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   6 ;; alice deal
                                   2 ;; bob deal
                                   3 ;; hidden
                                   7 ;; alice draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 6) {}))
 :throws java.lang.AssertionError

 "Can't play card that is not in hand"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice deal
                                   2 ;; bob deal
                                   1 ;; hidden
                                   1 ;; alice draw
                                   5 ;; bob draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 7) {:target-player-name "Bob"
                                                 :guessed-card-value 5}))
 :throws java.lang.AssertionError

 "Player advances to next player after playing card"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice deal
                                   2 ;; bob deal
                                   1 ;; hidden
                                   2 ;; alice draw
                                   5 ;; bob draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 2) {:target-player-name "Bob"})
     (:state/current-player))
 := "Bob"

 "Log entry is added after playing a card mid-round"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   2 ;; alice deal
                                   3 ;; bob deal
                                   1 ;; hidden
                                   4 ;; alice draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 2) {:target-player-name "Bob"})
     ;; log 0 is "Round 1 started.", so the play entry is at index 1
     (get-in [:state/log 1 :message/content]))
 := "Alice played 2 - Abbot"

 "Log visibility includes all players"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   2 ;; alice deal
                                   3 ;; bob deal
                                   1 ;; hidden
                                   4 ;; alice draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 2) {:target-player-name "Bob"})
     ;; log 0 is "Round 1 started.", so the play entry is at index 1
     (get-in [:state/log 1 :message/visibility]))
 := #{"Alice" "Bob"}

 "Log persists across round transition (round start/end + play + outcome entries)"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice deal
                                   5 ;; bob deal
                                   0 ;; hidden
                                   1 ;; alice draw
                                   ))
     (dd/draw-card "Alice")
     (dd/play-card "Alice" (dd/card-by-value 1) {:target-player-name "Bob"
                                                 :guessed-card-value 5})
     (get :state/log)
     count)
 ;; Round 1 started / Alice played Minion / Bob eliminated / Round 1 ended / Round 2 started
 := 5

 "Playing full round of game"
 (-> (dd/new-game ["Alice" "Bob"] (fake-deck
                                   1 ;; alice deal
                                   5 ;; bob deal
                                   4 ;; hidden
                                   2 ;; alice draw
                                   2 ;; bob draw
                                   ))
     (dd/draw-card  "Alice")
     (dd/play-card "Alice" (dd/card-by-value 2) {:target-player-name "Bob"})
     (dd/draw-card "Bob")
     (dd/play-card "Bob" (dd/card-by-value 2) {:target-player-name "Alice"})
     (dd/play-card "Alice" (dd/card-by-value 1) {:target-player-name "Bob"
                                                 :guessed-card-value 5})
     :state/round-win-counts)
 := {"Alice" 1})
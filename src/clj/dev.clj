(ns clj.dev
  (:require
   [clj.game :as game]
   [duck-dynasty.game :as dd]
   [hyperfiddle.rcf :as rcf]))

(rcf/enable!)


(rcf/tests
 (dd/eliminated-player? {:state/player-hands {"Alice" [1]}} "Alice") := false
 (dd/eliminated-player? {:state/player-hands {"Alice" [1]}} "Bob") := true

 (dd/eliminate-player {:state/player-hands {"Alice" [1] "Bob" [2]}} "Bob") := {:state/player-hands {"Alice" [1]}}

 (dd/remove-card-from-hand-upon-play {:state/player-hands {"Alice" [1 1] "Bob" [2]}} "Alice" 1) := {:state/player-hands {"Alice" [1] "Bob" [2]}}
 (dd/add-card-to-discard-pile {:state/discard-pile [1]} 2) := {:state/discard-pile [1 2]}

 "Eliminating Player"
 (-> (dd/new-game ["Alice"])
     (dd/eliminated-player? "Alice"))
 := false

 (-> (dd/new-game ["Alice"])
     (dd/eliminate-player "Alice")
     (dd/eliminated-player? "Alice"))
 := true

 "Playing Minion E2E"
 (let [state (dd/new-game ["Alice" "Bob"])
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

 "Playing Rogue, Wizard, Fool, King"
 (let [state (dd/new-game ["Alice" "Bob"])
       state (dd/draw-card state "Alice")]

   "playing rogue eliminates target player when their card has lower value"
   (-> state
       (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 3) (dd/card-by-value 5)])
       (assoc-in [:state/player-hands "Bob"] [(dd/card-by-value 2)])
       (dd/play-card "Alice" (dd/card-by-value 3) {:target-player-name "Bob"})
       (dd/eliminated-player? "Bob"))
   := true

   "playing wizard discards target player's card and draws a new card"
   (-> (dd/new-game ["Alice" "Bob"])
       (dd/draw-card "Alice")
       (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 3) (dd/card-by-value 5)])
       (assoc-in [:state/deck 0] (dd/card-by-value 1)) ;; force next card to be 1
       (dd/play-card "Alice" (dd/card-by-value 5) {:target-player-name "Bob"})
       (dd/player-hand "Bob"))
   := [(dd/card-by-value 1)])


 "Can't play card that is not in hand"
 (-> (dd/new-game ["Alice" "Bob"])
     (dd/draw-card "Alice")
     (assoc-in [:state/player-hands "Alice"] [(dd/card-by-value 1) (dd/card-by-value 2)])
     (dd/play-card "Alice" (dd/card-by-value 7) {:target-player-name "Bob"
                                                 :guessed-card-value 5}))
 :throws java.lang.AssertionError


 nil)







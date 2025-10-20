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

 (dd/remove-card-from-hand {:state/player-hands {"Alice" [1 1] "Bob" [2]}} "Alice" 1) := {:state/player-hands {"Alice" [1] "Bob" [2]}}

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



 nil)







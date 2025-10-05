(ns clj.dev
  (:require
   [clj.game :as game]
   [hyperfiddle.rcf :as rcf]))

(rcf/enable!)

(rcf/tests
 (-> (game/new-game ["Alice"])
     (game/eliminated-player? "Alice"))
 := false

 (-> (game/new-game ["Alice"])
     (game/eliminate-player "Alice")
     (game/eliminated-player? "Alice"))
 := true)
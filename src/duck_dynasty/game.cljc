(ns duck-dynasty.game)

;; DATA
(def cards
  [{:card/value 1 :card/name "Minion"     :card/targeting-rule :others :card/count 5 :card/ability "Guess a non-Minion card in another player's hand. If correct, they are eliminated."}
   {:card/value 2 :card/name "Abbot"      :card/targeting-rule :others :card/count 2 :card/ability "Look at another player's hand."}
   {:card/value 3 :card/name "Rogue"      :card/targeting-rule :others :card/count 2 :card/ability "Compare hands with another player. Lower value is eliminated."}
   {:card/value 4 :card/name "Knight"     :card/targeting-rule nil     :card/count 2 :card/ability "Protection from effects until your next turn."}
   {:card/value 5 :card/name "Wizard"     :card/targeting-rule :all    :card/count 2 :card/ability "Force any player (including yourself) to discard their hand and draw a new card."}
   {:card/value 6 :card/name "Fool"       :card/targeting-rule :others :card/count 1 :card/ability "Trade hands with another player."}
   {:card/value 7 :card/name "Queen"      :card/targeting-rule nil     :card/count 1 :card/ability "Must be discarded if you have Fool or Wizard."}
   {:card/value 9 :card/name "King"       :card/targeting-rule nil     :card/count 1 :card/ability "If discarded, you are eliminated."}
   {:card/value 0 :card/name "Princeling" :card/targeting-rule nil     :card/count 1 :card/ability "At the end of the round, this card's value is 8"}])

(def minion-guessable-card-values #{2 3 4 5 6 7 9 0})

;; MISC

(defn card-by-value [value]
  (first (filter #(= (:card/value %) value) cards)))

(defn create-deck []
  (let [card-list (for [card cards
                        _ (range (:card/count card))]
                    card)]
    (shuffle card-list)))

(defn deal-initial-cards [players]
  (let [deck (create-deck)
        initial-cards (take (count players) deck)
        remaining-deck (drop (count players) deck)
        hidden-card (first remaining-deck)
        final-deck (rest remaining-deck)]
    {:deck final-deck
     :hidden-card hidden-card
     :player-hands (zipmap players (map vector initial-cards))}))

;; STATE SPEC

(def PlayerName :string)

(def Card
  [:map
   [:card/value :int]
   [:card/name :string]
   [:card/has-target? :boolean]
   [:card/count :int]
   [:card/ability :string]])

(def State
  [:map
   [:state/players [:vector PlayerName]]
   [:state/current-player PlayerName]
   [:state/deck [:vector Card]]
   [:state/hidden-card Card]
   ;; just hands of active players
   [:state/player-hands [:map-of PlayerName [:vector {:min 1 :max 2} Card]]]
   [:state/discard-pile [:vector Card]]
   [:state/rounds :int]
   [:state/round :int]
   [:state/round-winners [:vector PlayerName]]
   [:state/game-winner PlayerName]])

;; STATE GETTERS

(defn active-players [state]
  (keys (:state/player-hands state)))

(defn targetable-players [state card-value]
  ;; TODO
  )

(defn player-hand [state player-name]
  (get-in state [:state/player-hands player-name]))

(defn player-card
  "Players other than the current player only ever have one card in their hand"
  [state player-name]
  ;; TODO check that we're calling this with the current player
  (first (player-hand state player-name)))

(defn eliminated-player? [state player-name]
  (nil? (player-card state player-name)))

(defn game-over? [state]
  (let [active-players (active-players state)]
    (= (count active-players) 1)))

(defn game-winner [state]
  (when (game-over? state)
    (first (active-players state))))

;; STATE ACTIONS
;; take state and other parameters
;; return new state

(defn new-game [player-names]
  (let [players (vec player-names)
        initial-setup (deal-initial-cards players)]
    {:state/players players
     :state/current-player (first players)
     :state/deck (:deck initial-setup)
     :state/hidden-card (:hidden-card initial-setup)
     :state/player-hands (:player-hands initial-setup)
     :state/discard-pile []
     :state/rounds 10
     :state/round 1
     :state/round-winners []
     :state/game-winner nil}))

(defn eliminate-player [state player-name]
  (update state :state/player-hands dissoc player-name))

;; assume two cards in hand, and card-to-remove is present in hand
(defn remove-card-from-hand [state player-name card-to-remove]
  (let [[first-card second-card] (player-hand state player-name)
        new-hand (if (= card-to-remove first-card) [second-card] [first-card])]
    (assoc-in state [:state/player-hands player-name] new-hand)))

(defn add-card-to-discard-pile [state card]
  (update state [:state/discard-pile] conj card))

(defn draw-card [state player-name]
  (let [deck (:state/deck state)
        current-hand (get (:state/player-hands state) player-name)
        drawn-card (first deck)
        remaining-deck (rest deck)
        new-hand (conj current-hand drawn-card)]
    (assoc state
           :state/deck remaining-deck
           :state/player-hands (assoc (:state/player-hands state) player-name new-hand))))

(defn play-minion [state _player _card {:keys [target-player-name guessed-card-value]}]
  {:pre [(contains? minion-guessable-card-values guessed-card-value)]}
  (let [target-card-value (:card/value (player-card state target-player-name))]
    (if (= target-card-value guessed-card-value)
      (eliminate-player state target-player-name)
      state)))

(defn play-abbot [state _player _card {:keys [target-player-name]}]
  (let [target-hand (player-hand state target-player-name)]
    (assoc state :state/player-hands (assoc (:state/player-hands state) target-player-name target-hand))))

(defn play-card [state player card extra-args]
  (let [state (add-card-to-discard-pile state card)
        state (remove-card-from-hand state player card)]
    (case (:card/value card)
      1 (play-minion state player card extra-args)
      2 (play-abbot state player card extra-args)
      #_#_3 (play-rogue state player card extra-args)
      #_#_4 nil
      #_#_5 (play-wizard state player card extra-args)
      #_#_6 (play-fool state player card extra-args)
      #_#_7 nil
      #_#_9 (play-king state player card extra-args)
      #_#_0 nil)))

;; 1) end-to-end game (with just two cards implemented), in this namespace and tested
;;     DONE after playing card, it is removed from the hand
;;     DONE is game over? 
;;     DONE who is winner?
;;     rounds
;;     player can only play a card that they have
;;     DONE check if have two of same card, does it remove both  (shouldn't)


;;  2) fix the front end

;;  3) add more cards




;; how do we remember who has a knight in front?
;;   keep track of serperate discaods, fn that is has-knight-in-front?
;;   have a state of player->has-knight-in-front?
;;  also, targetable-players fn

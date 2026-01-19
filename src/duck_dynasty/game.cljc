(ns duck-dynasty.game)

;; DATA
(def cards
  [{:card/value 1 :card/name "Minion"     :card/targeting-rule :others :card/count 5 :card/end-value 1 :card/ability "Guess a non-Minion card in another player's hand. If correct, they are eliminated."}
   {:card/value 2 :card/name "Abbot"      :card/targeting-rule :others :card/count 2 :card/end-value 2 :card/ability "Look at another player's hand."}
   {:card/value 3 :card/name "Rogue"      :card/targeting-rule :others :card/count 2 :card/end-value 3 :card/ability "Compare hands with another player. Lower value is eliminated."}
   {:card/value 4 :card/name "Knight"     :card/targeting-rule nil     :card/count 2 :card/end-value 4 :card/ability "Protection from effects until your next turn."}
   {:card/value 5 :card/name "Wizard"     :card/targeting-rule :all    :card/count 2 :card/end-value 5 :card/ability "Force any player (including yourself) to discard their hand and draw a new card."}
   {:card/value 6 :card/name "Fool"       :card/targeting-rule :others :card/count 1 :card/end-value 6 :card/ability "Trade hands with another player."}
   {:card/value 7 :card/name "Queen"      :card/targeting-rule nil     :card/count 1 :card/end-value 7 :card/ability "Must be discarded if you have Fool or Wizard."}
   {:card/value 9 :card/name "King"       :card/targeting-rule nil     :card/count 1 :card/end-value 9 :card/ability "If discarded, you are eliminated."}
   {:card/value 0 :card/name "Princeling" :card/targeting-rule nil     :card/count 1 :card/end-value 8 :card/ability "At the end of the round, this card's value is 8"}])

(def minion-guessable-card-values #{2 3 4 5 6 7 9 0})

;; MISC

(defn card-by-value [value]
  (first (filter #(= (:card/value %) value) cards)))

(defn create-deck []
  (let [card-list (for [card cards
                        _ (range (:card/count card))]
                    card)]
    (shuffle card-list)))

(defn deal-initial-cards [players deck]
  (let [initial-cards (take (count players) deck)
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
   [:card/targeting-rule [:enum :others :all :nil]]
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
   [:state/protected-players [:set PlayerName]]
   [:state/discard-pile [:vector Card]]
   [:state/rounds :int]
   [:state/round :int]
   [:state/round-winners [:vector PlayerName]]
   [:state/game-winner PlayerName]])

;; STATE GETTERS

(defn active-players
  "List of active players in player order"
  [state]
  (->> state
       :state/players
       ;; :state/player-hands is a map keyed by active players
       ;; so we can use it to filter the original player list for only active players
       (filter (:state/player-hands state))))

(defn targetable-players [state card-value]
  (case (:card/targeting-rule (card-by-value card-value))
    :others (remove #(= % (:state/current-player state)) (active-players state))
    :all (active-players state)
    nil []))

(defn protected-player? [state target-player-name]
  (contains? (:state/protected-players state) target-player-name))

(defn player-hand [state player-name]
  (get-in state [:state/player-hands player-name]))

(defn can-play-queen? [state player-name]
  (let [other-card (first (remove #(= 7 (:card/value %)) (player-hand state player-name)))]
    (contains? #{1 2 3 4 9 0} (:card/value other-card))))

(defn player-card
  "Players other than the current player only ever have one card in their hand"
  [state player-name]
  ;; TODO check that we're calling this with the current player
  (first (player-hand state player-name)))

(defn eliminated-player? [state player-name]
  (nil? (player-card state player-name)))

(defn game-over? [state]
  (or (= (count (active-players state)) 1) ;; only one player left
      (empty? (:state/deck state)))) ;; no cards left in deck

(defn players-with-highest-value-card [state]
  (->> (:state/player-hands state)
       (map (fn [[player-name [card]]] [(:card/end-value card) player-name]))
       (group-by first)
       (apply max-key first)
       second
       (map second)))

(defn game-winners [state]
  (cond
    (empty? (:state/deck state))
    (players-with-highest-value-card state)

    (= (count (active-players state)) 1)
    (active-players state)

    :else nil))


;; STATE ACTIONS
;; take state and other parameters
;; return new state

(defn new-game [player-names deck]
  (let [players (vec player-names)
        initial-setup (deal-initial-cards players deck)]
    {:state/players players
     :state/current-player (first players)
     :state/deck (:deck initial-setup)
     :state/hidden-card (:hidden-card initial-setup)
     :state/player-hands (:player-hands initial-setup)
     :state/protected-players #{}
     :state/discard-pile []
     :state/rounds 10
     :state/round 1
     :state/round-winners []
     :state/game-winner nil
     :state/abbot-reveal nil}))

(defn advance-player [state]
  (->> state
       active-players
       (cycle)
       (drop-while #(not= % (:state/current-player state)))
       (drop 1)
       (first)
       (assoc state :state/current-player)))

(defn eliminate-player [state player-name]
  (update state :state/player-hands dissoc player-name))

;; assume two cards in hand, and card-to-remove is present in hand
(defn remove-card-from-hand-upon-play [state player-name card-to-remove]
  (let [[first-card second-card] (player-hand state player-name)
        new-hand (if (= card-to-remove first-card) [second-card] [first-card])]
    (assoc-in state [:state/player-hands player-name] new-hand)))

(defn add-card-to-discard-pile [state card]
  (update state :state/discard-pile conj card))

(defn remove-single-card-from-hand [state player-name]
  (update state :state/player-hands dissoc player-name))

(defn swap-cards-in-hands [state player-name1 player-name2]
  (let [hand1 (player-hand state player-name1)
        hand2 (player-hand state player-name2)]
    (-> state
        (update :state/player-hands assoc player-name1 hand2)
        (update :state/player-hands assoc player-name2 hand1))))

(defn draw-card [state player-name]
  (let [deck (:state/deck state)
        current-hand (get (:state/player-hands state) player-name)
        drawn-card (first deck)
        remaining-deck (vec (rest deck))
        new-hand (conj current-hand drawn-card)]
    (assoc state
           :state/deck remaining-deck
           :state/player-hands (assoc (:state/player-hands state) player-name new-hand))))

(defn play-minion [state _player-name {:keys [target-player-name guessed-card-value]}]
  {:pre [(contains? minion-guessable-card-values guessed-card-value)
         (contains? (set (targetable-players state 1)) target-player-name)]}
  (let [target-players-card-value (:card/value (player-card state target-player-name))]
    (if (= target-players-card-value guessed-card-value)
      (eliminate-player state target-player-name)
      state)))

(defn play-abbot [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 2)) target-player-name)]}
  (let [target-player-card (player-card state target-player-name)]
    (assoc state :state/abbot-reveal {:abbot-reveal/abbot-player-name player-name
                                      :abbot-reveal/card target-player-card
                                      :abbot-reveal/target-player-name target-player-name}))) ;; TODO when player confirms seeing card, remove the reveal from state

(defn play-rogue [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 3)) target-player-name)]}
  (let [target-player-card (player-card state target-player-name)
        player-card (player-card state player-name)]
    (if (< (:card/value target-player-card) (:card/value player-card))
      (eliminate-player state target-player-name)
      (eliminate-player state player-name))))

(defn play-knight [state player-name _extra-args]
  (update state :state/protected-players conj player-name))

(defn play-wizard [state _player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 5)) target-player-name)]}
  (let [target-player-card (player-card state target-player-name)]
    (-> state
        (remove-single-card-from-hand target-player-name)
        (add-card-to-discard-pile target-player-card)
        (draw-card target-player-name))))

(defn play-fool [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 6)) target-player-name)]}
  (swap-cards-in-hands state player-name target-player-name))

(defn play-queen [state player-name _extra-args]
  {:pre [(can-play-queen? state player-name)]}
  state)

(defn play-king [state player-name _extra-args]
  (eliminate-player state player-name))

(defn play-princeling [state _player-name _extra-args]
  state)

(defn play-card [state player-name card extra-args]
  {:pre [(contains? (set (player-hand state player-name)) card)]}
  (let [state (remove-card-from-hand-upon-play state player-name card)
        state (add-card-to-discard-pile state card)
        state (update state :state/protected-players disj player-name)
        state (if (and (:card/targeting-rule card) (protected-player? state (:target-player-name extra-args)))
                state
                (-> (case (:card/value card)
                      1 (play-minion state player-name extra-args)
                      2 (play-abbot state player-name extra-args)
                      3 (play-rogue state player-name extra-args)
                      4 (play-knight state player-name extra-args)
                      5 (play-wizard state player-name extra-args)
                      6 (play-fool state player-name extra-args)
                      7 (play-queen state player-name extra-args)
                      9 (play-king state player-name extra-args)
                      0 (play-princeling state player-name extra-args))))]
    (advance-player state)))






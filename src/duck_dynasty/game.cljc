(ns duck-dynasty.game 
  (:require
    [clojure.string :as str]))

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

(def best-of 5)

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
        final-deck (vec (rest remaining-deck))]
    {:deck final-deck
     :hidden-card hidden-card
     :player-hands (zipmap players (map vector initial-cards))}))

(defn log [state message]
  (update state :state/log conj message))

;; STATE SPEC

(def PlayerName :string)

(def Card
  [:map
   [:card/value :int]
   [:card/name :string]
   [:card/targeting-rule [:maybe [:enum :others :all]]]
   [:card/count :int]
   [:card/ability :string]])

(def ExtraArgs
  [:map
   [:target-player-name {:optional true} [:maybe PlayerName]]
   [:guessed-card-value {:optional true} [:maybe :int]]])

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
   [:state/last-played [:maybe [:map
                                [:last-played/player-name PlayerName]
                                [:last-played/card Card]]]]
   [:state/last-round-result {:optional true}
    [:map
     [:round-result/round :int]
     [:round-result/reason [:enum :last-player-standing :deck-empty]]
     [:round-result/winners [:sequential PlayerName]]
     [:round-result/final-hands [:map-of PlayerName [:vector Card]]]]]
   [:state/round :int]
   [:state/round-win-counts [:map-of PlayerName :int]]
   [:state/game-winners [:maybe [:vector PlayerName]]]])

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

(defn hand-contains-queen? [state player-name]
  (contains? (set (map :card/value (player-hand state player-name))) 7)) 

(defn player-card
  "Players other than the current player only ever have one card in their hand"
  [state player-name]
  ;; TODO check that we're calling this with the current player
  (first (player-hand state player-name)))

(defn eliminated-player? [state player-name]
  (nil? (player-card state player-name)))

;; Round logic (single round within a game)
(defn round-over? [state]
  (or (= (count (active-players state)) 1) ;; only one player left
      (empty? (:state/deck state)))) ;; no cards left in deck

(defn players-with-highest-value-card [state]
  (->> (:state/player-hands state)
       (map (fn [[player-name [card]]] [(:card/end-value card) player-name]))
       (group-by first)
       (apply max-key first)
       second
       (map second)))

(defn round-winners [state]
  (cond
    (empty? (:state/deck state))
    (players-with-highest-value-card state)

    (= (count (active-players state)) 1)
    (active-players state)

    :else nil))

;; Game logic (best of N rounds)
(defn player-win-counts
  "Round wins for every player, in player order, including players with none."
  [state]
  (map #(get (:state/round-win-counts state) % 0) (:state/players state)))

(defn game-over?
  "After best-of rounds, or earlier once the leader can't be caught.
   A trailing player gains at most one win per remaining round."
  [state]
  (let [[most next-most] (sort > (player-win-counts state))
        rounds-left (- best-of (dec (:state/round state)))]
    (or (<= rounds-left 0)
        (> most (+ next-most rounds-left)))))

(defn game-winners
  "Players with the most round wins; several on a tie."
  [state]
  (when (game-over? state)
    (let [most (apply max (player-win-counts state))]
      (filterv #(= most (get (:state/round-win-counts state) % 0)) (:state/players state)))))
;; STATE ACTIONS
;; take state and other parameters
;; return new state

(defn set-up-round [state deck]
  (let [players (:state/players state)
        initial-setup (deal-initial-cards players deck)]
    (->  state
         (assoc :state/current-player (first players)
                :state/deck (:deck initial-setup)
                :state/hidden-card (:hidden-card initial-setup)
                :state/player-hands (:player-hands initial-setup)
                :state/protected-players #{}
                :state/discard-pile []
                :state/last-played nil
                :state/abbot-reveal nil)
         (log {:message/content (str "Round " (:state/round state) " started.")
               :message/visibility (set (:state/players state))}))))

(defn new-game
  {:malli/schema [:=> [:cat [:sequential PlayerName] [:sequential Card]] State]}
  [player-names deck]
  (-> {:state/players (vec player-names)
       :state/round 1
       :state/round-win-counts {}
       :state/game-winners nil
       :state/log []}
      (set-up-round deck)))

(defn round-result
  "Summary of a finished round: who won, why it ended, and the hands left at the end."
  [state round-winner-names]
  {:round-result/round (:state/round state)
   :round-result/reason (if (= (count (active-players state)) 1)
                          :last-player-standing
                          :deck-empty)
   :round-result/winners round-winner-names
   :round-result/final-hands (:state/player-hands state)})

(defn transition-to-next-round
  "Start the next round after recording the round winners.
   Resets deck, hands, protected players while keeping round-winners and players."
  [state round-winner-names]
  (-> (reduce (fn [s winner] (update-in s [:state/round-win-counts winner] (fnil inc 0)))
              state
              round-winner-names) 
      (log {:message/content (str "Round " (:state/round state) " ended. Winner/s: " (str/join ", " round-winner-names))
            :message/visibility (set (:state/players state))})
      (assoc :state/last-round-result (round-result state round-winner-names))
      (update :state/round inc)
      (set-up-round (create-deck))))

(defn advance-player [state]
  ;; walk all players (not just active ones) so this still works
  ;; when the current player eliminated themselves this turn
  (->> state
       :state/players
       (cycle)
       (drop-while #(not= % (:state/current-player state)))
       (drop 1)
       (filter (:state/player-hands state))
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
  ;; keep an empty vector (rather than dissoc) so a following draw-card conjs onto a vector
  (assoc-in state [:state/player-hands player-name] []))

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

(defn play-minion [state player-name {:keys [target-player-name guessed-card-value]}]
  {:pre [(contains? minion-guessable-card-values guessed-card-value)
         (contains? (set (targetable-players state 1)) target-player-name)]}
  (let [correct? (= (:card/value (player-card state target-player-name)) guessed-card-value)
        guess (str player-name " guessed " target-player-name " has "
                   guessed-card-value " - " (:card/name (card-by-value guessed-card-value)))]
    (-> state
        (cond-> correct? (eliminate-player target-player-name))
        (log {:message/content (if correct?
                                 (str guess ". Correct! " target-player-name " was eliminated")
                                 (str guess ". Wrong guess"))
              :message/visibility (set (:state/players state))}))))

(defn play-abbot [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 2)) target-player-name)]}
  (let [target-card (player-card state target-player-name)]
    (-> state
        (assoc :state/abbot-reveal {:abbot-reveal/abbot-player-name player-name
                                    :abbot-reveal/card target-card
                                    :abbot-reveal/target-player-name target-player-name})
        (log {:message/content (str player-name " saw " target-player-name "'s card: " (:card/name target-card))
              :message/visibility #{player-name}}))))

(defn play-rogue [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 3)) target-player-name)]}
  (let [target-card (player-card state target-player-name)
        current-card (player-card state player-name)
        eliminated (cond 
                     (< (:card/value target-card) (:card/value current-card)) target-player-name
                     (> (:card/value target-card) (:card/value current-card)) player-name)
        outcome (if eliminated
                  (str eliminated " was eliminated")
                  "No player was eliminated")
        state (cond-> state eliminated (eliminate-player eliminated))
        ;; only the two players see the compared cards,
        ;; unless this play ended the round
        reveal-to (if (round-over? state)
                    (set (:state/players state))
                    #{player-name target-player-name})
        others (into #{} (remove reveal-to) (:state/players state))]
    (-> state
        (log {:message/content (str player-name "'s " (:card/value current-card) " - " (:card/name current-card)
                                    " vs " target-player-name "'s " (:card/value target-card) " - " (:card/name target-card)
                                    ". " outcome)
              :message/visibility reveal-to})
        (cond-> (seq others) (log {:message/content outcome
                                   :message/visibility others})))))

(defn play-knight [state player-name _extra-args]
  (-> state
      (update :state/protected-players conj player-name)
      (log {:message/content (str player-name " is now protected")
            :message/visibility (set (:state/players state))})))

(defn draw-replacement-card
  "Draw from the deck, or take the card set aside at the start of the round if the deck is empty.
   An empty deck ends the round straight after this play, so the hidden card is not reused."
  [state player-name]
  (if (empty? (:state/deck state))
    (update-in state [:state/player-hands player-name] conj (:state/hidden-card state))
    (draw-card state player-name)))

(defn play-wizard [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 5)) target-player-name)
         (not (hand-contains-queen? state player-name))]}
  (let [target-card (player-card state target-player-name)]
    (if (= (:card/value target-card) 9)
      (-> state
          (eliminate-player target-player-name)
          (add-card-to-discard-pile target-card) 
          (log {:message/content (str target-player-name " discarded " (:card/name target-card) " and was eliminated")
                :message/visibility (set (:state/players state))}))
      (-> state
          (remove-single-card-from-hand target-player-name)
          (add-card-to-discard-pile target-card)
          (draw-replacement-card target-player-name)
          (log {:message/content (str target-player-name " discarded " (:card/name target-card) " and drew a new card")
                :message/visibility (set (:state/players state))})))))

(defn play-fool [state player-name {:keys [target-player-name]}]
  {:pre [(contains? (set (targetable-players state 6)) target-player-name)
         (not (hand-contains-queen? state player-name))]}
  (swap-cards-in-hands state player-name target-player-name))

(defn play-queen [state _player-name _extra-args] 
  state)

(defn play-king [state player-name _extra-args]
  (-> state
      (eliminate-player player-name)
      (log {:message/content (str player-name " was eliminated")
            :message/visibility (set (:state/players state))})))

(defn play-princeling [state _player-name _extra-args]
  state)

(defn clear-abbot-reveal [state]
  (assoc state :state/abbot-reveal nil))

(defn play-card
  {:malli/schema [:=> [:cat State PlayerName Card ExtraArgs] State]}
  [state player-name card extra-args]
  {:pre [(contains? (set (player-hand state player-name)) card)]}
  (let [all-players (set (:state/players state))
        state (remove-card-from-hand-upon-play state player-name card)
        state (add-card-to-discard-pile state card) 
        state (assoc state :state/last-played {:last-played/player-name player-name
                                               :last-played/card card})
        state (log state {:message/content (str player-name " played " (:card/value card) " - " (:card/name card))
                          :message/visibility all-players})
        state (update state :state/protected-players disj player-name)
        target-protected? (and (:card/targeting-rule card)
                               (protected-player? state (:target-player-name extra-args)))
        state (if target-protected?
                (log state {:message/content (str (:target-player-name extra-args) " was protected — nothing happened")
                            :message/visibility all-players})
                (case (:card/value card)
                  1 (play-minion state player-name extra-args)
                  2 (play-abbot state player-name extra-args)
                  3 (play-rogue state player-name extra-args)
                  4 (play-knight state player-name extra-args)
                  5 (play-wizard state player-name extra-args)
                  6 (play-fool state player-name extra-args)
                  7 (play-queen state player-name extra-args)
                  9 (play-king state player-name extra-args)
                  0 (play-princeling state player-name extra-args)))]
    (if (round-over? state)
      (let [winners (round-winners state)
            state (transition-to-next-round state winners)]
        (if (game-over? state)
          (assoc state :state/game-winners (game-winners state))
          state))
      (advance-player state))))






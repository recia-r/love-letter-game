(ns duck-dynasty.simulation-test
  "Plays many random games and checks the state after every move.
   From the REPL: (run-simulations 1000) for a summary of problems,
   (run-game seed) to replay one game in full."
  (:require
   [duck-dynasty.game :as dd]
   [hyperfiddle.rcf :as rcf]
   [malli.core :as m]
   [malli.error :as me]
   [malli.instrument :as mi]))

(rcf/enable!)

;; check args and results of game functions with a :malli/schema on every call
(mi/collect! {:ns ['duck-dynasty.game]})
(mi/instrument!)

(def player-pool ["Alice" "Bob" "Cara" "Dan"])
(def max-steps 500)

(def valid-state? (m/validator dd/State))
(def explain-state (m/explainer dd/State))

(def max-copies
  (into {} (map (juxt :card/value :card/count)) dd/cards))

;; seeded randomness, so any failing game can be replayed from its seed

(defn rand-nth-seeded [^java.util.Random rng coll]
  (nth coll (.nextInt rng (count coll))))

(defn shuffle-seeded [^java.util.Random rng coll]
  (let [cards (java.util.ArrayList. ^java.util.Collection coll)]
    (java.util.Collections/shuffle cards rng)
    (vec cards)))

(defn new-deck [rng]
  (shuffle-seeded rng (for [card dd/cards
                            _ (range (:card/count card))]
                        card)))

;; choosing a legal move

(defn playable-cards
  "A Queen must be played if held with a Wizard or Fool."
  [hand]
  (let [values (set (map :card/value hand))]
    (if (and (values 7) (or (values 5) (values 6)))
      (filterv #(= 7 (:card/value %)) hand)
      (vec hand))))

(defn choose-move [rng state player-name]
  (let [card (rand-nth-seeded rng (playable-cards (dd/player-hand state player-name)))
        targets (vec (dd/targetable-players state (:card/value card)))]
    {:card card
     :extra-args (cond-> {}
                   (seq targets)
                   (assoc :target-player-name (rand-nth-seeded rng targets))

                   (= 1 (:card/value card))
                   (assoc :guessed-card-value (rand-nth-seeded rng (vec (sort dd/minion-guessable-card-values)))))}))

;; checks run after every move

(defn state-problems [state]
  (let [hands (:state/player-hands state)
        all-cards (concat (:state/deck state)
                          (mapcat val hands)
                          (:state/discard-pile state)
                          [(:state/hidden-card state)])
        too-many (for [[value n] (frequencies (map :card/value all-cards))
                       :when (> n (get max-copies value 0))]
                   value)]
    (cond-> []
      (not (valid-state? state))
      (conj {:problem :schema
             :details (me/humanize (explain-state state))})

      (some nil? all-cards)
      (conj {:problem :nil-card})

      (seq too-many)
      (conj {:problem :too-many-copies :card-values (vec too-many)})

      (some (complement vector?) (vals hands))
      (conj {:problem :hand-not-vector})

      (some #(not= 1 (count %)) (vals hands))
      (conj {:problem :wrong-hand-size
             :hand-sizes (update-vals hands count)})

      (not (contains? hands (:state/current-player state)))
      (conj {:problem :current-player-not-active}))))

;; running games

(defn run-game
  "Play one random game. Returns the problems found, each tagged with the step and move."
  [seed]
  (let [rng (java.util.Random. seed)
        players (subvec player-pool 0 (+ 2 (.nextInt rng 3)))]
    ;; later rounds call dd/create-deck, so make those decks seeded too
    (with-redefs [dd/create-deck #(new-deck rng)]
      (let [state (dd/new-game players (new-deck rng))]
        (loop [state state
               step 0
               problems (state-problems state)]
          (cond
            (dd/game-over? state)
            {:seed seed :steps step :problems problems}

            (>= step max-steps)
            {:seed seed :steps step :problems (conj problems {:problem :did-not-finish})}

            :else
            (let [player-name (:state/current-player state)
                  drawn (dd/draw-card state player-name)
                  {:keys [card extra-args] :as move} (choose-move rng drawn player-name)
                  move (assoc move :player-name player-name)
                  result (try
                           {:state (dd/play-card drawn player-name card extra-args)}
                           (catch Throwable e
                             {:error e}))]
              (if-let [e (:error result)]
                {:seed seed
                 :steps step
                 :problems (conj problems {:problem :exception
                                           :message (ex-message e)
                                           :step step
                                           :move move
                                           :state drawn})}
                (recur (:state result)
                       (inc step)
                       (into problems
                             (map #(assoc % :step step :move move))
                             (state-problems (:state result))))))))))))

(defn describe-move [{:keys [player-name card extra-args]}]
  (cond-> {:player-name player-name :card (:card/name card)}
    (:target-player-name extra-args) (assoc :target (:target-player-name extra-args))
    (:guessed-card-value extra-args) (assoc :guess (:guessed-card-value extra-args))))

(defn problem-kind [{:keys [problem message details]}]
  (cond-> [problem]
    message (conj message)
    details (conj (set (keys details)))))

(defn summarize
  "Group problems from many games by kind, with a count and one small example of each."
  [results]
  (->> results
       (mapcat (fn [{:keys [seed problems]}]
                 (map #(assoc % :seed seed) problems)))
       (group-by problem-kind)
       (into {} (map (fn [[kind problems]]
                       [kind {:count (count problems)
                              :games (count (distinct (map :seed problems)))
                              :example (-> (first problems)
                                           (dissoc :state)
                                           (update :move #(some-> % describe-move)))}])))))

(defn run-simulations [n]
  (summarize (map run-game (range n))))

(rcf/tests
 "random games finish without errors or invalid states"
 (run-simulations 300)
 := {})

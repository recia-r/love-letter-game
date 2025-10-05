(ns clj.game
  (:require
   [org.httpkit.server :as http]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.util.response :refer [file-response]]
   [clojure.string :refer [split]]))

(defonce game-state (atom {:state/players []
                           :state/player-hands {}
                           :state/current-player 0
                           :state/deck []
                           :state/discard-pile []
                           :state/round-winner nil
                           :state/game-over? false
                           :state/must-play? false}))

;; TODO - malli spec for state, could reuse for frontend too
;; TODO - bug, if have two of same card, it removes both

(def cards
  [{:card/id 1 :card/name "Minion" :card/value 1 :card/count 5 :card/ability "Guess a non-Minion card in another player's hand. If correct, they are eliminated."}
   {:card/id 2 :card/name "Abbot" :card/value 2 :card/count 2 :card/ability "Look at another player's hand."}
   {:card/id 3 :card/name "Rogue" :card/value 3 :card/count 2 :card/ability "Compare hands with another player. Lower value is eliminated."}
   {:card/id 4 :card/name "Knight" :card/value 4 :card/count 2 :card/ability "Protection from effects until your next turn."}
   {:card/id 5 :card/name "Wizard" :card/value 5 :card/count 2 :card/ability "Force any player (including yourself) to discard their hand and draw a new card."}
   {:card/id 6 :card/name "Fool" :card/value 6 :card/count 1 :card/ability "Trade hands with another player."}
   {:card/id 7 :card/name "Queen" :card/value 7 :card/count 1 :card/ability "Must be discarded if you have Fool or Wizard."}
   {:card/id 9 :card/name "King" :card/value 9 :card/count 1 :card/ability "If discarded, you are eliminated."}
   {:card/id 0 :card/name "Princeling" :card/value 0 :card/count 1 :card/ability "At the end of the round, this card's value is 8"}])

;; Game logic functions
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
    {:state/deck final-deck
     :state/hidden-card hidden-card
     :state/player-hands (zipmap players (map vector initial-cards))}))

(defn new-game [player-names]
  (let [players (vec player-names)
        initial-setup (deal-initial-cards players)]
    {:state/players players
     :state/current-player 0
     :state/deck (:state/deck initial-setup)
     :state/hidden-card (:state/hidden-card initial-setup)
     :state/player-hands (:state/player-hands initial-setup)
     :state/discard-pile []
     :state/round-winner nil
     :state/game-over? false
     :state/must-play? false}))

(defn start-new-game [player-names]
  (reset! game-state (new-game player-names)))

(defn draw-card [player-id]
  (let [current-state @game-state
        deck (:state/deck current-state)
        current-hand (get (:state/player-hands current-state) player-id)]
    (if (empty? deck)
      nil
      (let [drawn-card (first deck)
            remaining-deck (rest deck)
            new-hand (if (vector? current-hand)
                       (conj current-hand drawn-card)
                       [drawn-card])]
        (swap! game-state assoc
               :state/deck remaining-deck
               :state/player-hands (assoc (:state/player-hands current-state) player-id new-hand)
               :state/must-play? true)
        drawn-card))))

;; TODO for minion, this will be insufficient
;; TODO it would be nice if each card was it's own fn
;; TODO it would be nice if this was a pure fn (takes in state and returns state)

(defn eliminated-player? [state player-id]
  (empty? (get-in state [:state/player-hands player-id])))

(defn eliminate-player [state player-id]
  (assoc-in state [:state/player-hands player-id] []))

(defn play-card [player-id card-to-play target-player-id]
  (let [current-state @game-state
        player-hand (get (:state/player-hands current-state) player-id) ;; TODO use get-in
        new-discard-pile (conj (:state/discard-pile current-state) card-to-play)
        ;; Remove only the specific card instance, not all cards with the same ID
        ;; TODO doesn't work, clojure's notion of identity is different
        remaining-cards (remove #(identical? % card-to-play) player-hand)]

    ;; Apply card effects
    ;; discard the card
    (swap! game-state assoc-in [:state/player-hands player-id] remaining-cards)
    (swap! game-state assoc-in [:state/discard-pile] new-discard-pile)

    ;; apply the card effect
    (case (:card/id card-to-play)
      1 ;; Minion
      (when target-player-id
        (let [target-hand (get (:state/player-hands current-state) target-player-id)]
          (when (and target-hand (not= (:card/id (first target-hand)) 1))
            (swap! game-state eliminate-player target-player-id))))
      2 ;; Abbot
      (when target-player-id
        nil)

      3 ;; Rogue
      (when target-player-id
        (let [target-hand (get (:state/player-hands current-state) target-player-id)]
          (when (and target-hand player-hand)
            (let [player-card (first (remove #(identical? % card-to-play) player-hand))]
              (if (< (:card/value player-card) (:card/value (first target-hand)))
                (swap! game-state eliminate-player player-id)
                (swap! game-state eliminate-player target-player-id))))))

      4 ;; Knight
      nil

      5 ;; Wizard
      (when target-player-id
        (let [target-hand (get (:state/player-hands current-state) target-player-id)]
          (when target-hand
            (if (= (:card/id (first target-hand)) 9) ;; King
              (swap! game-state update :state/eliminated-players conj target-player-id)
              (let [current-deck (:state/deck current-state)
                    drawn-card (first current-deck)
                    remaining-deck (rest current-deck)]
                (when drawn-card
                  (swap! game-state assoc
                         :state/deck remaining-deck
                         :state/player-hands (assoc (:state/player-hands current-state) target-player-id [drawn-card]))))))))

      6 ;; Fool
      (when target-player-id
        (let [target-hand (get (:state/player-hands current-state) target-player-id)]
          (swap! game-state assoc-in [:state/player-hands player-id] target-hand)
          (swap! game-state assoc-in [:state/player-hands target-player-id] player-hand)))

      7 ;; Queen
            ;; Must be discarded if Fool or Wizard in hand
      nil

      9 ;; King
      (swap! game-state update :state/eliminated-players conj player-id)

      0 ;; Princeling
      ;; No immediate effect - value becomes 8 at end of round
      nil

      nil)

    ;; Update game state - player now has remaining cards after playing
    (swap! game-state assoc
           :state/discard-pile new-discard-pile
           :state/player-hands (assoc (:state/player-hands current-state) player-id remaining-cards)
           :state/current-player (mod (inc (:state/current-player current-state)) (count (:state/players current-state)))
           :state/must-play? false)))

(defn check-round-end []
  (let [current-state @game-state
        active-players (remove #(contains? (:state/eliminated-players current-state) %) (:state/players current-state))]
    (cond
      ;; one player left
      (= (count active-players) 1)
      (do
        (swap! game-state assoc :state/round-winner (first active-players))
        true)

      ;; No cards left in deck - highest value wins
      (empty? (:state/deck current-state))
      (let [remaining-hands (select-keys (:state/player-hands current-state) active-players)
            highest-player (apply max-key #(:card/value (val %)) remaining-hands)]
        (swap! game-state assoc :state/round-winner (key highest-player))
        true)

      :else false)))

(defn start-new-round []
  (let [current-state @game-state
        players (:state/players current-state)
        initial-setup (deal-initial-cards players)]
    ;; Start new round
    (swap! game-state assoc
           :state/deck (:state/deck initial-setup)
           :state/hidden-card (:state/hidden-card initial-setup)
           :state/player-hands (:state/player-hands initial-setup)
           :state/discard-pile []
           :state/eliminated-players #{}
           :state/round-winner nil
           :state/current-player 0
           :state/must-play? false)))

;; TODO all of above would ideally be in a seperate namespace
;; TODO should write some tests for just the game logic (ie. no http)

;; API functions
(defn get-game-state []
  (let [state @game-state
        players (:state/players state)]
    {:players players
     :current-player (when (and (seq players) (< (:state/current-player state) (count players)))
                       (nth players (:state/current-player state)))
     :player-hands (:state/player-hands state)
     :discard-pile (:state/discard-pile state)
     :eliminated-players (vec (:state/eliminated-players state))
     :round-winner (:state/round-winner state)

     :game-over (:state/game-over? state)
     :deck-count (count (:state/deck state))
     :must-play (:state/must-play? state)}))

;; TODO - use malli to spec out the endpoints, to avoid this mess of nested ifs
;; TODO - should check if the move that is submitted via http is legal

(defn handle-start-game [request]
  (let [params (:params request)
        players (get params "players")]
    (if players
      (let [player-list (map clojure.string/trim (split players #","))]
        (start-new-game player-list)
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (pr-str {:success true})})
      {:status 400
       :headers {"Content-Type" "application/json"}
       :body (pr-str {:success false :error "Missing players parameter"})})))

(defn handle-play-card [request]
  (let [params (:params request)
        player-id (get params "player")
        card-id-str (get params "card")
        target-player-id (get params "target")]
    (if (and player-id card-id-str)
      (let [card-id (Integer/parseInt card-id-str)
            current-state @game-state
            player-hand (get (:state/player-hands current-state) player-id)
            card-to-play (first (filter #(= (:card/id %) card-id) player-hand))]
        (if card-to-play
          (do
            (play-card player-id card-to-play target-player-id)
            (when (check-round-end)
              (start-new-round))
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (pr-str {:success true})})
          {:status 400
           :headers {"Content-Type" "application/json"}
           :body (pr-str {:success false :error "Card not found in player's hand"})}))
      {:status 400
       :headers {"Content-Type" "application/json"}
       :body (pr-str {:success false :error "Missing player or card parameter"})})))

(defn handle-draw-card [request]
  (let [params (:params request)
        player-id (get params "player")]
    (if player-id
      (let [drawn-card (draw-card player-id)]
        (when drawn-card
          (swap! game-state update :current-player #(mod (inc %) (count (:players @game-state))))
          (when (check-round-end)
            (start-new-round)))
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (pr-str {:success true :card drawn-card})})
      {:status 400
       :headers {"Content-Type" "application/json"}
       :body (pr-str {:success false :error "Missing player parameter"})})))

;; Simple routing
(defn handler [request]
  (cond
    (and (= (:uri request) "/") (= (:request-method request) :get))
    (file-response "public/index.html")

    (and (= (:uri request) "/app/main.js") (= (:request-method request) :get))
    (file-response "target/public/app/main.js")

    (and (= (:uri request) "/api/game-state") (= (:request-method request) :get))
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str (get-game-state))}

    (and (= (:uri request) "/api/start-game") (= (:request-method request) :post))
    (handle-start-game request)

    (and (= (:uri request) "/api/play-card") (= (:request-method request) :post))
    (handle-play-card request)

    (and (= (:uri request) "/api/draw-card") (= (:request-method request) :post))
    (handle-draw-card request)

    ;; Handle CORS preflight requests
    (= (:request-method request) :options)
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body ""}

    :else
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Page not found"}))

(def wrapped-app (-> handler
                     wrap-multipart-params
                     wrap-params))


#_(server)
#_(def server (http/run-server #'wrapped-app {:port 8000}))
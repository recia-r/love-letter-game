(ns clj.game
  (:require
   [org.httpkit.server :as http]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.util.response :refer [file-response]]
   [clojure.string :refer [split]]))

;; Love Letter game state
(defonce game-state (atom {:players []
                           :current-player 0
                           :deck []
                           :discard-pile []
                           :eliminated-players #{}
                           :round-winner nil
                           :game-over false}))

;; Card definitions based on Love Letter rules
(def cards
  [{:id 1 :name "Minion" :value 1 :count 5 :ability "Guess a non-Minion card in another player's hand. If correct, they are eliminated."}
   {:id 2 :name "Abbot" :value 2 :count 2 :ability "Look at another player's hand."}
   {:id 3 :name "Rogue" :value 3 :count 2 :ability "Compare hands with another player. Lower value is eliminated."}
   {:id 4 :name "Knight" :value 4 :count 2 :ability "Protection from effects until your next turn."}
   {:id 5 :name "Wizard" :value 5 :count 2 :ability "Force any player (including yourself) to discard their hand and draw a new card."}
   {:id 6 :name "Fool" :value 6 :count 1 :ability "Trade hands with another player."}
   {:id 7 :name "Queen" :value 7 :count 1 :ability "Must be discarded if you have Fool or Wizard."}
   {:id 9 :name "King" :value 9 :count 1 :ability "If discarded, you are eliminated."}
   {:id 0 :name "Princeling" :value 0 :count 1 :ability "At the end of the round, this card's value is 8"}])

;; Game logic functions
(defn create-deck []
  (let [card-list (for [card cards
                        _ (range (:count card))]
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

(defn start-new-game [player-names]
  (let [players (vec player-names)
        initial-setup (deal-initial-cards players)]
    (reset! game-state
            {:players players
             :current-player 0
             :deck (:deck initial-setup)
             :hidden-card (:hidden-card initial-setup)
             :player-hands (:player-hands initial-setup)
             :discard-pile []
             :eliminated-players #{}
             :round-winner nil

             :game-over false})))

(defn draw-card [player-id]
  (let [current-state @game-state
        deck (:deck current-state)
        current-hand (get (:player-hands current-state) player-id)]
    (if (empty? deck)
      nil
      (let [drawn-card (first deck)
            remaining-deck (rest deck)
            new-hand (if (vector? current-hand)
                       (conj current-hand drawn-card)
                       [drawn-card])]
        (swap! game-state assoc
               :deck remaining-deck
               :player-hands (assoc (:player-hands current-state) player-id new-hand))
        drawn-card))))

(defn play-card [player-id card-to-play target-player-id]
  (let [current-state @game-state
        player-hand (get (:player-hands current-state) player-id)
        new-discard-pile (conj (:discard-pile current-state) card-to-play)
        remaining-cards (remove #(= (:id %) (:id card-to-play)) player-hand)]

    ;; Apply card effects
    ;; discard the card
    (swap! game-state assoc-in [:player-hands player-id] remaining-cards)
    (swap! game-state assoc-in [:discard-pile] new-discard-pile)

    ;; apply the card effect
    (case (:id card-to-play)
      1 ;; Minion
      (when target-player-id
        (let [target-hand (get (:player-hands current-state) target-player-id)]
          (when (and target-hand (not= (:id (first target-hand)) 1))
            (swap! game-state eliminate-player target-player-id))))
      2 ;; Abbot
      (when target-player-id
        nil)

      3 ;; Rogue
      (when target-player-id
        (let [target-hand (get (:player-hands current-state) target-player-id)]
          (when (and target-hand player-hand)
            (let [player-card (first (remove #(= (:id %) (:id card-to-play)) player-hand))]
              (if (< (:value player-card) (:value (first target-hand)))
                (swap! game-state update :eliminated-players conj player-id)
                (swap! game-state update :eliminated-players conj target-player-id))))))

      4 ;; Knight
      nil

      5 ;; Wizard
      (when target-player-id
        (let [target-hand (get (:player-hands current-state) target-player-id)]
          (when target-hand
            (if (= (:id (first target-hand)) 9) ;; King
              (swap! game-state update :eliminated-players conj target-player-id)
              (let [current-deck (:deck current-state)
                    drawn-card (first current-deck)
                    remaining-deck (rest current-deck)]
                (when drawn-card
                  (swap! game-state assoc
                         :deck remaining-deck
                         :player-hands (assoc (:player-hands current-state) target-player-id [drawn-card]))))))))

      6 ;; Fool
      (when target-player-id
        (let [target-hand (get (:player-hands current-state) target-player-id)]
          (swap! game-state assoc-in [:player-hands player-id] target-hand)
          (swap! game-state assoc-in [:player-hands target-player-id] player-hand)))

      7 ;; Queen
            ;; Must be discarded if Fool or Wizard in hand
      nil

      9 ;; King
      (swap! game-state update :eliminated-players conj player-id)

      0 ;; Princeling
      ;; No immediate effect - value becomes 8 at end of round
      nil

      nil)

    ;; Update game state - player now has remaining cards after playing
    (swap! game-state assoc
           :discard-pile new-discard-pile
           :player-hands (assoc (:player-hands current-state) player-id remaining-cards)
           :current-player (mod (inc (:current-player current-state)) (count (:players current-state))))))

(defn check-round-end []
  (let [current-state @game-state
        active-players (remove #(contains? (:eliminated-players current-state) %) (:players current-state))]
    (cond
      ;; Only one player left - they win
      (= (count active-players) 1)
      (do
        (swap! game-state assoc :round-winner (first active-players))
        true)

      ;; No cards left in deck - highest value wins
      (empty? (:deck current-state))
      (let [remaining-hands (select-keys (:player-hands current-state) active-players)
            highest-player (apply max-key #(:value (val %)) remaining-hands)]
        (swap! game-state assoc :round-winner (key highest-player))
        true)

      :else false)))

(defn start-new-round []
  (let [current-state @game-state
        players (:players current-state)]
    ;; Start new round
    (let [initial-setup (deal-initial-cards players)]
      (swap! game-state assoc
             :deck (:deck initial-setup)
             :hidden-card (:hidden-card initial-setup)
             :player-hands (:player-hands initial-setup)
             :discard-pile []
             :eliminated-players #{}
             :round-winner nil
             :current-player 0))))

;; API functions
(defn get-game-state []
  (let [state @game-state
        players (:players state)]
    {:players players
     :current-player (when (and (seq players) (< (:current-player state) (count players)))
                       (nth players (:current-player state)))
     :player-hands (:player-hands state)
     :discard-pile (:discard-pile state)
     :eliminated-players (vec (:eliminated-players state))
     :round-winner (:round-winner state)

     :game-over (:game-over state)
     :deck-count (count (:deck state))}))

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
            player-hand (get (:player-hands current-state) player-id)
            card-to-play (first (filter #(= (:id %) card-id) player-hand))]
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
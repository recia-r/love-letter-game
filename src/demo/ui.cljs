(ns demo.ui
  (:require
   [reagent.core :as r]
   [cljs.reader :as edn]))

;; Centralized styles
(def styles
  {:card {:border "1px solid #ccc"
          :padding "10px"
          :margin "5px"
          :border-radius "5px"
          :text-align "center"
          :min-width "80px"}

   :card-name {:font-weight "bold"
               :margin-bottom "5px"}

   :card-value {:font-size "14px"
                :color "#666"
                :margin-bottom "5px"}

   :card-ability {:font-size "12px"
                  :margin-top "5px"}

   :player-hands-container {:display "flex"
                            :flex-direction "row"
                            :justify-content "space-around"
                            :align-items "flex-start"
                            :flex-wrap "wrap"
                            :gap "20px"
                            :margin "20px 0"}

   :player-hand-container {:flex "1"
                           :min-width "200px"
                           :max-width "300px"
                           :border "2px solid #ddd"
                           :border-radius "8px"
                           :padding "15px"
                           :margin "10px"
                           :background-color "#f9f9f9"}

   :cards-container {:display "flex"
                     :flex-direction "row"
                     :flex-wrap "wrap"
                     :gap "10px"
                     :justify-content "center"}

   :start-game-buttons {:margin-top "20px"}})

;; Frontend state for Love Letter game
(defonce app-state (r/atom {:players []
                            :current-player nil
                            :player-hands {}
                            :discard-pile []
                            :eliminated-players []
                            :round-winner nil
                            :game-over false
                            :deck-count 0
                            :selected-card nil
                            :target-player nil
                            :game-started false}))

;; API calls to backend using JavaScript fetch
(defn fetch-game-state! []
  (-> (js/fetch "http://localhost:8000/api/game-state")
      (.then #(.text %))
      (.then #(let [new-state (edn/read-string %)]
                (reset! app-state new-state)))
      (.catch #(js/console.error "Error fetching game state:" %))))

(defn start-game! [player-names]
  (let [form-data (js/FormData.)]
    (.append form-data "players" player-names)
    (-> (js/fetch "http://localhost:8000/api/start-game"
                  #js {:method "POST"
                       :body form-data})
        (.then #(fetch-game-state!))
        (.catch #(js/console.error "Error starting game:" %)))))

(defn play-card! [card-id player-id target-player-id]
  (let [form-data (js/FormData.)]
    (.append form-data "card" (str card-id))
    (.append form-data "player" player-id)
    (when target-player-id
      (.append form-data "target" target-player-id))
    (-> (js/fetch "http://localhost:8000/api/play-card"
                  #js {:method "POST"
                       :body form-data})
        (.then #(do
                  (fetch-game-state!)
                  (swap! app-state assoc :selected-card nil :target-player nil)))
        (.catch #(js/console.error "Error playing card:" %)))))

(defn draw-card! [player-id]
  (let [form-data (js/FormData.)]
    (.append form-data "player" player-id)
    (-> (js/fetch "http://localhost:8000/api/draw-card"
                  #js {:method "POST"
                       :body form-data})
        (.then #(fetch-game-state!))
        (.catch #(js/console.error "Error drawing card:" %)))))

;; UI Components
(defn card-display [card]
  [:div.card
   {:style (:card styles)}
   [:div.card-name {:style (:card-name styles)} (:name card)]
   [:div.card-value {:style (:card-value styles)} (str "Value: " (:value card))]
   [:div.card-ability {:style (:card-ability styles)} (:ability card)]])

(defn player-hand [player-id cards]
  [:div.player-hand
   [:h3 (str player-id)]
   [:div.cards
    {:style (:cards-container styles)}
    (for [card cards]
      ^{:key (:id card)}
      [card-display card])]])

(defn game-controls [current-player player-hands]
  [:div.game-controls
   [:h3 (str "Current Player: " current-player)]
   [:div
    [:button
     {:on-click #(draw-card! current-player)}
     "Draw Card"]
    [:br]
    [:br]
    (let [current-hand (get player-hands current-player)]
      [:div
       [:p (str "Cards in hand: " (count current-hand))]
       (for [card current-hand]
         ^{:key (str "card-" (:id card) "-" (hash card))}
         [:button
          {:on-click #(swap! app-state assoc :selected-card card)}
          (str "Play " (:name card))])])]

   (when (:selected-card @app-state)
     [:div
      [:p (str "Selected: " (:name (:selected-card @app-state)))]
      (when (and (not= (:id (:selected-card @app-state)) 4) ; Handmaid doesn't need target
                 (not= (:id (:selected-card @app-state)) 7)) ; Countess doesn't need target
        [:div
         [:p "Select target player:"]
         (for [player (keys player-hands)]
           ^{:key (str "target-" player)}
           [:button
            {:on-click #(swap! app-state assoc :target-player player)}
            (str "Target " player)])])
      [:button
       {:on-click #(play-card! (:id (:selected-card @app-state)) current-player (:target-player @app-state))}
       "Confirm Play"]])])

(defn game-status [round-winner game-over eliminated-players]
  [:div.game-status
   [:h3 "Game Status"]

   (when round-winner
     [:div.round-winner
      [:h4 (str "Round Winner: " round-winner)]])

   (when game-over
     [:div.game-over
      [:h4 (str "Game Over! Winner: " round-winner)]])

   (when (not-empty eliminated-players)
     [:div.eliminated
      [:h4 "Eliminated Players:"]
      (for [player eliminated-players]
        ^{:key player}
        [:p player])])])

(defn start-game-form []
  [:div.start-game
   [:h3 "Start New Game"]
   [:input {:type "text"
            :placeholder "Player names (comma-separated)"
            :default-value "Alice,Bob,Charlie"
            :id "player-names"}]
   [:button {:on-click #(let [names (.. (js/document.getElementById "player-names") -value)]
                          (start-game! names))}
    "Start Game"]])

(defn app []
  (let [current-state @app-state
        has-players (not (empty? (:players current-state)))]
    [:div.app
     [:h1 "Love Letter Game"]

     [:button {:on-click #(reset! app-state {:players []
                                             :current-player nil
                                             :player-hands {}
                                             :discard-pile []
                                             :eliminated-players []
                                             :round-winner nil
                                             :game-over false
                                             :deck-count 0
                                             :selected-card nil
                                             :target-player nil
                                             :game-started false})}
      "New Game"]

     (if has-players
       [:div.game
        [game-status (:round-winner current-state) (:game-over current-state) (:eliminated-players current-state)]

        [:div.game-board

         [:div.player-hands
          {:style (:player-hands-container styles)}
          (for [[player-id cards] (:player-hands current-state)]
            ^{:key player-id}
            [:div.player-hand
             {:style (:player-hand-container styles)}
             [player-hand player-id cards]])]

         [game-controls (:current-player current-state) (:player-hands current-state)]

         [:div.deck-info
          [:p (str "Cards remaining in deck: " (:deck-count current-state))]]]

        [:div.discard-pile
         [:h3 "Discard Pile"]
         (for [card (:discard-pile current-state)]
           ^{:key (str (:id card) "-" (hash card))}
           [card-display card])]]

       [start-game-form])]))

;; Initialize - this will be called from core.cljs
(defn init []
  (fetch-game-state!))
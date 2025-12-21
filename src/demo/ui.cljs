(ns demo.ui
  (:require
   [reagent.core :as r]
   [duck-dynasty.game :as dd]
   [clojure.string :as str]
   [cljs.reader :as reader]))

;; Game state atom
(defonce game-state (r/atom nil))

(defn fetch-game-state []
  (-> (js/fetch "/api/game-state")
      (.then (fn [response] (.text response)))
      (.then (fn [edn-text]
               (reset! game-state (reader/read-string edn-text))))
      (.catch (fn [error]
                (js/console.error "Error fetching game state:" error)))))

(defn start-game [player-names]
  (let [players-str (str/join "," player-names)
        form-data (doto (js/FormData.)
                    (.append "players" players-str))
        options (clj->js {:method "POST"
                          :body form-data})]
    (-> (js/fetch "/api/start-game" options)
        (.then (fn [response] (.text response)))
        (.then (fn [edn-text]
                 (reset! game-state (reader/read-string edn-text))))
        (.catch (fn [error]
                  (js/console.error "Error starting game:" error))))))

(defn play-card-api [player-name card-value target-player-name guessed-value]
  (let [form-data (doto (js/FormData.)
                    (.append "player" player-name)
                    (.append "card" (str card-value)))]
    (when target-player-name
      (.append form-data "target" target-player-name))
    (when guessed-value
      (.append form-data "guessed-value" (str guessed-value)))
    (let [options (clj->js {:method "POST"
                            :body form-data})]
      (-> (js/fetch "/api/play-card" options)
          (.then (fn [response] (.text response)))
          (.then (fn [edn-text]
                   (reset! game-state (reader/read-string edn-text))))
          (.catch (fn [error]
                    (js/console.error "Error playing card:" error)))))))

(defn init []
  ;; Initialize a new game with two players
  (fetch-game-state))

(defn card-component [card]
  [:div.card
   {:style {:border "1px solid #ccc"
            :padding "10px"
            :margin "5px"
            :border-radius "4px"
            :background-color "#f9f9f9"}}
   [:div.card-name {:style {:font-weight "bold"}} (:card/name card)]
   [:div.card-value {:style {:font-size "0.9em" :color "#666"}}
    "Value: " (:card/value card)]
   [:div.card-ability {:style {:font-size "0.8em" :color "#888" :margin-top "5px"}}
    (:card/ability card)]])

(defn player-hand-component [state player-name]
  (let [hand (dd/player-hand state player-name)]
    [:div.player-hand
     {:style {:margin "10px 0"}}
     [:h3 (str player-name "'s Hand")]
     (if (empty? hand)
       [:div "No cards (eliminated)"]
       [:div.cards
        {:style {:display "flex" :flex-wrap "wrap"}}
        (for [card hand]
          ^{:key (str (:card/value card) "-" (:card/name card))}
          [card-component card])])]))

(defn play-card-form [state player-name card]
  (let [target-players (dd/targetable-players state (:card/value card))
        needs-target? (seq target-players)
        needs-guess? (= (:card/value card) 1) ; Minion needs a guess
        target (r/atom (first target-players))
        guessed-value (r/atom 2)]
    (fn []
      [:div.play-card-form
       {:style {:border "2px solid #4CAF50"
                :padding "15px"
                :margin "10px 0"
                :border-radius "4px"
                :background-color "#f0f8f0"}}
       [:div {:style {:font-weight "bold" :margin-bottom "10px"}}
        (str "Play " (:card/name card))]
       (when needs-target?
         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px"}} "Target Player:"]
          [:select {:value @target
                    :on-change #(reset! target (-> % .-target .-value))}
           (for [tp target-players]
             ^{:key tp}
             [:option {:value tp} tp])]])
       (when needs-guess?
         [:div {:style {:margin-bottom "10px"}}
          [:label {:style {:display "block" :margin-bottom "5px"}} "Guess Card Value:"]
          [:select {:value @guessed-value
                    :on-change #(reset! guessed-value (js/parseInt (-> % .-target .-value)))}
           (for [val [2 3 4 5 6 7 9 0]]
             ^{:key val}
             [:option {:value val} (str val " - " (:card/name (dd/card-by-value val)))])]])
       [:button {:on-click (fn []
                             (play-card-api player-name
                                            (:card/value card)
                                            (when needs-target? @target)
                                            (when needs-guess? @guessed-value)))
                 :style {:padding "8px 16px"
                         :background-color "#4CAF50"
                         :color "white"
                         :border "none"
                         :border-radius "4px"
                         :cursor "pointer"}}
        "Play Card"]])))

(defn current-player-component [state]
  (let [current-player (:state/current-player state)
        hand (dd/player-hand state current-player)]
    [:div.current-player
     {:style {:background-color "#e3f2fd"
              :padding "20px"
              :border-radius "8px"
              :margin "20px 0"}}
     [:h2 {:style {:margin-top "0"}}
      (str "Current Player: " current-player)]
     [player-hand-component state current-player]
     (when (seq hand)
       [:div.playable-cards
        {:style {:margin-top "20px"}}
        (for [card hand]
          ^{:key (str "play-" (:card/value card))}
          [:div {:style {:margin-bottom "15px"}}
           [play-card-form state current-player card]])])]))

(defn game-status-component [state]
  [:div.game-status
   {:style {:background-color "#fff3cd"
            :padding "15px"
            :border-radius "8px"
            :margin "20px 0"}}
   (if (dd/game-over? state)
     [:div
      [:h2 {:style {:color "#856404"}} "Game Over!"]
      [:p {:style {:font-size "1.2em" :font-weight "bold"}}
       (str "Winner: " (dd/game-winner state))]]
     [:div
      [:p (str "Active Players: " (str/join ", " (dd/active-players state)))]
      [:p (str "Round: " (:state/round state) " / " (:state/rounds state))]])])

(defn app []
  [:<>
   [:div.actions
    {:style {:margin-top "30px" :text-align "center"}}
    [:button {:on-click #(start-game ["Alice" "Bob"])
              :style {:padding "10px 20px"
                      :background-color "#2196F3"
                      :color "white"
                      :border "none"
                      :border-radius "4px"
                      :cursor "pointer"
                      :font-size "16px"}}
     "New Game"]]
   (if-let [state @game-state]
     [:div.app
      {:style {:max-width "800px"
               :margin "0 auto"
               :padding "20px"
               :font-family "Arial, sans-serif"}}
      [:h1 {:style {:text-align "center" :color "#333"}} "Love Letter Game"]
      [game-status-component state]
      [current-player-component state]
      [:div.all-players
       {:style {:margin-top "30px"}}
       [:h2 "All Players"]
       (for [player (dd/active-players state)]
         ^{:key player}
         [:div {:style {:margin-bottom "20px"}}
          [player-hand-component state player]])]]
     [:div "Loading game state..."])])

